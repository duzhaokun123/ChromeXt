package org.matrix.chromext.utils

import java.security.MessageDigest

fun String.md5(): String {
  val md5 = MessageDigest.getInstance("MD5")
  val digest = md5.digest(this.toByteArray())
  return digest.toHexString()
}