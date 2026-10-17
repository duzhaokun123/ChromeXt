package org.matrix.chromext.db

import androidx.room3.ColumnTypeConverter
import org.json.JSONObject

object Converters {
  @ColumnTypeConverter
  fun StringToJSONObject(value: String?): JSONObject? {
    value ?: return null
    return JSONObject(value)
  }

  @ColumnTypeConverter
  fun JSONObjectToString(value: JSONObject?): String? {
    value ?: return null
    return value.toString()
  }
}