package dev.ujhhgtg.wekit.features.items.scripting_java

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class JavaEngineApiSurfaceTest {
    private val originalEntrypoints = setOf(
        "sendText",
        "uploadText",
        "uploadTextAndPicList",
        "download",
        "downloadImage",
        "downloadImages",
        "startTransform",
        "unhookEverything",
        "uploadDeviceStep",
    )

    private val originalSignatureFragments = setOf(
        "BshMethod(\"uploadText\", arrayOf(BString))",
        "BshMethod(\"uploadText\", arrayOf(org.json.JSONObject::class.java))",
        "BshMethod(\"uploadTextAndPicList\", arrayOf(BString, List::class.java))",
        "BshMethod(\"downloadImage\", arrayOf(BString, Consumer::class.java))",
        "BshMethod(\"downloadImage\", arrayOf(BString, BString, Consumer::class.java))",
        "BshMethod(\"downloadImages\", arrayOf(List::class.java, Consumer::class.java))",
        "BshMethod(\"downloadImages\", arrayOf(List::class.java, BString, Consumer::class.java))",
        "BshMethod(\"download\", arrayOf(BString, BString, Map::class.java, java.lang.Long.TYPE, any))",
        "BshMethod(\"download\", arrayOf(BString, BString, Map::class.java, Consumer::class.java))",
        "BshMethod(\"downloadVideo\", arrayOf(BString, Consumer::class.java))",
        "BshMethod(\"downloadVideo\", arrayOf(BString, BString, Consumer::class.java))",
        "BshMethod(\"startTransform\", arrayOf(int, BString, BString, int, Consumer::class.java))",
        "BshMethod(\"uploadDeviceStep\", arrayOf(java.lang.Long.TYPE))",
    )

    private val originalHookSignatures = setOf(
        """BshMethod\(\s*"hookBefore"\s*,\s*arrayOf\(Member::class.java,\s*Consumer::class.java\)""",
        """BshMethod\(\s*"hookAfter"\s*,\s*arrayOf\(Member::class.java,\s*Consumer::class.java\)""", 
    )

    private val additiveSignatureFragments = setOf(
        "BshMethod(\"uploadVideo\", arrayOf(BString))",
        "BshMethod(\"uploadVideo\", arrayOf(org.json.JSONObject::class.java))",
        "BshMethod(\"uploadTextAndVideo\", arrayOf(BString, BString))",
        "BshMethod(\"uploadTextAndVideo\", arrayOf(BString, BString, BString, BString))",
        "BshMethod(\"uploadTextAndVideo\", arrayOf(org.json.JSONObject::class.java))",
        "BshMethod(\"registerPlusMenu\", arrayOf(BString, Consumer::class.java))",
        "BshMethod(\"registerPlusMenu\", arrayOf(BString, BString, Consumer::class.java))",
        "BshMethod(\"registerPlusMenu\", arrayOf(BString, BString, java.lang.Boolean.TYPE, Consumer::class.java))",
        "BshMethod(\"registerPlusMenu\", arrayOf(BString, java.lang.Boolean.TYPE, Consumer::class.java))",
        "BshMethod(\"sendProtobufPacket\", arrayOf(BString, int, BString))",
        "BshMethod(\"sendProtobufPacket\", arrayOf(BString, int, BString, Consumer::class.java))",
        "BshMethod(\"sendProtobufPacket\", arrayOf(BString, int, org.json.JSONObject::class.java))",
        "BshMethod(\"sendProtobufPacket\", arrayOf(BString, int, org.json.JSONObject::class.java, Consumer::class.java))",
        "BshMethod(\"sendProtobufPacket\", arrayOf(BString, int, int, int, BString))",
        "BshMethod(\"sendProtobufPacket\", arrayOf(BString, int, int, int, BString, Consumer::class.java))",
        "BshMethod(\"sendProtobufPacket\", arrayOf(BString, int, int, int, org.json.JSONObject::class.java))",
        "BshMethod(\"sendProtobufPacket\", arrayOf(BString, int, int, int, org.json.JSONObject::class.java, Consumer::class.java))",
    )
    @Test
    fun originalAndAdditiveScriptEntrypointsRemainRegistered() {
        val source = readJavaEngineSource()
        val registrations = Regex("setMethod\\(BshMethod\\(\\\"([^\\\"]+)\\\"").findAll(source)
            .map { it.groupValues[1] }
            .toList()
        val names = registrations.toSet()

        // Baseline is the 95 registrations present at HEAD before the current additive adapters.
        assertTrue(registrations.size >= 95, "Original script registration surface shrank")
        assertTrue(
            names.containsAll(originalEntrypoints),
            "An existing WeKii script entrypoint is missing",
        )
        originalSignatureFragments.forEach { signature ->
            assertTrue(source.contains(signature), "Existing script signature is missing: $signature")
        }
        originalHookSignatures.forEach { signature ->
            assertTrue(Regex(signature).containsMatchIn(source), "Existing hook signature is missing: $signature")
        }
        additiveSignatureFragments.forEach { signature ->
            assertTrue(source.contains(signature), "Additive script signature is missing: $signature")
        }
    }

    @Test
    fun unavailableHostCapabilitiesRemainExplicit() {
        val source = readJavaEngineSource()

        assertTrue(
            source.contains("WeKii 当前没有 Protobuf transport runtime"),
            "Protobuf compatibility entrypoints must report the missing transport runtime",
        )
        assertTrue(
            source.contains("registerPlusMenu degraded to message menu"),
            "Plus-menu compatibility must remain an explicit message-menu fallback",
        )
        assertTrue(
            !source.contains("BshMethod(\"uploadLivePhoto\"") &&
                !source.contains("BshMethod(\"uploadTextAndLivePhoto\""),
            "Live-photo entrypoints must not be exposed until the host media pipeline exists",
        )
    }


    private fun readJavaEngineSource(): String {
        val relative = Paths.get(
            "app",
            "src",
            "main",
            "java",
            "dev",
            "ujhhgtg",
            "wekit",
            "features",
            "items",
            "scripting_java",
            "JavaEngine.kt",
        )
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val candidates = generateSequence(start) { it.parent }
            .map { it.resolve(relative) }
            .toList()
        return candidates.firstOrNull { Files.isRegularFile(it) }?.let { Files.readString(it) }
            ?: error("Unable to locate JavaEngine.kt for API surface regression test from $start")
    }
}
