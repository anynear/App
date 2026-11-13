package app.anynear.attachments

import app.anynear.anmp.core.PhotoTopics
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Vision.VNClassificationObservation
import platform.Vision.VNClassifyImageRequest
import platform.Vision.VNDetectFaceRectanglesRequest
import platform.Vision.VNImageRequestHandler

/*
 * iOS (ADR 0070): Vision classifies the photo, faces only as „present“. Both run on the device.
 */

internal actual suspend fun recognizePhotoTopics(bytes: ByteArray): List<String>? = withContext(Dispatchers.Default) {
    runCatching { look(bytes) }.getOrNull()
}

@OptIn(ExperimentalForeignApi::class)
private fun look(bytes: ByteArray): List<String>? {
    val classify = VNClassifyImageRequest()
    val faces = VNDetectFaceRectanglesRequest()
    val handler = VNImageRequestHandler(data = bytes.toNSData(), options = emptyMap<Any?, Any?>())
    if (!handler.performRequests(listOf(classify, faces), error = null)) return null
    val topics = classify.results.orEmpty()
        .mapNotNull { it as? VNClassificationObservation }
        .filter { it.confidence >= PhotoTopics.MIN_SCORE }
        .flatMap { PhotoTopics.fromVision(it.identifier).toList() }
    val people = faces.results.orEmpty().isNotEmpty()
    return (if (people) topics + PhotoTopics.PEOPLE else topics).distinct()
}
