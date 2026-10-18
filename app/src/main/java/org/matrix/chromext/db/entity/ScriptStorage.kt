package org.matrix.chromext.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import org.json.JSONObject

@Entity
data class ScriptStorage(
  @PrimaryKey
  val id: String,
  var storage: JSONObject = JSONObject(),
)