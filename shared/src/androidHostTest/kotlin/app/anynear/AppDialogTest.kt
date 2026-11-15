package app.anynear

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Dialogs close only through their buttons or the back gesture, never by a tap next to them (`ui/AppDialog.kt`). */
class AppDialogTest {
    private val sources = listOf("src/commonMain", "src/androidMain", "src/iosMain", "../androidApp/src/main").map(::File)
        .filter { it.isDirectory }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    @Test
    fun noAlertDialogOutsideTheBuildingBlock() {
        assertTrue(sources.isNotEmpty(), "Quellordner nicht gefunden: ${File("src/commonMain").absolutePath}")
        val direct = sources.filterNot { it.name == "AppDialog.kt" }
            .filter { Regex("""(?<![A-Za-z0-9_])AlertDialog\(""").containsMatchIn(it.readText()) }
            .map { it.path }
        assertTrue(direct.isEmpty(), "AlertDialog statt AppDialog: $direct")
    }

    @Test
    fun everyDialogIgnoresTapsOutside() {
        val loose = sources.filterNot { it.name == "AppDialog.kt" }
            .filter { f ->
                val text = f.readText()
                // Own DialogProperties would bypass the rule; Dialog(…) needs the properties of the building block.
                Regex("""(?<![A-Za-z0-9_])DialogProperties\(""").containsMatchIn(text) ||
                    Regex("""(?<![A-Za-z0-9_.])Dialog\([^)]*""").findAll(text).any { m ->
                        val call = text.substring(m.range.first, minOf(text.length, m.range.first + 300))
                        "AppFullScreenDialogProperties" !in call && "AppDialogProperties" !in call
                    }
            }
            .map { it.path }
        assertTrue(loose.isEmpty(), "Dialog ohne AppDialogProperties: $loose")
    }
}
