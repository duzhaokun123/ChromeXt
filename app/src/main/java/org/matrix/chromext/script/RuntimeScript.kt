package org.matrix.chromext.script

import org.json.JSONObject
import java.util.Locale

data class RuntimeScript(
  val id: String,
  val meta: String,
  val code: String,
  var storage: JSONObject?,
  val match: List<String> = mutableListOf(),
  val grant: List<String> = mutableListOf(),
  val exclude: List<String> = mutableListOf(),
  val lib: List<String> = mutableListOf(),
  val noframes: Boolean = true,
)

data class ScriptMetadata(
  val match: List<String>,
  val include: List<String>,
  val exclude: List<String>,
  val grant: List<String>,
  val require: List<String>,
  val connect: List<String>,
  val raw: List<Pair<String, String?>>
) {
  val id get() = "$namespace:$name"
  val name get() = getMetadata("name")
  val namespace get() = getMetadata("namespace")
  val description get() = getMetadata("description")
  val author get() = getMetadata("author")
  val version get() = getMetadata("version")

  val localeName get() = getLocaleMetadata("name")
  val localeDescription get() = getLocaleMetadata("description")

  fun getMetadata(key: String): String? {
    return raw.find { it.first == key }?.second
  }

  fun getLocaleMetadata(key: String, locale: Locale? = null): String? {
    val locale = locale ?: Locale.getDefault()
    return getMetadata("$key:${locale.language}-${locale.country}")
      ?: getMetadata("$key:${locale.language}")
      ?: getMetadata(key)
  }

  fun hasMetadata(key: String): Boolean {
    return raw.find { it.first == key } != null
  }
}