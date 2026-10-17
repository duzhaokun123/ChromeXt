package org.matrix.chromext.script

import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log
import java.io.File

object ScriptFileStorage {
  val storagePath: File

  init {
    val context = Chrome.getContext()
    storagePath = context.filesDir.resolve("ChromeXt").resolve("script")
      .also { it.mkdirs() }
  }

  fun getScript(id: String): String? {
    runCatching {
      return storagePath.resolve("$id.user.js").readText()
    }.onFailure { e ->
      Log.e("getScript $id failed", e)
    }
    return null
  }

  fun putScript(id: String, content: String): Boolean {
    runCatching {
      storagePath.resolve("$id.user.js").writeText(content)
      return true
    }.onFailure { e ->
      Log.e("putScript $id failed", e)
    }
    return false
  }

  fun deleteScript(vararg ids: String) {
    ids.forEach { id ->
      runCatching {
        storagePath.resolve("$id.user.js").delete()
      }.onFailure { e ->
        Log.e("deleteScript $id failed", e)
      }
    }
  }

  fun deleteAll() {
    storagePath.listFiles()?.forEach { file ->
      file.delete()
    }
  }
}