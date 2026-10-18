package org.matrix.chromext.utils

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

fun String.md5(): String {
  val md5 = MessageDigest.getInstance("MD5")
  val digest = md5.digest(this.toByteArray())
  return digest.toHexString()
}

fun JSONArray.toList(): List<JSONObject> {
  return buildList {
    for (i in 0 until this@toList.length()) {
      add(this@toList.getJSONObject(i))
    }
  }
}