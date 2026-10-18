package org.matrix.chromext.script

import org.json.JSONObject

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
  val name get() = findMetadata("name")
  val description get() = findMetadata("description")
  val author get() = findMetadata("author")
  val version get() = findMetadata("version")

  fun findMetadata(key: String): String? {
    return raw.find { it.first == key }?.second
  }

  fun hasMetadata(key: String): Boolean {
    return raw.find { it.first == key } != null
  }
}