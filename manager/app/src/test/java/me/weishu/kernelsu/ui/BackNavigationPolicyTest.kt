package me.weishu.kernelsu.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.w3c.dom.Element

class BackNavigationPolicyTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    @Test
    fun applicationOptsOutOfInteractiveSystemBack() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val manifest = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertEquals("false", application.getAttributeNS(androidNamespace, "enableOnBackInvokedCallback"))
    }

    @Test
    fun noActivityOverridesTheApplicationBackPolicy() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val manifest = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val activities = manifest.getElementsByTagName("activity")

        for (index in 0 until activities.length) {
            val activity = activities.item(index) as Element
            assertFalse(
                activity.getAttributeNS(androidNamespace, "name"),
                activity.getAttributeNS(androidNamespace, "enableOnBackInvokedCallback") == "true",
            )
        }
    }

    @Test
    fun removedBackPreferenceAndGestureHooksCannotBeReactivated() {
        val removedSymbols = listOf(
            "enablePredictiveBack",
            "enable_predictive_back",
            "predictivePopTransitionSpec",
            "setEnableOnBackInvokedCallback",
            "shouldAllowSwipeEdgePopOverride",
        )
        val sources = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertFalse("Manager source files must be available", sources.isEmpty())

        for (source in sources) {
            val text = source.readText()
            for (symbol in removedSymbols) {
                assertFalse("${source.path} still contains $symbol", text.contains(symbol))
            }
        }
    }
}
