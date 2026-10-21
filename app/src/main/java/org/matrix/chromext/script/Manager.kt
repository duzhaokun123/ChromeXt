package org.matrix.chromext.script

import android.content.Context
import androidx.room3.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.matrix.chromext.Chrome
import org.matrix.chromext.db.ChromeXtDatabase
import org.matrix.chromext.db.entity.Script
import org.matrix.chromext.db.entity.ScriptStorage
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.ScriptMetadata
import org.matrix.chromext.utils.ScriptParser
import org.matrix.chromext.utils.loadExternalResource
import java.util.UUID

data class LoadedScript(
  val script: Script,
  val metadata: ScriptMetadata,
  val options: Options,
  val code: String,
  val lib: List<String>
) {
  data class Options(
    val match: List<String>,
    val include: List<String>,
    val exclude: List<String>,
    val grant: List<String>,
    val require: List<String>,
    val connect: List<String>,
    val runAt: Script.RunAt,
    val noFrames: Boolean,
    val hasStorage: Boolean,
  )

  val id get() = script.id
  val uuid get() = script.uuid
}

val LoadedScript.storage: JSONObject?
  get() = if (this.options.hasStorage) ScriptDbManager.scriptStorage.getOrPut(this.id, { JSONObject() }) else null

object ScriptDbManager {
  val cosmeticFilters: MutableMap<String, String>
  val cspRules: MutableMap<String, String>

  val loadedScripts = mutableListOf<LoadedScript>()
  val scriptStorage = mutableMapOf<String, JSONObject>()
  val db: ChromeXtDatabase

  init {
    val ctx = Chrome.getContext()
    @Suppress("UNCHECKED_CAST")
    cosmeticFilters =
      ctx.getSharedPreferences("CosmeticFilter", Context.MODE_PRIVATE).getAll()
          as MutableMap<String, String>
    @Suppress("UNCHECKED_CAST")
    cspRules =
      ctx.getSharedPreferences("CSPRule", Context.MODE_PRIVATE).getAll()
          as MutableMap<String, String>

    db = Room.databaseBuilder<ChromeXtDatabase>(ctx, "ChromeXt")
      .build()

    runBlocking {
      loadScriptStorage()
      loadScript()
    }
  }

  fun upsert(vararg script: Script) {
    runBlocking {
      db.scriptDao().upsert(*script)
      script.forEach { script ->
        loadedScripts.removeAll { it.id == script.id }
        val loadedScript = loadScriptById(script.id)
        if (loadedScript != null) {
          loadedScripts.add(loadedScript)
          Log.i("upsert ${script.id} reloaded")
        } else if (script.enabled) {
          Log.e("upsert ${script.id} reload failed")
        }
      }
    }
  }

  fun upsertScriptFile(scriptFile: String): Boolean {
    val (metaString, _) = ScriptParser.splitScript(scriptFile) ?: return false
    val metadata = ScriptParser.parseMetadata(metaString ?: return false)
    val success = ScriptFileStorage.putScript(metadata.id, scriptFile)
    if (!success) {
      return false
    }
    runBlocking {
      loadedScripts.removeAll { it.id == metadata.id }
      val script = db.scriptDao().getById(metadata.id)
        ?.copy(meta = metaString)
        ?: Script(metadata.id, UUID.randomUUID(), metaString)
      db.scriptDao().upsert(script)
      val loadedScript = loadScriptById(script.id)
      if (loadedScript != null) {
        loadedScripts.add(loadedScript)
        Log.i("upsertScriptFile ${script.id} reloaded")
      } else if (script.enabled) {
        Log.e("upsertScriptFile ${script.id} reload failed")
      }
    }
    return true
  }

  @Deprecated(
    "use Chrome.injectChromeXtScript", ReplaceWith(
      "Chrome.injectChromeXtScript(url, webView, frameId)",
      "org.matrix.chromext.Chrome"
    )
  )
  fun invokeScript(url: String, webView: Any? = null, frameId: String? = null) {
    Chrome.injectChromeXtScript(url, webView, frameId)
  }

  fun updateScriptStorage() {
    runBlocking {
      db.scriptStorageDao().upsert(
        scriptStorage.map { (k, v) ->
          ScriptStorage(k, v)
        })
    }
  }

  fun deleteScript(vararg ids: String) {
    loadedScripts.removeAll { it.id in ids }
    ids.forEach { scriptStorage.remove(it) }
    runBlocking {
      db.scriptDao().deleteById(*ids)
      db.scriptStorageDao().deleteById(*ids)
    }
    ScriptFileStorage.deleteScript(*ids)
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

  private suspend fun loadScript() {
    db.scriptDao().getAllEnabledId()
      .forEach { id ->
        val loadedScript = loadScriptById(id)
        if (loadedScript == null) {
          Log.e("load userscript $id failed")
        } else {
          loadedScripts.add(loadedScript)
        }
      }
  }

  private suspend fun loadScriptStorage() {
    db.scriptStorageDao().getAll()
      .forEach {
        scriptStorage[it.id] = it.storage
      }
  }

  private suspend fun loadScriptById(id: String): LoadedScript? {
    val script = db.scriptDao().getById(id) ?: return null
    if (!script.enabled) return null
    val scriptFile = ScriptFileStorage.getScript(script.id) ?: return null
    val code = (ScriptParser.splitScript(scriptFile) ?: return null).second ?: return null
    val metadata = ScriptParser.parseMetadata(script.meta)
    // keep old behavior
    val addGrant = mutableListOf<String>()
    if (!metadata.grant.contains("GM_xmlhttpRequest") &&
      (metadata.grant.contains("GM_download") ||
          metadata.grant.contains("GM.xmlHttpRequest") ||
          metadata.grant.contains("GM_getResourceText"))) {
      addGrant.add("GM_xmlhttpRequest")
    }
    val hasStorage = metadata.grant.contains("GM.getValue") ||
        metadata.grant.contains("GM_getValue") ||
        metadata.grant.contains("GM_cookie")
    val options = LoadedScript.Options(
      match = metadata.match + script.userMatchList,
      include = metadata.include + script.userIncludeList,
      exclude = metadata.exclude + script.userExcludeList,
      grant = metadata.grant + addGrant,
      require = metadata.require,
      connect = metadata.connect + script.userConnectList,
      runAt = if (script.userRunAt != Script.RunAt.UNSET) script.userRunAt else metadata.`run-at`,
      noFrames = metadata.noframes,
      hasStorage = hasStorage
    )
    val lib = mutableListOf<String>()
    GlobalScope.launch(Dispatchers.IO) {
      metadata.require.forEach { runCatching { lib.add(loadExternalResource(it).decodeToString()) } }
    }
    val loadedScript = LoadedScript(
      script = script,
      metadata = metadata,
      options = options,
      code = code,
      lib = lib
    )
    return loadedScript
  }
}
