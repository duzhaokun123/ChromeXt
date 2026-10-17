package org.matrix.chromext

import android.content.Context
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.qauxv.lifecycle.Parasitics
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.matrix.chromext.hook.BaseHook
import org.matrix.chromext.hook.ContextMenuHook
import org.matrix.chromext.hook.PageMenuHook
import org.matrix.chromext.hook.PreferenceHook
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.hookAfter

val supportedPackages =
    arrayOf(
        "com.android.chrome",
      )

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
  companion object {
    var modulePath: String? = null
    var hostClassloader: ClassLoader? = null
  }

  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    Log.d(lpparam.processName + " started")
    if (lpparam.packageName == "org.matrix.chromext") return
    if (supportedPackages.contains(lpparam.packageName)) {
      hostClassloader = lpparam.classLoader
      lpparam.classLoader
          .loadClass("org.chromium.ui.base.WindowAndroid")
          .declaredConstructors[1]
          .hookAfter {
            HiddenApiBypass.setHiddenApiExemptions("");
            Chrome.init(it.args[0] as Context, lpparam.packageName)
            Parasitics.initForStubActivity(Chrome.getContext())
            if(!Parasitics::class.java.getDeclaredField("__stub_hooked")
                .also { it.isAccessible = true }.getBoolean(null)) {
              Log.toast(Chrome.getContext(), "Parasitics not load!")
            }
            initHooks(UserScriptHook)
            if (ContextMenuHook.isInit) return@hookAfter
            runCatching {
                  initHooks(PreferenceHook)
                  initHooks(PageMenuHook)
                }
                .onFailure {
                  initHooks(ContextMenuHook)
                  if (BuildConfig.DEBUG) Log.ex(it)
                }
          }
    } else {
      Log.e("Unsupported package: ${lpparam.packageName}, not Google Chrome stable")
    }
  }

  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    modulePath = startupParam.modulePath
    Resource.init(startupParam.modulePath)
  }

  private fun initHooks(vararg hook: BaseHook) {
    hook.forEach {
      if (it.isInit) return@forEach
      it.init()
      if (it.isInit) Log.d("${it.javaClass.simpleName} hooked")
    }
  }
}
