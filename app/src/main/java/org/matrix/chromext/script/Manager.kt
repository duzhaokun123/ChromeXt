package org.matrix.chromext.script

import android.content.Context
import android.net.Uri
import androidx.room3.Room
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.db.ChromeXtDatabase
import org.matrix.chromext.db.entity.Script
import org.matrix.chromext.utils.invokeMethod
import org.matrix.chromext.utils.isChromeXtFrontEnd
import org.matrix.chromext.utils.isDevToolsFrontEnd
import org.matrix.chromext.utils.isUserScript
import org.matrix.chromext.utils.matching
import org.matrix.chromext.utils.parseOrigin
import org.matrix.chromext.utils.resolveContentUrl
import org.matrix.chromext.utils.shouldBypassSandbox

object ScriptDbManager {

  val scripts: MutableList<RuntimeScript>
  val cosmeticFilters: MutableMap<String, String>
  val userAgents: MutableMap<String, String>
  val cspRules: MutableMap<String, String>

  val db: ChromeXtDatabase

  init {
    val ctx = Chrome.getContext()
    @Suppress("UNCHECKED_CAST")
    cosmeticFilters =
        ctx.getSharedPreferences("CosmeticFilter", Context.MODE_PRIVATE).getAll()
            as MutableMap<String, String>
    @Suppress("UNCHECKED_CAST")
    userAgents =
        ctx.getSharedPreferences("UserAgent", Context.MODE_PRIVATE).getAll()
            as MutableMap<String, String>
    @Suppress("UNCHECKED_CAST")
    cspRules =
        ctx.getSharedPreferences("CSPRule", Context.MODE_PRIVATE).getAll()
            as MutableMap<String, String>

    db = Room.databaseBuilder<ChromeXtDatabase>(ctx, "ChromeXt")
      .build()

    scripts = runBlocking { db.scriptDao().getAll() }
      .mapNotNull { script ->
        val parsedScript = parseScript(ScriptFileStorage.getScript(script.id) ?: "", null) ?: return@mapNotNull null
        return@mapNotNull parseScript(script.meta + parsedScript.code, script.storage.toString())
          ?.copy(id = script.id)
      }
      .toMutableList()
  }

  fun insert(vararg script: RuntimeScript) {
    script.forEach { script ->
      ScriptFileStorage.putScript(script.id, script.meta + script.code)
      runBlocking {
        db.scriptDao().upsert(
          Script(
            id = script.id,
            meta = script.meta,
            storage = scripts.find { it.id == script.id }?.storage ?: JSONObject()
          )
        )
      }
    }
  }

  fun updateScriptMeta(id: String, meta: String) {
    runBlocking {
      db.scriptDao().updateMeta(id, meta)
    }
  }

  private fun fixEncoding(url: String, path: String, codes: MutableList<String>) {
    if (path.endsWith(".js") || path.endsWith(".txt")) {
      // Fix encoding for local text files
      val inputStream = Chrome.getContext().contentResolver.openInputStream(Uri.parse(url))
      val text = inputStream?.bufferedReader()?.readText()
      if (text != null) {
        val data = JSONObject(mapOf("utf-8" to text))
        codes.add("window.content=${data};")
        codes.add(Local.encoding)
      }
      inputStream?.close()
    } else if (url.endsWith(".js") || url.endsWith(".txt")) {
      // Fix encoding for remote text files
      codes.add(Local.encoding)
    }

    if (codes.size > 1 && (url.endsWith(".txt") || path.endsWith(".txt")))
        codes.add("fixEncoding();")
  }

  fun invokeScript(url: String, webView: Any? = null, frameId: String? = null) {
    val codes = mutableListOf<String>(Local.initChromeXt)
    val path = resolveContentUrl(url)
    val webSettings = webView?.invokeMethod { name == "getSettings" }

    var trustedPage = true
    // Whether ChromeXt is accessible in the global context
    var runScripts = false
    // Whether UserScripts are invoked
    var bypassSandbox = false

    fixEncoding(url, path, codes)

    if (isUserScript(url, path)) {
      trustedPage = false
      codes.add(Local.promptInstallUserScript)
      bypassSandbox = shouldBypassSandbox(url)
    } else if (isDevToolsFrontEnd(url)) {
      codes.add(Local.customizeDevTool)
      webSettings?.invokeMethod(null) { name == "setUserAgentString" }
    } else if (!isChromeXtFrontEnd(url)) {
      val origin = parseOrigin(url)
      if (origin != null) {
        if (cspRules.contains(origin)) {
          runCatching {
            val rule = JSONArray(cspRules.get(origin))
            codes.add("Symbol.ChromeXt.cspRules.push(...${rule});${Local.cspRule}")
          }
        }
        if (cosmeticFilters.contains(origin)) {
          runCatching {
            val filter = JSONArray(cosmeticFilters.get(origin))
            codes.add("Symbol.ChromeXt.filters.push(...${filter});${Local.cosmeticFilter}")
          }
        }
        if (userAgents.contains(origin)) {
          val agent = userAgents.get(origin)
          codes.add("Object.defineProperties(window.navigator,{userAgent:{value:'${agent}'}});")
          webSettings?.invokeMethod(agent) { name == "setUserAgentString" }
        }
        trustedPage = false
        runScripts = true
      }
    }

    if (trustedPage) {
      codes.add("globalThis.ChromeXt = Symbol.ChromeXt;")
    } else if (runScripts) {
      codes.add("Symbol.ChromeXt.lock(${Local.key}, '${Local.name}');")
    }
    codes.add("//# sourceURL=local://ChromeXt/init" + if (frameId == null) "" else "/" + frameId)
    webSettings?.invokeMethod(true) { name == "setJavaScriptEnabled" }
    val initScript = codes.joinToString("\n")

    val asyncEvaluation = bypassSandbox || frameId != null
    var framesGranted = false

    if (!asyncEvaluation)
        Chrome.evaluateJavascript(
            listOf(initScript), webView, frameId, bypassSandbox, bypassSandbox)
    codes.clear()
    if (asyncEvaluation) codes.add(initScript)
    if (runScripts) {
      scripts
          .filter { matching(it, url) && !(frameId != null && it.noframes) }
          .forEach {
            if (it.grant.contains("frames")) framesGranted = true
            GM.bootstrap(it, codes)
          }
      if (!asyncEvaluation) Chrome.evaluateJavascript(codes, webView, frameId)
    }
    if (asyncEvaluation)
        Chrome.evaluateJavascript(codes, webView, frameId, bypassSandbox, bypassSandbox)

    if (framesGranted && frameId == null) Chrome.injectFrames(webView)
  }

  fun updateScriptStorage() {
    runBlocking {
      db.withWriteTransaction {
        scripts.forEach { script ->
          db.scriptDao().updateStorage(script.id, script.storage ?: JSONObject())
        }
      }
    }
  }

  fun deleteScript(vararg ids: String) {
    runBlocking {
      db.scriptDao().deleteById(*ids)
    }
    ScriptFileStorage.deleteScript(*ids)
    scripts.removeAll { it.id in ids }
  }

  fun getAllScript(): List<Script> {
    return runBlocking {
      db.scriptDao().getAll()
    }
  }
}
