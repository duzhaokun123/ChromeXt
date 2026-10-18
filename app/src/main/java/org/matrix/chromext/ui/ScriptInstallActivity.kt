package org.matrix.chromext.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toolbar
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.matrix.chromext.Chrome
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ScriptParser

class ScriptInstallActivity : Activity(), OnApplyWindowInsetsListener {
  companion object {
    const val PICK_FILE = 1
  }

  val toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }
  val sv by lazy { findViewById<ScrollView>(R.id.sv) }
  val et_url by lazy { findViewById<EditText>(R.id.et_url) }
  val tv_info by lazy { findViewById<TextView>(R.id.tv_info) }
  val tv_raw by lazy { findViewById<TextView>(R.id.tv_raw) }
  val tv_installHint by lazy { findViewById<TextView>(R.id.tv_installHint) }
  val btn_install by lazy { findViewById<Button>(R.id.btn_install) }
  val btn_load by lazy { findViewById<Button>(R.id.btn_load) }

  var scriptFile: String? = null

  private val scope = MainScope()

  override fun onCreate(savedInstanceState: Bundle?) {
    Resource.enrich(this)

    setTheme(android.R.style.Theme_DeviceDefault_NoActionBar)
    super.onCreate(savedInstanceState)
    setContentView(R.layout.layout_script_install)
    setActionBar(toolbar)
    setTitle("Install Script")

    et_url.setText(intent.dataString ?: "")
    if (intent.dataString != null) {
      loadUrl()
    }

    btn_install.setOnClickListener {
      installScript()
    }
    btn_load.setOnClickListener {
      loadUrl()
    }

    ViewCompat.setOnApplyWindowInsetsListener(window.decorView, this)
  }

  override fun onApplyWindowInsets(
    v: View,
    insets: WindowInsetsCompat
  ): WindowInsetsCompat {
    insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { systemBars ->
      toolbar.updatePadding(top = systemBars.top)
      sv.updatePadding(bottom = systemBars.bottom)
    }
    return WindowInsetsCompat.CONSUMED
  }

  override fun onCreateOptionsMenu(menu: Menu?): Boolean {
    menuInflater.inflate(R.menu.menu_script_install, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onMenuItemSelected(featureId: Int, item: MenuItem): Boolean {
    when (item.itemId) {
      R.id.file -> {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
          addCategory(Intent.CATEGORY_OPENABLE)
          type = "*/*"
        }
        startActivityForResult(intent, PICK_FILE)
        return true
      }
    }
    return super.onMenuItemSelected(featureId, item)
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    when(requestCode) {
      PICK_FILE -> {
        if (resultCode == RESULT_OK) {
          et_url.setText(data?.dataString)
          loadUrl()
        }
      }
      else -> super.onActivityResult(requestCode, resultCode, data)
    }
  }

  private fun loadUrl() {
    tv_info.text = "Loading..."
    tv_raw.text = ""
    tv_installHint.text = ""
    btn_install.isEnabled = false
    btn_load.isEnabled = false
    val url = et_url.text.toString().toUri()
    scope.launch(Dispatchers.IO) {
      runCatching {
        when(url.scheme) {
          "http", "https" -> {
            val response = Chrome.httpClient.get(url.toString())
            scriptFile = response.bodyAsText()
          }
          "content" -> {
            val inputStream = contentResolver.openInputStream(url)!!
            val reader = inputStream.reader()
            scriptFile = reader.readText()
            reader.close()
            inputStream.close()
          }
          "file" -> {
            val file = url.toFile()
            scriptFile = file.readText()
          }
          else -> throw RuntimeException("loadUrl unsupported scheme ${url.scheme}")
        }
        val metadata = ScriptParser.parseScriptMetadata(scriptFile!!)
        withContext(Dispatchers.Main) {
          tv_raw.text = scriptFile
          if (metadata == null) {
            btn_install.isEnabled = false
            tv_info.text = "script parse failed!"
          } else {
            btn_install.isEnabled = true
            tv_info.text = """
              |name: ${metadata.localeName}
              |version: ${metadata.version}
              |author: ${metadata.author}
              |
              |${metadata.localeDescription}
              |
              |@grant:
              |${metadata.grant.joinToString("\n")}
              |
              |@match
              |${metadata.match.joinToString("\n")}
              |
              |@include
              |${metadata.include.joinToString("\n")}
              |
              |@exclude
              |${metadata.exclude.joinToString("\n")}
              |
              |@connect
              |${metadata.connect.joinToString("\n")}
            """.trimMargin()

            val runtimeScript = ScriptDbManager.scripts.find { it.id == metadata.id }
            val runtimeScriptMetadata = if (runtimeScript != null) ScriptParser.parseScriptMetadata(runtimeScript.meta) else null
            if (runtimeScriptMetadata == null) {
              btn_install.text = "install"
              tv_installHint.text = "new script"
            } else {
              tv_installHint.text = "${runtimeScriptMetadata.version} -> ${metadata.version}"
              if (metadata.version == runtimeScriptMetadata.version) {
                btn_install.text = "reinstall"
              } else {
                btn_install.text = "update"
              }
            }
          }
        }
      }.onFailure { e ->
        Log.ex(e)
        withContext(Dispatchers.Main) {
          tv_info.text = e.message
          tv_raw.text = e.toString()
          btn_install.isEnabled = false
        }
      }
      withContext(Dispatchers.Main) {
        btn_load.isEnabled = true
      }
    }
  }

  private fun installScript() {
    if (scriptFile != null) {
      val success = ScriptDbManager.upsertScriptFile(scriptFile!!)
      if (success) {
        Log.toast(this, "install success")
        finish()
      } else {
        Log.toast(this, "install failed")
      }
    } else {
      Log.toast(this, "script not loaded!")
    }
  }

  override fun onDestroy() {
    scope.cancel()
    super.onDestroy()
  }
}