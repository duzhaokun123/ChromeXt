package org.matrix.chromext.proxy

import android.content.Context
import org.matrix.chromext.Chrome
import org.matrix.chromext.utils.findField

object PageMenuProxy {

  val chromeTabbedActivity = UserScriptProxy.chromeTabbedActivity
  val customTabActivity = Chrome.load("org.chromium.chrome.browser.customtabs.CustomTabActivity")
  val propertyModel = Chrome.load("org.chromium.ui.modelutil.PropertyModel")
  val tab = Chrome.load("org.chromium.chrome.browser.tab.Tab")
  val emptyTabObserver =
      Chrome.load("org.chromium.chrome.browser.login.ChromeHttpAuthHandler").superclass as Class<*>
  val tabImpl = UserScriptProxy.tabImpl
  val mIsLoading = UserScriptProxy.mIsLoading
  val mObservers = findField(tabImpl) { type.interfaces.contains(Iterable::class.java) }

  /**
   * public class AppMenuItemUtils {
   *     public static PropertyModel buildModelForStandardMenuItem(
   *             Context context,
   *             AppMenuItemTheme theme,
   *             @IdRes int id,
   *             @StringRes int titleId,
   *             @DrawableRes int iconResId,
   *             boolean isMenuIconAtStart);
   * }
   */
  val method_AppMenuItemUtils_buildModelForStandardMenuItem = Chrome.dexKitBridge.getMethodDirect(
    "AppMenuItemUtils.buildModelForStandardMenuItem"
  ) { dexKitBridge ->
    dexKitBridge.findMethod {
        matcher {
          returnType(Chrome.load("org.chromium.ui.modelutil.PropertyModel"))
          paramTypes(Context::class.java, null, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
        }
    }.single()
  }.getMethodInstance(Chrome.getContext().classLoader)
}
