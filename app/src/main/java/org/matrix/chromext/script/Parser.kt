package org.matrix.chromext.script

import android.net.Uri
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL

public fun downloadLib(libUrl: String): String {
  if (libUrl.startsWith("data:")) {
    val chunks = libUrl.split(",").toMutableList()
    val type = chunks.removeFirst()
    val data = Uri.decode(chunks.joinToString(""))
    if (type.endsWith("base64")) {
      return Base64.decode(data, Base64.DEFAULT).toString()
    } else {
      return data
    }
  }
  val url = URL(libUrl)
  val connection = url.openConnection() as HttpURLConnection
  return connection.inputStream.bufferedReader().use { it.readText() }
}

