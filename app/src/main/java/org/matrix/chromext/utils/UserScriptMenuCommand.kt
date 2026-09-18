package org.matrix.chromext.utils

import android.view.View

object UserScriptMenuCommand {
  data class MenuCommandItem(
    val index: Int,
    val title: String,
    val scriptName: String,
    var enabled: Boolean = true,
    val id: Int = View.generateViewId()
  )

  private val map = mutableMapOf<String, MutableMap<Int, MenuCommandItem>>()

  fun registerMenuCommand(chromeXtId: String, item: MenuCommandItem) {
    map.getOrPut(chromeXtId, { mutableMapOf() })
      .getOrPut(item.index, { item })
      .enabled = true
  }

  fun unregisterMenuCommand(chromeXtId: String, index: Int) {
    map.getOrPut(chromeXtId, { mutableMapOf() })[index]?.enabled = false
  }

  fun getRegisterMenuCommands(chromeXtId: String?): List<MenuCommandItem> {
    return map[chromeXtId]?.values?.filter { item -> item.enabled } ?: emptyList()
  }

  fun getRegisterMenuCommand(chromeXtId: String?, id: Int): MenuCommandItem? {
    return map[chromeXtId]?.values?.find { item -> item.enabled && item.id == id }
  }
}