package org.matrix.chromext.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toolbar
import androidx.core.graphics.drawable.toDrawable
import androidx.core.text.inSpans
import androidx.core.text.italic
import androidx.core.text.scale
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ScriptParser
import org.matrix.chromext.utils.dp2px
import org.matrix.chromext.utils.loadExternalResource

class ScriptInstallActivity : Activity(), OnApplyWindowInsetsListener {
  companion object {
    const val PICK_FILE = 1

    val grantChromeXt = listOf(
      "GM.ChromeXt",
      "GM_ChromeXt"
    )
    val grantHighRisk = listOf(
      "GM_notification",
      "GM_setClipboard",
      "GM_cookie",
      "GM.notification",
      "GM.setClipboard",
      "GM.cookie"
    )
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
    when (requestCode) {
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
    val url = et_url.text.toString()
    scope.launch(Dispatchers.IO) {
      runCatching {
        scriptFile = loadExternalResource(url)!!.decodeToString()
        val metadata = ScriptParser.parseScriptMetadata(scriptFile!!)

        var scriptIcon: Drawable? = null
        if (metadata?.icon != null) {
          runCatching {
            val data = loadExternalResource(metadata.icon!!)
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
              ?: throw RuntimeException("image ${metadata.icon} decode failed")
            scriptIcon = bitmap.toDrawable(resources)
            scriptIcon.setBounds(0, 0, dp2px(24), dp2px(24))
          }.onFailure { e ->
            Log.w("load script icon failed", e)
          }
        }

        val installedScript = if (metadata != null) ScriptDbManager.getScript(metadata.id) else null
        val installedScriptMetadata =
          if (installedScript != null) ScriptParser.parseMetadata(installedScript.meta) else null

        withContext(Dispatchers.Main) {
          tv_raw.text = scriptFile
          if (metadata == null) {
            btn_install.isEnabled = false
            tv_info.text = "script parse failed!"
          } else {
            btn_install.isEnabled = true
            val sb = SpannableStringBuilder()
            if (scriptIcon != null) {
              val imageSpan = ImageSpan(scriptIcon)
              val ss = SpannableString("icon")
              ss.setSpan(imageSpan, 0, 4, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
              sb.appendLine(ss)
            }
            sb.scale(0.9F, { append("name: ") })
            metadata.localeName?.let { sb.append(it) } ?: run { sb.italic { append("<null>") } }
            sb.appendLine()
            sb.scale(0.9F, { append("version: ") })
            metadata.version?.let { sb.append(it) } ?: run { sb.italic { append("<null>") } }
            sb.appendLine()
            sb.scale(0.9F, { append("author: ") })
            metadata.author?.let { sb.append(it) } ?: run { sb.italic { append("<null>") } }
            sb.appendLine()
            sb.appendLine()
            metadata.localeDescription?.let { sb.appendLine(it) }
              ?: run { sb.italic { append("No Description") } }
            sb.appendLine()
            sb.appendLine("@grant:")
            metadata.grant.forEach { grant ->
              when (grant) {
                in grantChromeXt -> {
                  sb.inSpans(
                    StyleSpan(Typeface.BOLD),
                    ForegroundColorSpan(Color.WHITE),
                    BackgroundColorSpan(Color.RED)
                  ) {
                    appendLine(grant)
                  }
                }

                in grantHighRisk -> {
                  sb.inSpans(StyleSpan(Typeface.BOLD), ForegroundColorSpan(Color.RED)) {
                    appendLine(grant)
                  }
                }

                else -> sb.appendLine(grant)
              }
            }
            sb.appendLine()
            sb.appendLine("@match")
            metadata.match.forEach { match ->
              sb.appendLine(match)
            }
            sb.appendLine()
            sb.appendLine("@include")
            metadata.include.forEach { include ->
              sb.appendLine(include)
            }
            sb.appendLine()
            sb.appendLine("@exclude")
            metadata.exclude.forEach { exclude ->
              sb.appendLine(exclude)
            }
            sb.appendLine()
            sb.appendLine("@connect")
            metadata.connect.forEach { connect ->
              sb.appendLine(connect)
            }

            tv_info.text = sb

            if (installedScriptMetadata == null) {
              btn_install.text = "install"
              tv_installHint.text = "new script"
            } else {
              tv_installHint.text = "${installedScriptMetadata.version} -> ${metadata.version}"
              if (metadata.version == installedScriptMetadata.version) {
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