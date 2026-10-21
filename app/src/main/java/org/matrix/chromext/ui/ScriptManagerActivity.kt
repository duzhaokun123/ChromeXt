package org.matrix.chromext.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toolbar
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.db.entity.Script
import org.matrix.chromext.script.LoadedScript
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.ScriptFileStorage
import org.matrix.chromext.script.UserscriptMigration
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ScriptMetadata
import org.matrix.chromext.utils.ScriptParser
import org.matrix.chromext.utils.loadExternalResource

data class InstallScriptInfo(
  val script: Script,
  val metadata: ScriptMetadata,
  val fileSize: Long
)

class ScriptManagerActivity : Activity(),
  OnApplyWindowInsetsListener {
  val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }
  val tv_info by lazy { findViewById<TextView>(R.id.tv_info) }
  val lv_scripts by lazy { findViewById<ListView>(R.id.lv_scripts) }

  var loadedScripts: List<LoadedScript> = emptyList()
  var installScripts: List<InstallScriptInfo> = emptyList()
  var scriptIcon: Map<String, Drawable> = emptyMap()

  private val scope = MainScope()
  private var importing = false
  private var importItem: MenuItem? = null
  private var restartRequired = false

  override fun onCreate(savedInstanceState: Bundle?) {
    Resource.enrich(this)

    setTheme(android.R.style.Theme_DeviceDefault_NoActionBar)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_script_manager)
    setActionBar(toolbar)
    setTitle("Script Manager")

    tv_info.text = """
      |loading...
      |(if it keep loading, database broken, try reset ChromeXt)
    """.trimMargin()

    lv_scripts.adapter =
      object : BaseAdapter() {
        override fun getCount() = installScripts.size

        override fun getItem(position: Int) = installScripts[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(
          position: Int,
          convertView: View?,
          parent: ViewGroup?
        ): View? {
          val view = convertView ?: LayoutInflater.from(this@ScriptManagerActivity)
            .inflate(R.layout.item_script, parent, false)

          val (script, metadata, fileSize) = installScripts[position]

          val icon = scriptIcon[script.id]
          view.findViewById<ImageView>(R.id.iv_icon).let { iv_icon ->
            if (icon != null) {
              iv_icon.setImageDrawable(icon)
            } else {
              iv_icon.setImageResource(R.drawable.hide_image_24px)
            }
          }
          view.findViewById<TextView>(R.id.tv_name).text = metadata.localeName
          view.findViewById<TextView>(R.id.tv_info).text = """
            |version: ${metadata.version}
            |author: ${metadata.author}
            |id: ${script.id}
            |uuid: ${script.uuid}
            |enabled: ${script.enabled}
            |fileSize: ${if (fileSize != -1L) fileSize else "file lost!"}
            |
            |${metadata.localeDescription}
          """.trimMargin()
          view.findViewById<Button>(R.id.btn_delete).setOnClickListener {
            AlertDialog.Builder(this@ScriptManagerActivity)
              .setTitle("Delete ${metadata.localeName} ?")
              .setNegativeButton(android.R.string.cancel, null)
              .setPositiveButton(android.R.string.ok) { _, _ ->
                ScriptDbManager.deleteScript(script.id)
                updateScriptInfo()
              }.show()
          }
          view.findViewById<Button>(R.id.btn_edit).setOnClickListener {
            startActivity(
              Intent(
                this@ScriptManagerActivity,
                ScriptConfigActivity::class.java
              ).apply {
                putExtra("id", script.id)
              })
          }

          return view
        }
      }

    ViewCompat.setOnApplyWindowInsetsListener(window.decorView, this)
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_script_manager, menu)
    importItem = menu?.findItem(R.id.import_old_db)
    importItem?.isVisible = UserscriptMigration.hasOldDatabase()
    return super.onCreateOptionsMenu(menu)
  }

  override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.import_old_db -> {
        importOldDatabase()
        return true
      }

      R.id.reflash -> {
        updateScriptInfo()
        return true
      }

      R.id.install -> {
        startActivity(Intent(this, ScriptInstallActivity::class.java))
        return true
      }
    }
    return super.onMenuItemSelected(featureId, item)
  }

  override fun onApplyWindowInsets(
    v: View,
    insets: WindowInsetsCompat
  ): WindowInsetsCompat {
    insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { systemBars ->
      toolbar.updatePadding(top = systemBars.top)
      lv_scripts.updatePadding(bottom = systemBars.bottom)
    }
    return WindowInsetsCompat.CONSUMED
  }

  override fun onResume() {
    super.onResume()
    updateScriptInfo()
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }

  private fun importOldDatabase() {
    if (importing) return
    if (!UserscriptMigration.hasOldDatabase()) {
      Log.toast(this, "no old version scripts found")
      return
    }
    AlertDialog.Builder(this)
      .setTitle("Import scripts from old version?")
      .setMessage("Chrome must be restarted afterwards. The old database is deleted on success.")
      .setNegativeButton(android.R.string.cancel, null)
      .setPositiveButton(android.R.string.ok) { _, _ -> runMigration() }
      .show()
  }

  private fun runMigration() {
    importing = true
    importItem?.isEnabled = false
    scope.launch(Dispatchers.IO) {
      val result = runCatching { UserscriptMigration.migrate(ScriptDbManager.db) }
      withContext(Dispatchers.Main) {
        importing = false
        importItem?.isEnabled = true
        result
          .onSuccess { r ->
            if (r.failed == 0) {
              restartRequired = true
              Log.toast(
                this@ScriptManagerActivity,
                "Imported ${r.imported}, skipped ${r.skipped}. Restart Chrome to apply")
              // old database is gone, hide the menu item
              invalidateOptionsMenu()
            } else {
              if (r.imported > 0) {
                restartRequired = true
              }
              Log.toast(
                this@ScriptManagerActivity,
                "Imported ${r.imported}, ${r.failed} scripts failed, old database kept")
            }
          }
          .onFailure { e ->
            Log.ex(e, "import old database failed")
            Log.toast(this@ScriptManagerActivity, "import failed: ${e.message}")
          }
        updateScriptInfo()
      }
    }
  }

  private fun updateScriptInfo() {
    scope.launch(Dispatchers.IO) {
      loadedScripts = ScriptDbManager.loadedScripts
      installScripts =
        ScriptDbManager.getAllScript()
          .map {
            InstallScriptInfo(
              it, ScriptParser.parseMetadata(it.meta), ScriptFileStorage.getScriptSize(it.id)
            )
          }
      launch(Dispatchers.IO) {
        scriptIcon = installScripts.mapNotNull { (script, metadata, _) ->
          if (metadata.icon == null) return@mapNotNull null
          async {
            script.id to runCatching {
              val data = loadExternalResource(metadata.icon!!)
              val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                ?: throw RuntimeException("image ${metadata.icon} decode failed")
              bitmap.toDrawable(resources)
            }.onFailure { e ->
              Log.w(e)
            }
          }
        }.awaitAll().mapNotNull {
          val icon = it.second.getOrNull() ?: return@mapNotNull null
          it.first to icon
        }.toMap()
        withContext(Dispatchers.Main) {
          (lv_scripts.adapter as BaseAdapter).notifyDataSetChanged()
        }
      }
      launch(Dispatchers.Main) {
        tv_info.text =
          """
            |loaded scripts ${loadedScripts.size}
            |install scripts ${installScripts.size}
          """
            .trimMargin() +
            if (restartRequired) "\nrestart Chrome to load imported scripts" else ""
        (lv_scripts.adapter as BaseAdapter).notifyDataSetChanged()
      }
    }
  }
}