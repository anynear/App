package app.anynear.images

import app.anynear.core.b64
import app.anynear.core.sha256

/**
 * Reference to an uploaded image of a circle or a person (ADR 0012), as in the ANMP core: blob [ImageRef.id]
 * (hash of the ciphertext), key [ImageRef.key], space it is in, and hash of the image ([ImageRef.hash],
 * [imageHash]) by which everyone recognises the same image (ADR 0014).
 */
typealias ImageRef = app.anynear.anmp.core.ImageRef

/** First 16 bytes of SHA-256 over the image, Base64url. */
suspend fun imageHash(bytes: ByteArray): String = b64.encode(sha256(bytes).copyOf(16))

/** Reference to the own image only on this phone (display); the core uploads it per space. */
fun localImageRef(hash: String): ImageRef = ImageRef(id = "own_$hash", key = "", space = "", hash = hash)

object ImageLimits {
    /** Longest side of a circle or person image after downscaling. */
    const val MAX_EDGE = 512

    const val QUALITY = 85
}
