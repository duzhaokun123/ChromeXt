package org.matrix.chromext.hook

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import chromium.AppMenuHandler
import de.robv.android.xposed.XC_MethodHook.Unhook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.reflect.Modifier
import java.util.ArrayList
import org.matrix.chromext.Chrome
import org.matrix.chromext.R
import org.matrix.chromext.Resource
import org.matrix.chromext.devtools.getInspectPages
import org.matrix.chromext.proxy.PageMenuProxy
import org.matrix.chromext.proxy.UserScriptProxy
import org.matrix.chromext.script.Local
import org.matrix.chromext.script.ScriptDbManager
import org.matrix.chromext.utils.*
import java.lang.reflect.Constructor
import java.util.function.Supplier
import kotlin.time.Duration.Companion.milliseconds

enum class AppMenuItemType(val value: Int) {
  /** Regular Android menu item that contains a title and an icon if icon is specified. */
  STANDARD(0),

//  /**
//   * Menu item that has two buttons, the first one is a title and the second one is an icon. It is
//   * different from the regular menu item because it contains two separate buttons.
//   */
//  TITLE_BUTTON(?),

//  /**
//   * Menu item that has multiple buttons (no more than 5). Every one of these buttons is displayed
//   * as an icon.
//   */
//  BUTTON_ROW(?),

//  /** Menu item that when contains submenus. */
//  MENU_ITEM_WITH_SUBMENU(?),

//  /** The header for submenus when submenus are displayed in drilldown. */
//  SUBMENU_HEADER(?),

  /** A divider item to distinguish between menu item groupings. */
  DIVIDER(7),

//  /**
//   * The number of menu item types specified above. If you add a menu item type you MUST increment
//   * this.
//   */
//  NUM_ENTRIES(?)
}

enum class EntryPoint(val value: Int) {
  UNKNOWN(0),

  /** The user opened reader mode through an app message. */
  MESSAGE(1),

  /** The user opened reader mode through the app menu. */
  APP_MENU(2),

  /** The user opened reader mode through the toolbar button. */
  TOOLBAR_BUTTON(3),
}

object PageMenuHook : BaseHook() {

  private fun getUrl(): String {
    return Chrome.getUrl()!!
  }

  override fun init() {
    if (ContextMenuHook.isInit) return
    val proxy = PageMenuProxy

    fun menuHandler(ctx: Context, id: Int): Boolean {
      val commandIndex = UserScriptMenuCommand.idToIndex(id)
      if (commandIndex != -1) {
        UserScriptProxy.evaluateJavascript("Symbol.${Local.name}.unlock(${Local.key}, false).commands[${commandIndex}].listener()")
        return false
      }

      when (id) {
        R.id.install_script_id -> {
          val sandBoxed = shouldBypassSandbox(getUrl())
          Chrome.evaluateJavascript(listOf("Symbol.${Local.name}.unlock(${Local.key}).dispatch('installScript', document.URL);"), null, null, sandBoxed)
        }
        R.id.eruda_console_id ->
            UserScriptProxy.evaluateJavascript(Local.openEruda)
        R.id.devtools -> {
          GlobalScope.launch(Dispatchers.IO) {
            val pages = getInspectPages()
            if (pages == null) {
              withContext(Dispatchers.Main) {
                Log.toast(ctx, "can't inspect")
              }
            } else {
              val id = Chrome.getTabId(null)
              val page = pages.toList().find { page ->
                page.get("id").toString() == id
              }
              if (page == null) {
                withContext(Dispatchers.Main) {
                  Log.toast(ctx, "page not found")
                }
              } else {
                val devtoolsFrontendUrl = page.getString("devtoolsFrontendUrl").toUri()
                val intent = CustomTabsIntent.Builder()
                  .setUrlBarHidingEnabled(true)
                  .setShowTitle(true)
                  .build().intent
                intent.apply {
                  setData(devtoolsFrontendUrl)
                  setPackage(Chrome.packageName)
                  addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
                }
                ctx.startActivity(intent)
              }
            }
          }
        }
      }
      return false
    }

    findMethod(proxy.chromeTabbedActivity) {
          // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu, ? triggeringMotion)
          (parameterCount >= 2) &&
              parameterTypes[0] == Int::class.java &&
              parameterTypes[1] == Boolean::class.java &&
              returnType == Boolean::class.java
        }
        .hookBefore {
          if (menuHandler(it.thisObject as Context, it.args[0] as Int)) {
            it.result = true
          }
        }

    findMethod(proxy.customTabActivity) {
          // public boolean onMenuOrKeyboardAction(int id, boolean fromMenu, ? triggeringMotion)
          (parameterCount >= 2) &&
              parameterTypes[0] == Int::class.java &&
              parameterTypes[1] == Boolean::class.java &&
              returnType == Boolean::class.java
        }
        .hookBefore {
          if (menuHandler(it.thisObject as Context, it.args[0] as Int)) {
            it.result = true
          }
        }

    var findMenuHook: Unhook? = null
    findMenuHook =
        findMethod(proxy.chromeTabbedActivity) {
              parameterTypes.size == 0 &&
                  returnType.declaredMethods.size >= 6 &&
                  (returnType.declaredMethods.find {
                    // Bundle getBundleForMenuItem(int itemId);
                    it.returnType == Bundle::class.java && it.parameterTypes.size == 1
                  } != null) &&
                  (returnType.declaredFields.size == 0 ||
                      returnType.declaredFields.find {
                        Context::class.java.isAssignableFrom(it.type)
                      } != null) &&
                  (returnType.isInterface() || Modifier.isAbstract(returnType.modifiers))
            }
            // public AppMenuPropertiesDelegate createAppMenuPropertiesDelegate()
            .hookAfter {
              findMenuHook!!.unhook()
              val tabbedAppMenuPropertiesDelegate = it.result::class.java
              inflateAppMenu(tabbedAppMenuPropertiesDelegate)
            }

    isInit = true
  }

  fun inflateAppMenu(tabbedAppMenuPropertiesDelegate: Class<*>): Unhook {
    val appMenuPropertiesDelegateImpl = tabbedAppMenuPropertiesDelegate.superclass as Class<*>
    // Can be found by searching `Android.PrepareMenu`

    val parameters = appMenuPropertiesDelegateImpl.declaredConstructors[0].parameterTypes
    val mContext = findField(appMenuPropertiesDelegateImpl, true) { type == parameters[0] }
    val mActivityTabProvider =
        findField(appMenuPropertiesDelegateImpl, true) { type == parameters[1] }

    val buildModelForStandardMenuItem = PageMenuProxy.method_AppMenuItemUtils_buildModelForStandardMenuItem
    //  org.chromium.chrome.browser.app.appmenu.AppMenuItemTheme
    val class_AppMenuItemTheme = buildModelForStandardMenuItem.parameterTypes[1]
    //  org.chromium.chrome.browser.app.appmenu.AppMenuPropertiesDelegateImpl.mAppMenuItemTheme
    val field_AppMenuPropertiesDelegateImpl_mAppMenuItemTheme =
      appMenuPropertiesDelegateImpl.declaredFields.find { it.type == class_AppMenuItemTheme }!!
    //  org.chromium.chrome.browser.app.appmenu.AppMenuPropertiesDelegateImpl.mContext
    val field_AppMenuPropertiesDelegateImpl_mContext =
      appMenuPropertiesDelegateImpl.declaredFields.find { it.type == Context::class.java }!!

    val MVCListAdapter_ModelList =
      PageMenuProxy.method_AppMenuItemUtils_buildModelForStandardMenuItem
        .declaringClass
        .declaredMethods
        .find {
          it.returnType == Void.TYPE && it.parameterCount == 2 && it.parameterTypes[0] != List::class.java
        }!!.parameterTypes[0]

    val mItems = findField(MVCListAdapter_ModelList, true) { type == ArrayList::class.java }

    val excludedReturnValuesForModelItem =
        arrayOf(
            MVCListAdapter_ModelList,
            Int::class.java,
            Boolean::class.java,
            Void.TYPE,
            View::class.java)
    val buildNewIncognitoTabItem =
        findMethod(tabbedAppMenuPropertiesDelegate) {
          parameterTypes.size == 0 &&
              !Modifier.isStatic(modifiers) &&
              !excludedReturnValuesForModelItem.contains(returnType)
        }
    // private MVCListAdapter.ListItem buildNewIncognitoTabItem()
    val MVCListAdapter_ListItem = buildNewIncognitoTabItem.returnType
    val mType = findField(MVCListAdapter_ListItem) { type == Int::class.java }
    // the original field name was "type"

    return findMethod(tabbedAppMenuPropertiesDelegate) {
          parameterTypes.size == 0 && returnType == MVCListAdapter_ModelList
        }
        // public MVCListAdapter.ModelList buildMenuModelList()
        .hookAfter {
          val tabProvider = mActivityTabProvider.get(it.thisObject)!!
          val currentTab = tabProvider.invokeMethod { name == "get" }
          Chrome.updateTab(currentTab)
          val ctx = mContext.get(it.thisObject) as Context

          Resource.enrich(ctx)
          val url = getUrl()

          @Suppress("UNCHECKED_CAST")
          val menuModels = mItems.get(it.result) as MutableList<Any>

          val skip = menuModels.size <= 10 || isChromeScheme(url)
          if (skip && !isUserScript(url)) return@hookAfter

          val mContext = field_AppMenuPropertiesDelegateImpl_mContext.get(it.thisObject)
          val mAppMenuItemTheme = field_AppMenuPropertiesDelegateImpl_mAppMenuItemTheme.get(it.thisObject)

          val installScriptItem = buildModelForStandardMenuItem.invoke(
            it.thisObject,
            mContext,
            mAppMenuItemTheme,
            R.id.install_script_id,
            R.string.main_menu_install_script,
            R.drawable.ic_install_script,
            false
          )

          val erudaItem = buildModelForStandardMenuItem.invoke(
            it.thisObject,
            mContext,
            mAppMenuItemTheme,
            R.id.eruda_console_id,
            R.string.main_menu_eruda_console,
            R.drawable.ic_devtools,
            false
          )

          val devtoolsItem = buildModelForStandardMenuItem.invoke(
            it.thisObject,
            mContext,
            mAppMenuItemTheme,
            R.id.devtools,
            R.string.main_menu_devtools,
            R.drawable.code_24px,
            false
          )

          val menusToAdd = mutableListOf<Any>()

          val itemConstuctor = MVCListAdapter_ListItem.declaredConstructors[0]
          if (isUserScript(url)) {
            menusToAdd.add(
                itemConstuctor.newInstance(installScriptItem, AppMenuItemType.STANDARD.value))
          }
          menusToAdd.add(
              itemConstuctor.newInstance(erudaItem, AppMenuItemType.STANDARD.value))
          menusToAdd.add(
            itemConstuctor.newInstance(devtoolsItem, AppMenuItemType.STANDARD.value)
          )
          menusToAdd.add(createUserScriptMenus(itemConstuctor, mAppMenuItemTheme, currentTab))

          val injectPosition =
              menuModels
                  .filter { mType.get(it) == AppMenuItemType.DIVIDER.value }[2]
                  .let { menuModels.indexOf(it) }
          menuModels.addAll(injectPosition + 1, menusToAdd)
        }
  }

  // empty menu is intended if no command register
  private fun createUserScriptMenus(
    `constructor_MVCListAdapter$ListItem`: Constructor<*>,
    theme: Any?, currentTab: Any?
  ): Any {
    val scriptNameItems = mutableListOf<Any>()
    GlobalScope.launch(Dispatchers.Main) {
      val commandsDeferred = UserScriptMenuCommand.getDeferredMenuCommandsForTab(currentTab)
      Chrome.evaluateJavascript(listOf(Local.updateMenuCommands), currentTab)
      val commands = withTimeoutOrNull(500.milliseconds) { commandsDeferred.await() }
      if (commands == null) {
        Log.w("createUserScriptMenus wait timeout for ${Chrome.getTabId(currentTab)}")
        return@launch
      }
      val usedTemplateIds = mutableSetOf<Int>()
      commands
        .groupBy { item -> item.scriptId }
        .forEach { (id, items) ->
          val scriptCommandItems = mutableListOf<Any>()
          items.forEach { item ->
            val titleId =
              ChromeXtModuleContext.addStringRes(item.title).also { usedTemplateIds.add(it) }
            val model = PageMenuProxy.method_AppMenuItemUtils_buildModelForStandardMenuItem.invoke(
              null,
              ChromeXtModuleContext.instance(),
              theme,
              item.id,
              titleId,
              Resources.ID_NULL,
              false
            )
            val listItem = PageMenuProxy.method_AppMenuItemUtils_createStandardListItem.invoke(
              null,
              model,
              /* showIcon = */ false
            )!!
            scriptCommandItems.add(listItem)
          }
          val name = ScriptDbManager.loadedScripts.find { it.id == id }?.metadata?.localeName
            ?: id.substringAfterLast(":")
          val titleId = ChromeXtModuleContext.addStringRes(name).also { usedTemplateIds.add(it) }
          val model = PageMenuProxy.method_AppMenuItemUtils_buildModelForMenuItemWithSubmenu.invoke(
            null,
            ChromeXtModuleContext.instance(),
            theme,
            Resources.ID_NULL,
            titleId,
            Resources.ID_NULL,
            object : Supplier<List<Any>> {
              override fun get(): List<Any> {
                return scriptCommandItems
              }
            },
            false
          )
          val listItem =
            PageMenuProxy.method_AppMenuItemUtils_createMenuItemWithSubmenuListItem.invoke(
              null,
              model,
              /* showIcon = */ false
            )!!
          scriptNameItems.add(listItem)
        }
      usedTemplateIds.forEach { id ->
        ChromeXtModuleContext.removeStringRes(id)
      }
    }
    val model = PageMenuProxy.method_AppMenuItemUtils_buildModelForMenuItemWithSubmenu.invoke(
      null,
      Chrome.getContext(),
      theme,
      Resources.ID_NULL,
      R.string.main_menu_userscript,
      R.drawable.ic_extension,
      object : Supplier<List<Any>> {
        override fun get(): List<Any> {
          return scriptNameItems
        }
      },
      false
    )
    return `constructor_MVCListAdapter$ListItem`.newInstance(
      model,
      AppMenuHandler.AppMenuItemType.MENU_ITEM_WITH_SUBMENU
    )
  }
}
