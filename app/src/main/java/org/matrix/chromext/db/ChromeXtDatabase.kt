package org.matrix.chromext.db

import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.RoomDatabase
import org.matrix.chromext.db.dao.ScriptDao
import org.matrix.chromext.db.dao.ScriptStorageDao
import org.matrix.chromext.db.entity.Script
import org.matrix.chromext.db.entity.ScriptStorage

@Database(entities = [Script::class, ScriptStorage::class], version = 1)
@ColumnTypeConverters(Converters::class)
abstract class ChromeXtDatabase : RoomDatabase() {
  abstract fun scriptDao(): ScriptDao
  abstract fun scriptStorageDao(): ScriptStorageDao
}