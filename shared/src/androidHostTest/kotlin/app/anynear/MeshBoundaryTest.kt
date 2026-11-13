package app.anynear

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Meshtastic (GPL) appears only in the adapter under `mesh/` (ADR 0057). */
class MeshBoundaryTest {
    @Test
    fun meshtasticOnlyInsideMesh() {
        // Runs in the folder of the module `shared`.
        val roots = listOf("src/commonMain", "src/androidMain", "src/iosMain", "../androidApp/src/main").map(::File)
        assertTrue(roots.first().isDirectory, "Quellordner nicht gefunden: ${roots.first().absolutePath}")
        val outside = roots.filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { "org.meshtastic" in it.readText() }
            .filterNot { it.parentFile.name == "mesh" }
            .map { it.path }
        assertTrue(outside.isEmpty(), "Meshtastic außerhalb von mesh/: $outside")
    }
}
