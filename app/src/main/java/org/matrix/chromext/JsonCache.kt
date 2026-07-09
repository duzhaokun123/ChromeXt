package org.matrix.chromext

import org.json.JSONArray
import org.json.JSONObject
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import java.io.File

@OptIn(DexKitExperimentalApi::class)
class JsonCache(private val file: File) : DexKitCacheBridge.Cache {
  private var root = JSONObject()

  init {
    runCatching {
      if (file.exists()) {
        root = JSONObject(file.readText())
      }
    }
  }

  @Synchronized
  private fun persist() {
    runCatching {
      file.parentFile?.mkdirs()
      file.writeText(root.toString())
    }
  }

  @Synchronized
  override fun getString(key: String, default: String?): String? {
    return if (root.has(key)) root.getString(key) else default
  }

  @Synchronized
  override fun putString(key: String, value: String) {
    root.put(key, value)
    persist()
  }

  @Synchronized
  override fun getStringList(key: String, default: List<String>?): List<String>? {
    if (!root.has(key)) return default
    val array = root.getJSONArray(key)
    return List(array.length()) { array.getString(it) }
  }

  @Synchronized
  override fun putStringList(key: String, value: List<String>) {
    root.put(key, JSONArray(value))
    persist()
  }

  @Synchronized
  override fun remove(key: String) {
    root.remove(key)
    persist()
  }

  @Synchronized
  override fun getAllKeys(): Collection<String> {
    val keys = LinkedHashSet<String>()
    root.keys().forEach { keys.add(it) }
    return keys
  }

  @Synchronized
  override fun clearAll() {
    root = JSONObject()
    persist()
  }
}
