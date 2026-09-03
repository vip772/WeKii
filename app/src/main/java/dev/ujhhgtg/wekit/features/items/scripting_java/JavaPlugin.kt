package dev.ujhhgtg.wekit.features.items.scripting_java

import bsh.Interpreter
import java.nio.file.Path
import java.util.Properties

data class JavaPluginInfo(
    val name: String,
    val author: String? = null,
    val version: String? = null,
    val updateTime: String? = null
)

data class JavaPlugin(
    val name: String,
    val dir: Path,
    val info: JavaPluginInfo,
    val content: String,
    val interpreter: Interpreter
) {
    companion object {
        fun parseInfoProp(content: String): JavaPluginInfo {
            val props = Properties()
            runCatching { content.reader().use { props.load(it) } }.getOrElse {
                content.lineSequence().forEach { line ->
                    val trimmed = line.trim()
                    val eq = trimmed.indexOf(=)
                    if (eq > 0 && !trimmed.startsWith("#")) {
                        props[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
                    }
                }
            }
            fun value(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { key -> props.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } }
            return JavaPluginInfo(
                name = value("name", "pluginName", "title") ?: "unnamed",
                author = value("author", "作者"),
                version = value("version", "版本"),
                updateTime = value("updateTime", "update_time", "更新时间")
            )
        }
    }
}
