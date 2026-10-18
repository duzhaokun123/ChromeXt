package org.matrix.chromext.script

import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.md5
import java.io.File

object ScriptFileStorage {
  val storagePath: File

  init {
    val context = Chrome.getContext()
    storagePath = context.filesDir.resolve("ChromeXt").resolve("script")
      .also { it.mkdirs() }
  }

  fun getScript(id: String): String? {
    val idMd5 = id.md5()
    runCatching {
      return storagePath.resolve("$idMd5.user.js").readText()
    }.onFailure { e ->
      Log.e("getScript $id (md5: $idMd5) failed", e)
    }
    return null
  }

  fun putScript(id: String, content: String): Boolean {
    val idMd5 = id.md5()
    runCatching {
      storagePath.resolve("$idMd5.user.js").writeText(content)
      return true
    }.onFailure { e ->
      Log.e("putScript $id (md5: $idMd5) failed", e)
    }
    return false
  }

  fun deleteScript(vararg ids: String) {
    ids.forEach { id ->
      val idMd5 = id.md5()
      runCatching {
        storagePath.resolve("$idMd5.user.js").delete()
      }.onFailure { e ->
        Log.e("deleteScript $id (md5: $idMd5) failed", e)
      }
    }
  }

  fun deleteAll() {
    storagePath.listFiles()?.forEach { file ->
      file.delete()
    }
  }

  fun getScriptSize(id: String): Long {
    val idMd5 = id.md5()
    runCatching {
      val file = storagePath.resolve("$idMd5.user.js")
      if (file.exists()) {
        return file.length()
      }
    }.onFailure { e ->
      Log.e("getScriptSize $id (md5: $idMd5) failed", e)
    }
    return -1
  }
}