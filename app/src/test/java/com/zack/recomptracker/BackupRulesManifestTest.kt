package com.zack.recomptracker

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Node
import org.w3c.dom.NodeList

/**
 * Guards the Auto Backup exclusion that keeps the EncryptedSharedPreferences key store off cloud
 * backup / device transfer (review P1-4). Without it, the encrypted keyset is backed up but its
 * Keystore master key is not, so a restore to a new device crash-loops Application.onCreate.
 */
class BackupRulesManifestTest {

    private fun parse(path: String) = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(File(path))

    private fun NodeList.asSequence(): Sequence<Node> = (0 until length).asSequence().map(::item)

    @Test
    fun `manifest wires both backup-rule files`() {
        val app = parse("src/main/AndroidManifest.xml")
            .getElementsByTagName("application").item(0)
        assertEquals(
            "API 31+ backup/transfer rules must be declared",
            "@xml/data_extraction_rules",
            app.attributes.getNamedItem("android:dataExtractionRules")?.nodeValue,
        )
        assertEquals(
            "legacy (API <= 30) backup rules must be declared",
            "@xml/backup_rules",
            app.attributes.getNamedItem("android:fullBackupContent")?.nodeValue,
        )
    }

    // Requires the exact on-disk filename WITH the .xml extension: Android matches the sharedpref
    // `path` against shared_prefs/<name>.xml, so `secure_ai_prefs` (no extension) would be a silent
    // no-op that leaves the keyset backed up. This assertion pins that make-or-break detail.
    private fun excludesSecureStore(nodes: Sequence<Node>): Boolean = nodes.any { node ->
        node.nodeName == "exclude" &&
            node.attributes?.getNamedItem("domain")?.nodeValue == "sharedpref" &&
            node.attributes?.getNamedItem("path")?.nodeValue == "secure_ai_prefs.xml"
    }

    @Test
    fun `data extraction rules exclude the key store from cloud backup and device transfer`() {
        val doc = parse("src/main/res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val excludes = doc.getElementsByTagName(section).item(0).childNodes.asSequence()
            assertTrue(
                "secure_ai_prefs must be excluded from <$section>",
                excludesSecureStore(excludes),
            )
        }
    }

    @Test
    fun `legacy full-backup rules exclude the key store`() {
        val doc = parse("src/main/res/xml/backup_rules.xml")
        assertTrue(
            "secure_ai_prefs must be excluded from legacy full-backup content",
            excludesSecureStore(doc.getElementsByTagName("full-backup-content").item(0).childNodes.asSequence()),
        )
    }
}
