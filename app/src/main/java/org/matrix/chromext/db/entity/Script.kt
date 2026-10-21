package org.matrix.chromext.db.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import java.util.UUID

@Entity
data class Script(
  @PrimaryKey
  // namespace:name
  val id: String,
  val uuid: UUID,
  val meta: String,
  var enabled: Boolean = true,
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

  val userMatchList: List<String>
    get() = userMatch.split("\n").filter { it.isNotBlank() }
  val userIncludeList: List<String>
    get() = userInclude.split("\n").filter { it.isNotBlank() }
  val userExcludeList: List<String>
    get() = userExclude.split("\n").filter { it.isNotBlank() }
  val userConnectList: List<String>
    get() = userConnect.split("\n").filter { it.isNotBlank() }
}