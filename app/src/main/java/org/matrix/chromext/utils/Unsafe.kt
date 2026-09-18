package org.matrix.chromext.utils

import com.ironz.unsafe.UnsafeAndroid

object Unsafe {
  val instance by lazy { UnsafeAndroid() }
}