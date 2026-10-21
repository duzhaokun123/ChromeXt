package org.matrix.chromext.script

import android.content.Context
import android.database.AbstractWindowedCursor
import android.database.CursorWindow
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Build
import java.util.UUID
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.db.ChromeXtDatabase
import org.matrix.chromext.db.entity.Script
import org.matrix.chromext.db.entity.ScriptStorage
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ScriptParser

private const val OLD_DB_NAME = "userscript"
private const val OLD_DB_VERSION = 9
private const val OLD_DB_TABLE = "script"
private const val OLD_DB_COLUMNS = "id,meta,code,storage"
private const val SQL_CREATE_ENTRIES =
  "CREATE TABLE script (id TEXT PRIMARY KEY NOT NULL, meta TEXT NOT NULL, code TEXT NOT NULL, storage TEXT);"

/**
 * Database used by old ChromeXt (JingMatrix/ChromeXt), kept here to let SQLiteOpenHelper upgrade it
 * to [OLD_DB_VERSION] before reading.
 */
private class OldScriptDbHelper(context: Context) :
  SQLiteOpenHelper(context, OLD_DB_NAME, null, OLD_DB_VERSION) {

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(SQL_CREATE_ENTRIES)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 7) {
      db.execSQL("DROP TABLE script;")
      onCreate(db)
      return
    }

    if (oldVersion == 7 && newVersion == 8) {
      db.execSQL("CREATE TABLE tmp AS SELECT id,meta,code,storage FROM script;")
      db.execSQL("DROP TABLE script;")
      db.execSQL("ALTER TABLE tmp RENAME TO script;")
    }

    if (oldVersion == 8 && newVersion == 9) {
      db.execSQL("CREATE TABLE tmp AS SELECT id,meta,code,storage FROM script;")
      db.execSQL("DROP TABLE script;")
      db.execSQL(SQL_CREATE_ENTRIES)
      db.execSQL(
        "INSERT INTO script (id,meta,code) SELECT id,meta,code FROM tmp WHERE storage = '';")
      db.execSQL("INSERT INTO script SELECT * FROM tmp WHERE storage != '';")
      db.execSQL("DROP TABLE tmp;")
    }

    if (newVersion - oldVersion > 1) {
      onUpgrade(db, oldVersion, oldVersion + 1)
      onUpgrade(db, oldVersion + 1, newVersion)
    }
  }

  override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

object UserscriptMigration {
  data class Result(val imported: Int, val skipped: Int, val failed: Int)

  private data class OldRow(
    val id: String,
    val meta: String,
    val code: String,
    val storage: String?,
  )

  fun hasOldDatabase(): Boolean {
    return oldDatabaseFile().exists()
  }

  private fun oldDatabaseFile() = Chrome.getContext().getDatabasePath(OLD_DB_NAME)

  suspend fun migrate(db: ChromeXtDatabase): Result {
    val context = Chrome.getContext()
    val dbFile = context.getDatabasePath(OLD_DB_NAME)
    check(dbFile.exists()) { "old database ${dbFile.path} not found" }

    val existingIds = db.scriptDao().getAll().mapTo(mutableSetOf()) { it.id }
    var imported = 0
    var skipped = 0
    var failed = 0

    val helper = OldScriptDbHelper(context)
    try {
      val oldDb = helper.writableDatabase
      Log.i("userscript database opened, version ${oldDb.version}")
      check(oldDb.hasScriptColumns()) { "userscript database has unexpected schema" }

      oldDb
        .query(OLD_DB_TABLE, OLD_DB_COLUMNS.split(",").toTypedArray(), null, null, null, null, null)
        .use { cursor ->
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (cursor as AbstractWindowedCursor)
              .setWindow(CursorWindow("userscript", 32L * 1024 * 1024))
          }
          while (cursor.moveToNext()) {
            val row =
              try {
                OldRow(
                  id = cursor.getString(0) ?: "",
                  meta = cursor.getString(1) ?: "",
                  code = cursor.getString(2) ?: "",
                  storage = if (cursor.isNull(3)) null else cursor.getString(3),
                )
              } catch (e: Exception) {
                Log.w("read row ${cursor.position} from userscript failed: ${e.message}")
                failed += 1
                continue
              }

            if (row.meta.isBlank()) {
              Log.w("skip old script ${row.id}, meta is empty")
              failed += 1
              continue
            }

            val metadata = ScriptParser.parseMetadata(row.meta)
            val id =
              if (metadata.name.isNullOrBlank() || metadata.namespace.isNullOrBlank()) row.id
              else metadata.id
            if (id.isBlank()) {
              Log.w("skip old script with empty id")
              failed += 1
              continue
            }
            if (id in existingIds) {
              skipped += 1
              continue
            }

            val meta =
              if (row.meta.last().isWhitespace()) row.meta else row.meta + "\n"
            val scriptFile = meta + row.code
            if (ScriptParser.splitScript(scriptFile) == null) {
              Log.w("skip old script $id, split script failed")
              failed += 1
              continue
            }
            if (!ScriptFileStorage.putScript(id, scriptFile)) {
              Log.e("skip old script $id, write script file failed")
              failed += 1
              continue
            }

            db.scriptDao().upsert(Script(id, UUID.randomUUID(), meta))
            parseStorage(row.storage)?.let { storage ->
              db.scriptStorageDao().upsert(ScriptStorage(id, storage))
            }
            existingIds.add(id)
            imported += 1
          }
        }
    } finally {
      helper.close()
    }

    if (failed == 0) {
      val deleted = context.deleteDatabase(OLD_DB_NAME)
      Log.i(
        "migrate userscript: imported $imported, skipped $skipped, deleted old database $deleted")
    } else {
      Log.w(
        "migrate userscript: imported $imported, skipped $skipped, failed $failed, keep old database")
    }
    return Result(imported, skipped, failed)
  }

  private fun SQLiteDatabase.hasScriptColumns(): Boolean {
    val columns = mutableSetOf<String>()
    rawQuery("PRAGMA table_info($OLD_DB_TABLE)", null).use { cursor ->
      val nameIndex = cursor.getColumnIndex("name")
      if (nameIndex == -1) return false
      while (cursor.moveToNext()) {
        cursor.getString(nameIndex)?.let { columns.add(it) }
      }
    }
    return columns.containsAll(OLD_DB_COLUMNS.split(","))
  }

  private fun parseStorage(raw: String?): JSONObject? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty() || text == "null") return null
    return runCatching { JSONObject(text) }
      .onFailure { e -> Log.w("parse old script storage failed: ${e.message}") }
      .getOrNull()
  }
}
