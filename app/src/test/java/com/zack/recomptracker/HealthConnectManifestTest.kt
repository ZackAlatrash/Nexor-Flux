package com.zack.recomptracker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectManifestTest {
    private val manifest = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(File("src/main/AndroidManifest.xml"))

    @Test
    fun `manifest declares every requested Health Connect read permission`() {
        val declaredPermissions = manifest.getElementsByTagName("uses-permission")
            .asSequence()
            .map { it.attributes.getNamedItem("android:name").nodeValue }
            .toSet()

        assertTrue("Missing Health Connect steps permission", "android.permission.health.READ_STEPS" in declaredPermissions)
        assertTrue("Missing Health Connect weight permission", "android.permission.health.READ_WEIGHT" in declaredPermissions)
        assertTrue("Missing Health Connect sleep permission", "android.permission.health.READ_SLEEP" in declaredPermissions)
        assertTrue("Missing Health Connect nutrition permission", "android.permission.health.READ_NUTRITION" in declaredPermissions)
        assertTrue(
            "Missing Health Connect history permission",
            "android.permission.health.READ_HEALTH_DATA_HISTORY" in declaredPermissions,
        )
    }

    @Test
    fun `manifest registers the legacy Health Connect rationale action`() {
        val declaredActions = manifest.getElementsByTagName("action")
            .asSequence()
            .map { it.attributes.getNamedItem("android:name").nodeValue }
            .toSet()

        assertTrue(
            "Missing legacy Health Connect rationale action",
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" in declaredActions,
        )
    }

    private fun org.w3c.dom.NodeList.asSequence(): Sequence<org.w3c.dom.Node> =
        (0 until length).asSequence().map(::item)
}
