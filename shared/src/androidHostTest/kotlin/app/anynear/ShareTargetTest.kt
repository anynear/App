package app.anynear

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

/**
 * Sharing into the app (Android): the shared item lands once, as a draft in the chosen conversation.
 *
 * Bug that this prevents: if one chose anynear in the share menu, Android started a second `MainActivity` in
 * its own task next to the one already running. Both show the same app controller. In the new one you store the
 * text as a draft and send it; the old one had already read the draft when fetching and kept showing it in the
 * input field after „Zurück“. A second send sent it once more.
 */
class ShareTargetTest {
    @Test
    fun shareGoesToTheRunningWindow() {
        // Runs in the folder of the module `shared`.
        val manifest = File("../androidApp/src/main/AndroidManifest.xml")
        assertTrue(manifest.isFile, "Manifest nicht gefunden: ${manifest.absolutePath}")
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(manifest)
        val activities = doc.getElementsByTagName("activity").let { list -> (0 until list.length).map { list.item(it) as Element } }
        val receivers = activities.filter { activity ->
            val actions = activity.getElementsByTagName("action")
            (0 until actions.length).any { (actions.item(it) as Element).getAttributeNS(ANDROID, "name") == "android.intent.action.SEND" }
        }
        assertEquals(listOf(".MainActivity"), receivers.map { it.getAttributeNS(ANDROID, "name") })
        // singleTask: Android passes the shared item to the running window (onNewIntent) instead of opening a second.
        assertEquals("singleTask", receivers.single().getAttributeNS(ANDROID, "launchMode"))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
