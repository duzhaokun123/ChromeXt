package org.matrix.chromext.utils

import org.matrix.chromext.db.entity.Script
import java.util.Locale

data class ScriptMetadata(
  val match: List<String>,
  val include: List<String>,
  val exclude: List<String>,
  val grant: List<String>,
  val require: List<String>,
  val connect: List<String>,
  val raw: List<Pair<String, String?>>
) {
  val id get() = "$namespace:$name"
  val name get() = getMetadata("name")
  val namespace get() = getMetadata("namespace")
  val description get() = getMetadata("description")
  val author get() = getMetadata("author")
  val version get() = getMetadata("version")
  val noframes get() = hasMetadata("noframes")
  val `run-at` get() = Script.RunAt.fromValue(getMetadata("run-at") ?: "") ?: Script.RunAt.UNSET

  val localeName get() = getLocaleMetadata("name")
  val localeDescription get() = getLocaleMetadata("description")

  fun getMetadata(key: String): String? {
    return raw.find { it.first == key }?.second
  }

  fun getLocaleMetadata(key: String, locale: Locale? = null): String? {
    val locale = locale ?: Locale.getDefault()
    return getMetadata("$key:${locale.language}-${locale.country}")
      ?: getMetadata("$key:${locale.language}")
      ?: getMetadata(key)
  }

  fun hasMetadata(key: String): Boolean {
    return raw.find { it.first == key } != null
  }
}

object ScriptParser {
  fun parseMetadata(metadata: String): ScriptMetadata {
    val match = mutableListOf<String>()
    val include = mutableListOf<String>()
    val exclude = mutableListOf<String>()
    val grant = mutableListOf<String>()
    val require = mutableListOf<String>()
    val connect = mutableListOf<String>()

    val raw = metadata.split("\n")
      .mapNotNull { line ->
        val metaMatchGroup =
          Regex("""^//\s+@(?<key>[\w-:]+)(\s+(?<value>.+))?""").matchEntire(line)?.groups
            ?: return@mapNotNull null
        val key = metaMatchGroup[1]?.value ?: return@mapNotNull null
        val value = if (metaMatchGroup[2] != null) metaMatchGroup[3]?.value else null
        if (value != null) {
          when (key) {
            "match" -> match.add(value)
            "include" -> include.add(value)
            "exclude" -> exclude.add(value)
            "grant" -> grant.add(value)
            "require" -> require.add(value)
            "connect" -> connect.add(value)
          }
        }
        return@mapNotNull key to value
      }
    return ScriptMetadata(
      match = match,
      include = include,
      exclude = exclude,
      grant = grant,
      require = require,
      connect = connect,
      raw = raw
    )
  }

  /**
   * @return meta, code
   */
  fun splitScript(input: String): Pair<String?, String?>? {
    val blockMatchGroup =
      Regex("""(?<metablock>[\S\s]*?// ==UserScript==\r?\n[\S\s]*?\r?\n// ==/UserScript==\s+)(?<code>[\S\s]*)""")
        .matchEntire(input)?.groups
    if (blockMatchGroup == null) return null
    return blockMatchGroup[1]?.value to blockMatchGroup[2]?.value
  }

  fun parseScriptMetadata(input: String): ScriptMetadata? {
    val (metadata, _) = splitScript(input) ?: return null
    return parseMetadata(metadata ?: return null)
  }
}