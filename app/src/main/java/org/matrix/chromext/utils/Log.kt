package org.matrix.chromext.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
import java.lang.ref.WeakReference
import org.matrix.chromext.BuildConfig
import org.matrix.chromext.TAG

object Log {
  private var lastToast: WeakReference<Toast>? = null

  fun i(vararg messages: Any?) {
    val msg = messages.normalizeToSting()
    Log.i(TAG, msg)
    XposedBridge.log("ChromeXt logging: " + msg)
  }

  fun d(vararg messages: Any?, full: Boolean = false) {
    val msg = messages.normalizeToSting()
    if (BuildConfig.DEBUG) {
      if (!full && msg.length > 300) {
        Log.d(TAG, msg.take(300) + " ...")
      } else {
        Log.d(TAG, msg)
      }
    }
  }

  fun w(vararg messages: Any?) {
    val msg = messages.normalizeToSting()
    Log.w(TAG, msg)
  }

  fun e(vararg messages: Any?) {
    val msg = messages.normalizeToSting()
    Log.e(TAG, msg)
    XposedBridge.log("ChromeXt error: " + msg)
  }

  fun ex(thr: Throwable, vararg messages: Any?) {
    val msg = messages.normalizeToSting()
    Log.e(TAG, msg, thr)
    XposedBridge.log("ChromeXt exception caught: [${msg}] " + thr.toString())
  }

  fun toast(context: Context, vararg messages: Any?) {
    val msg = messages.normalizeToSting()
    this.lastToast?.get()?.cancel()
    val duration = Toast.LENGTH_SHORT
    val toast = Toast.makeText(context, msg, duration)
    toast.show()
    this.lastToast = WeakReference(toast)
  }

  private fun Array<*>.normalizeToSting(): String {
    return this.joinToString(separator = " ") {
      when (it) {
        is Throwable -> it.stackTraceToString()
        is Array<*> -> it.contentDeepToString()
        else -> it.toString()
      }
    }
  }
}
