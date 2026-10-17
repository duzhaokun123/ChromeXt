package org.matrix.chromext.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import org.json.JSONObject

@Entity
data class Script(
  @PrimaryKey
  val id: String,
  val meta: String,
//  var enabled: Boolean = true,
  var storage: JSONObject = JSONObject()
)