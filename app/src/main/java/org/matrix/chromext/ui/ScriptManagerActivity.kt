package org.matrix.chromext.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toolbar
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.db.entity.Script
import org.matrix.chromext.script.LoadedScript
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.script.ScriptFileStorage
import org.matrix.chromext.script.ScriptMetadata
import org.matrix.chromext.utils.ScriptParser

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

  private val scope = MainScope()

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

          view.findViewById<TextView>(R.id.tv_name).text = metadata.localeName
          view.findViewById<TextView>(R.id.tv_info).text = """
            |version: ${metadata.version}
            |author: ${metadata.author}
            |id: ${script.id}
            |uuid: ${script.uuid}
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
    updateScriptInfo()
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_script_manager, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
    when(item.itemId) {
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
    insets.getInsets(WindowInsetsCompat.Type.systemBars()).let {systemBars ->
      toolbar.updatePadding(top = systemBars.top)
      lv_scripts.updatePadding(bottom = systemBars.bottom)
    }
    return WindowInsetsCompat.CONSUMED
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
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
      scope.launch(Dispatchers.Main) {
        tv_info.text = """
          |loaded scripts ${loadedScripts.size}
          |install scripts ${installScripts.size}
        """.trimMargin()
        (lv_scripts.adapter as BaseAdapter).notifyDataSetChanged()
      }
    }
  }
}