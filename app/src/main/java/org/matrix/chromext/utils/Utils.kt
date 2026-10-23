package org.matrix.chromext.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import org.json.JSONArray
import org.json.JSONObject
import org.matrix.chromext.Chrome
import java.security.MessageDigest
import kotlin.io.encoding.Base64

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

fun dp2px(dp: Number, resources: Resources? = null): Int {
  val resources = resources ?: Chrome.getContext().resources
  return (dp.toDouble() * resources.displayMetrics.density).toInt()
}

suspend fun loadExternalResource(url: String): ByteArray {
  val uri = url.toUri()
  return when (uri.scheme) {
    "http", "https" -> {
      Chrome.httpClient.get(uri.toString()).bodyAsBytes()
    }

    "content" -> {
      Chrome.getContext().contentResolver.openInputStream(uri)
        ?.use { it.readBytes() }
        ?: throw RuntimeException("contentResolver open $uri return null")
    }

    "file" -> {
      uri.toFile().readBytes()
    }
    /**
     * RFC 2397: The "data" URL scheme
     *
     * dataurl    := "data:" [ mediatype ] [ ";base64" ] "," data
     * mediatype  := [ type "/" subtype ] *( ";" parameter )
     * data       := *urlchar
     * parameter  := attribute "=" value
     *
     */
    "data" -> {
      val line = url.removePrefix("data:")
      val comma = line.indexOf(",")
      if (comma == -1) throw RuntimeException("loadExternalResource unsupported data url for $uri")
      val p1 = line.substring(0, comma)
      val p2 = line.substring(comma + 1)
      if (p1.split(";").last().equals("base64", ignoreCase = true)) {
        Base64.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)
          .decode(Uri.decode(p2))
      } else {
        Uri.decode(p2).toByteArray()
      }
    }

    else -> {
      throw RuntimeException("loadExternalResource unsupported scheme ${uri.scheme} for $uri")
    }
  }
}

fun Context.startActivitySafe(intent: Intent): Throwable? {
  return runCatching {
    if (this !is Activity) {
      Log.w("start from outside of an Activity ")
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    this.startActivity(intent)
  }.exceptionOrNull()
}