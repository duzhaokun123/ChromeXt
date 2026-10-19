package org.matrix.chromext.script

import android.content.Context
import androidx.room3.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.db.ChromeXtDatabase
import org.matrix.chromext.db.entity.Script
import org.matrix.chromext.db.entity.ScriptStorage
import org.matrix.chromext.utils.ScriptParser
import org.matrix.chromext.utils.invokeMethod
import org.matrix.chromext.utils.isDevToolsFrontEnd
import org.matrix.chromext.utils.isUserScript
import org.matrix.chromext.utils.matching
import org.matrix.chromext.utils.parseOrigin
import org.matrix.chromext.utils.resolveContentUrl
import org.matrix.chromext.utils.shouldBypassSandbox
import java.util.UUID

object ScriptDbManager {
  // HACK! should not keep old code's Script
  var scripts: MutableList<RuntimeScript>
    private set
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

    scripts = loadRuntimeScript()
  }

  fun upsert(vararg script: Script) {
    runBlocking {
      db.scriptDao().upsert(*script)
    }
    scripts = loadRuntimeScript()
  }

  fun upsertScriptFile(scriptFile: String): Boolean {
    val (metaString, _) = ScriptParser.splitScript(scriptFile) ?: return false
    val metadata = ScriptParser.parseMetadata(metaString ?: return false)
    val success = ScriptFileStorage.putScript(metadata.id, scriptFile)
    if (!success) {
      return false
    }
    runBlocking {
      val script = db.scriptDao().getById(metadata.id)
        ?.copy(meta = metaString)
        ?: Script(metadata.id, UUID.randomUUID(), metaString)
      db.scriptDao().upsert(script)
    }
    scripts = loadRuntimeScript()
    return true
  }

  fun invokeScript(url: String, webView: Any? = null, frameId: String? = null) {
    val codes = mutableListOf<String>(Local.initChromeXt)
    val path = resolveContentUrl(url)
    val webSettings = webView?.invokeMethod { name == "getSettings" }

    var trustedPage: Boolean
    // Whether ChromeXt is accessible in the global context
    var runScripts = false
    // Whether UserScripts are invoked
    var bypassSandbox = false

    if (isUserScript(url, path)) {
      codes.add(Local.promptInstallUserScript)
      bypassSandbox = shouldBypassSandbox(url)
    }

    if (isDevToolsFrontEnd(url)) {
      trustedPage = true
      codes.add(Local.customizeDevTool)
      webSettings?.invokeMethod(null) { name == "setUserAgentString" }
    } else {
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
      }
      trustedPage = false
      runScripts = true
    }

    if (trustedPage) {
      codes.add("globalThis.ChromeXt = Symbol.ChromeXt;")
    } else {
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
      db.scriptStorageDao().upsert(
      scripts.map {
        ScriptStorage(it.id, it.storage ?: JSONObject())
      })
    }
  }

  fun deleteScript(vararg ids: String) {
    runBlocking {
      db.scriptDao().deleteById(*ids)
      db.scriptStorageDao().deleteById(*ids)
    }
    ScriptFileStorage.deleteScript(*ids)
    scripts = loadRuntimeScript()
  }

  fun getAllScript(): List<Script> {
    return runBlocking {
      db.scriptDao().getAll()
    }
  }

  fun getScript(id: String): Script? {
    return runBlocking {
      db.scriptDao().getById(id)
    }
  }

  // HACK! should split script and storage
  private fun loadRuntimeScript(): MutableList<RuntimeScript> {
     return runBlocking { db.scriptDao().getAll() }
      .mapNotNull { script ->
        val scriptFile = ScriptFileStorage.getScript(script.id) ?: return@mapNotNull null
        val (_, code) = ScriptParser.splitScript(scriptFile) ?: return@mapNotNull null
        // HACK! should replace old parser
        var meta = script.meta.replace("// ==/UserScript==", "") +
            script.userMatch.split("\n").filter { it.isNotBlank() }.joinToString("\n","\n") { "// @match $it" } +
            script.userInclude.split("\n").filter { it.isNotBlank() }.joinToString("\n", "\n") { "// @include $it" } +
            script.userExclude.split("\n").filter { it.isNotBlank() }.joinToString("\n", "\n") { "// @exclude $it" } +
            script.userConnect.split("\n").filter { it.isNotBlank() }.joinToString("\n", "\n") { "// @connect $it" }
        if (script.userRunAt != Script.RunAt.UNSET) {
          meta = meta.replace(Regex("""^//\s+@run-at\s+.+$""", RegexOption.MULTILINE), "") +
              "\n// @run-at ${script.userRunAt.value}\n"
        }
        meta += "\n// ==/UserScript==\n\n"
        val storage = runBlocking { db.scriptStorageDao().getById(script.id) } ?: JSONObject()
        val runtimeScript =
          parseScript(meta + code, uuid = script.uuid.toString(), storage = storage.toString())
        return@mapNotNull runtimeScript?.copy(id = script.id)
      }
      .toMutableList()
  }
}
