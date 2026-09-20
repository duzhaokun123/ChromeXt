package org.matrix.chromext.utils

import kotlinx.coroutines.CompletableDeferred
import org.matrix.chromext.Chrome
import java.util.concurrent.ConcurrentHashMap

object UserScriptMenuCommand {
  data class MenuCommandItem(
    val index: Int,
    val title: String,
    val scriptName: String,
    var enabled: Boolean = true,
    val id: Int = indexToId(index)
  )

  fun indexToId(index: Int): Int = 0x00FF0000 or index
  fun idToIndex(id: Int): Int {
    if (id and 0xFFFF0000.toInt() != 0x00FF0000) return -1
    return id and 0x0000FFFF
  }

  private val deferreds = ConcurrentHashMap<String, CompletableDeferred<List<MenuCommandItem>>>()

  fun getDeferredMenuCommandsForTab(tab: Any?): CompletableDeferred<List<MenuCommandItem>> {
    val tabId = Chrome.getTabId(tab)
    return deferreds.getOrPut(tabId, { CompletableDeferred()})
  }

  fun updateMenuCommandsForTab(tab: Any?, commands: List<MenuCommandItem>) {
    val tabId = Chrome.getTabId(tab)
    deferreds.remove(tabId)?.complete(commands)
      ?: Log.w("updateMenuCommandsForTab called for $tabId but no getDeferredMenuCommandsForTab called first")
  }
}