package org.matrix.chromext.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity
data class Script(
  @PrimaryKey
  val id: String,
  val meta: String,
//  var enabled: Boolean = true,
  var userRunAt: RunAt = RunAt.UNSET,
  var userMatch: String = "",
  var userInclude: String = "",
  var userExclude: String = "",
  var userConnect: String = "",
) {
  enum class RunAt(val value: String) {
    UNSET("unset"),
    DOCUMENT_START("document-start"),
    DOCUMENT_END("document-end"),
    DOCUMENT_IDLE("document-idle");

    companion object {
      fun fromValue(value: String): RunAt? {
        return when (value) {
          "unset" -> UNSET
          "document-start" -> DOCUMENT_START
          "document-end" -> DOCUMENT_END
          "document-idle" -> DOCUMENT_IDLE
          else -> null
        }
      }
    }

    override fun toString(): String {
      return value
    }
  }
}