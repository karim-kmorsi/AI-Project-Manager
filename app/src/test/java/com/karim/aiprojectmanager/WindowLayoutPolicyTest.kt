package com.karim.aiprojectmanager

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Guards the native policy that component inset simulations cannot exercise. */
class WindowLayoutPolicyTest {
    @Test fun mainActivityExplicitlyResizesForImeRatherThanPanningTheToolbar() {
        val namespace = "http://schemas.android.com/apk/res/android"
        val root = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(File("src/main/AndroidManifest.xml")).documentElement
        val activities = root.getElementsByTagName("activity")
        val main = (0 until activities.length).map { activities.item(it) as Element }
            .single { it.getAttributeNS(namespace, "name") == ".MainActivity" }
        assertEquals("adjustResize", main.getAttributeNS(namespace, "windowSoftInputMode"))
        // Theme changes continue to use the existing configuration handling, not recreation.
        assertEquals("uiMode", main.getAttributeNS(namespace, "configChanges"))
    }
}
