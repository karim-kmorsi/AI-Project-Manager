package com.karim.aiprojectmanager

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Host-only checks: inspect configuration without reading app data or invoking a transport. */
class BackupPolicyTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"
    private val domains = setOf("root", "file", "database", "sharedpref", "external",
        "device_root", "device_file", "device_database", "device_sharedpref")

    private fun parse(path: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(File("src/main/$path")).documentElement
    }

    private fun assertAllDataExcluded(section: Element) {
        assertEquals("Allow-list rules must never reopen an excluded transport", 0, section.getElementsByTagName("include").length)
        val excluded = section.getElementsByTagName("exclude")
        assertEquals(domains.size, excluded.length)
        val found = (0 until excluded.length).map { index ->
            val node = excluded.item(index) as Element
            assertEquals("Exclude entire domain, including drafts and atomic sidecars", ".", node.getAttribute("path"))
            node.getAttribute("domain")
        }.toSet()
        assertEquals(domains, found)
    }

    @Test fun manifestDisablesBackupAndReferencesBothPlatformPolicies() {
        val application = parse("AndroidManifest.xml").getElementsByTagName("application").item(0) as Element
        assertEquals("false", application.getAttributeNS(androidNamespace, "allowBackup"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(androidNamespace, "fullBackupContent"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(androidNamespace, "dataExtractionRules"))
    }

    @Test fun legacyPolicyExcludesCredentialAndDeviceProtectedData() {
        val root = parse("res/xml/backup_rules.xml")
        assertEquals("full-backup-content", root.tagName)
        assertAllDataExcluded(root)
    }

    @Test fun modernPolicyExcludesCloudAndDeviceTransferIndependently() {
        val root = parse("res/xml/data_extraction_rules.xml")
        assertEquals("data-extraction-rules", root.tagName)
        for (transport in listOf("cloud-backup", "device-transfer")) {
            val sections = root.getElementsByTagName(transport)
            assertEquals(1, sections.length)
            assertAllDataExcluded(sections.item(0) as Element)
        }
    }
}
