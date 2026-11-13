package app.anynear.app

import app.anynear.anmp.core.DocVectors
import app.anynear.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * The small meaning model of the storage (ADR 0078 §5), loaded once from the app's resources (around 15 MB, stays
 * in memory as long as the app is running). Is built reproducibly with `ANMP/tools/doc-vectors/make.py`.
 */
class DocMeaning(private val load: suspend () -> ByteArray = ::resource) {
    private val lock = Mutex()
    private var loaded: DocVectors? = null
    private var failed = false

    /** The model, `null` if it is missing or cannot be read (then there is only word search and synonyms). */
    suspend fun get(): DocVectors? = loaded ?: lock.withLock {
        loaded ?: if (failed) null else runCatching { DocVectors(load()) }.onFailure { failed = true }.getOrNull().also { loaded = it }
    }

    private companion object {
        @OptIn(ExperimentalResourceApi::class)
        suspend fun resource(): ByteArray = Res.readBytes("files/doc-vectors.bin")
    }
}
