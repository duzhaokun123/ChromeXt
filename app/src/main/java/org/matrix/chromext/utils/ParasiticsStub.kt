package org.matrix.chromext.utils

import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.os.Build
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.matrix.chromext.Chrome
import org.matrix.chromext.ScriptManagerActivity
import org.matrix.chromext.MainHook as AppMainHook
import org.matrix.chromext.R as AppR
import org.matrix.chromext.utils.Log as AppLog

object ParasiticsStub {
  object R {
    object string {
      @JvmField
      val res_inject_success = AppR.string.res_inject_success
    }
  }

  object StartupInfo {
    @JvmStatic
    fun getModulePath(): String? = AppMainHook.modulePath
  }

  object MainHook

  object SyncUtils {
    @OptIn(DelicateCoroutinesApi::class)
    @JvmStatic
    fun runOnUiThread(r: Runnable) {
      GlobalScope.launch(Dispatchers.Main) {
        r.run()
      }
    }
  }

  object Log {
    @JvmStatic
    fun e(message: Any) {
      AppLog.e(message)
    }

    @JvmStatic
    fun i(message: Any) {
      AppLog.i(message)
    }
  }

  object Initiator {
    @JvmStatic
    fun getPluginClassLoader(): ClassLoader? {
      return this::class.java.classLoader
    }

    @JvmStatic
    fun getHostClassLoader(): ClassLoader {
      return AppMainHook.hostClassloader!!
    }
  }

  object HostInfo {
    @JvmStatic
    fun getApplication(): Application {
      return Chrome.getContext().applicationContext as Application
    }

    @JvmStatic
    fun getPackageName(): String {
      return Chrome.packageName
    }
  }

  object ActProxyMgr {
    const val STUB_DEFAULT_ACTIVITY = "org.chromium.chrome.browser.settings.SettingsActivity"
    const val STUB_TRANSLUCENT_ACTIVITY = STUB_DEFAULT_ACTIVITY
    const val STUB_TOOL_ACTIVITY = STUB_DEFAULT_ACTIVITY

    const val ACTIVITY_PROXY_INTENT = "chromext.ACTIVITY_PROXY_INTENT"

    private val moduleActivity = listOf(ScriptManagerActivity::class.java).map { it.name }

    @JvmStatic
    fun isModuleProxyActivity(name: String): Boolean {
      return name in moduleActivity
    }

    @JvmStatic
    fun isModuleBundleClassLoaderRequired(name: String): Boolean {
      return isModuleProxyActivity(name)
    }
  }

  interface WindowIsTranslucent

  object CounterfeitActivityInfoFactory {
    @JvmStatic
    fun makeProxyActivityInfo(className: String, flags: Long): ActivityInfo? {
      val activityInfo = Chrome.getContext().packageManager.getActivityInfo(
        ComponentName(Chrome.getContext(), ActProxyMgr.STUB_DEFAULT_ACTIVITY), flags.toInt()
      )
      activityInfo.apply {
        targetActivity = null
        taskAffinity = null
        descriptionRes = 0
        name = className
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          splitName = null
        }
      }
      return activityInfo
    }
  }
}