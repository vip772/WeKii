package dev.ujhhgtg.wekit.features.items.scripting_java

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class JavaPluginInfoTest {
    @Test
    fun parsesStandardPropertiesAndAliases() {
        val info = JavaPlugin.parseInfoProp("""
            # comment
            name=Demo Plugin
            author=WeKii
            version=1.2.3
            update_time=2026-09-03
        """.trimIndent())
        assertEquals("Demo Plugin", info.name)
        assertEquals("WeKii", info.author)
        assertEquals("1.2.3", info.version)
        assertEquals("2026-09-03", info.updateTime)
    }

    @Test
    fun missingNameFallsBackToUnnamed() {
        assertEquals("unnamed", JavaPlugin.parseInfoProp("author=WeKii").name)
    }
}
