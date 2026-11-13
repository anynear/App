package app.anynear.attachments

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGSizeMake
import platform.ImageIO.CGImagePropertyOrientation
import platform.ImageIO.kCGImagePropertyOrientationDown
import platform.ImageIO.kCGImagePropertyOrientationLeft
import platform.ImageIO.kCGImagePropertyOrientationRight
import platform.ImageIO.kCGImagePropertyOrientationUp
import platform.PDFKit.PDFDocument
import platform.PDFKit.kPDFDisplayBoxMediaBox
import platform.UIKit.UIImagePNGRepresentation
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate
import platform.Vision.VNRequestTextRecognitionLevelFast

/*
 * iOS (ADR 0067): photos and pages without a text layer via Vision, PDFs via the text layer of PDFKit. Both run on the
 * device, without network.
 */

/** Vision already reads at the accurate level; version 1 stays (ADR 0078). */
internal actual val readerVersion: Int = 1

internal actual suspend fun readPlatformText(bytes: ByteArray, kind: AttachmentKind): String? = withContext(Dispatchers.Default) {
    runCatching {
        when (kind) {
            AttachmentKind.IMAGE -> recognize(bytes)
            AttachmentKind.PDF -> pdfText(bytes)
            else -> null
        }
    }.getOrNull()
}

/** At least this many reliably read characters, otherwise the photo stays as it is (little text). */
private const val UPRIGHT_MIN_CHARS = 20.0

/** The best direction must read this much more than the second best. */
private const val UPRIGHT_MARGIN = 1.5

internal actual suspend fun uprightTurn(image: ByteArray): Int = withContext(Dispatchers.Default) {
    runCatching {
        val data = image.toNSData()
        // Vision reads only upright text well: read in all four directions, the one with the most reliable text wins.
        // „Right“ means the image is to be rotated 90° clockwise for viewing.
        val scores = listOf(
            0 to kCGImagePropertyOrientationUp,
            90 to kCGImagePropertyOrientationRight,
            180 to kCGImagePropertyOrientationDown,
            270 to kCGImagePropertyOrientationLeft,
        ).map { (turn, orientation) -> turn to readScore(data, orientation) }.sortedByDescending { it.second }
        val (turn, best) = scores[0]
        if (best < UPRIGHT_MIN_CHARS || best < UPRIGHT_MARGIN * scores[1].second) 0 else turn
    }.getOrDefault(0)
}

/** Reliably read characters, weighted by confidence, when Vision reads the image in [orientation]. */
@OptIn(ExperimentalForeignApi::class)
private fun readScore(data: platform.Foundation.NSData, orientation: CGImagePropertyOrientation): Double {
    val request = VNRecognizeTextRequest()
    request.recognitionLevel = VNRequestTextRecognitionLevelFast
    request.usesLanguageCorrection = false
    val handler = VNImageRequestHandler(data = data, orientation = orientation, options = emptyMap<Any?, Any?>())
    if (!handler.performRequests(listOf(request), error = null)) return 0.0
    return request.results.orEmpty().sumOf { o ->
        val text = (o as? VNRecognizedTextObservation)?.topCandidates(1u)?.firstOrNull() as? VNRecognizedText ?: return@sumOf 0.0
        if (text.confidence < 0.5f) 0.0 else text.confidence * text.string.count { it.isLetterOrDigit() }.toDouble()
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun recognize(image: ByteArray): String? {
    val request = VNRecognizeTextRequest()
    request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
    request.recognitionLanguages = listOf("de-DE", "en-US")
    request.usesLanguageCorrection = true
    val handler = VNImageRequestHandler(data = image.toNSData(), options = emptyMap<Any?, Any?>())
    if (!handler.performRequests(listOf(request), error = null)) return null
    return request.results.orEmpty().mapNotNull { o ->
        ((o as? VNRecognizedTextObservation)?.topCandidates(1u)?.firstOrNull() as? VNRecognizedText)?.string
    }.joinToString("\n")
}

@OptIn(ExperimentalForeignApi::class)
private fun pdfText(bytes: ByteArray): String? {
    val doc = PDFDocument(data = bytes.toNSData())
    val layer = doc.string.orEmpty()
    if (layer.count { it.isLetter() } >= 40) return layer
    // Scanned PDFs have no text layer: read the first pages as an image.
    return buildString {
        for (i in 0 until minOf(doc.pageCount.toInt(), OCR_MAX_PAGES)) {
            val page = doc.pageAtIndex(i.toULong()) ?: continue
            val picture = page.thumbnailOfSize(CGSizeMake(1700.0, 2400.0), forBox = kPDFDisplayBoxMediaBox)
            val png = UIImagePNGRepresentation(picture) ?: continue
            recognize(png.toByteArray())?.let { append(it).append('\n') }
        }
    }
}
