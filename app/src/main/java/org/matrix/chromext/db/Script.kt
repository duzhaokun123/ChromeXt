package org.matrix.chromext.db

import androidx.room3.Entity
import androidx.room3.Ignore
import androidx.room3.PrimaryKey
import org.json.JSONObject

@Entity
data class Script(
  @PrimaryKey
  val id: String,
  val meta: String,
  val code: String,
  var storage: JSONObject?,

  @Ignore
  val match: List<String> = mutableListOf(),
  @Ignore
  val grant: List<String> = mutableListOf(),
  @Ignore
  val exclude: List<String> = mutableListOf(),
  @Ignore
  val lib: List<String> = mutableListOf(),
  @Ignore
  val noframes: Boolean = true,
)