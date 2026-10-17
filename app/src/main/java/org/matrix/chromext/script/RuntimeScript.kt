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