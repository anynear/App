package app.anynear.attachments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.FaceDetector
import app.anynear.AndroidPlatform
import app.anynear.anmp.core.PhotoTopics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * Android (ADR 0070): MobileNet v1 (ImageNet, quantized, Apache-2.0) with LiteRT, both without Google services; the
 * model is supplied by :androidApp as an asset under `vision/`. „Menschen“ via the system's face detection, which only
 * determines that a face is visible.
 */

private const val MODEL = "vision/mobilenet_v1_1.0_224_quant.tflite"
private const val INPUT = 224
private const val OUTPUTS = 1001

/** Longest edge for the face search; larger gains nothing and costs time. */
private const val FACE_EDGE = 800
private const val MAX_FACES = 5
private const val MIN_FACE_CONFIDENCE = 0.4f

private val lock = Mutex()
private var interpreter: Interpreter? = null

internal actual suspend fun recognizePhotoTopics(bytes: ByteArray): List<String>? = withContext(Dispatchers.Default) {
    runCatching {
        val bitmap = decode(bytes) ?: return@runCatching null
        val topics = lock.withLock { classify(bitmap) } ?: return@runCatching null
        val people = hasFace(bitmap)
        bitmap.recycle()
        (if (people) topics + PhotoTopics.PEOPLE else topics).distinct()
    }.getOrNull()
}

private fun decode(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= FACE_EDGE) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Topics from the model's confident hits; `null` if the model is missing. */
private fun classify(bitmap: Bitmap): List<String>? {
    val model = interpreter ?: open()?.also { interpreter = it } ?: return null
    val scaled = Bitmap.createScaledBitmap(bitmap, INPUT, INPUT, true)
    val input = ByteBuffer.allocateDirect(INPUT * INPUT * 3).order(ByteOrder.nativeOrder())
    val pixels = IntArray(INPUT * INPUT)
    scaled.getPixels(pixels, 0, INPUT, 0, 0, INPUT, INPUT)
    for (p in pixels) {
        input.put(((p shr 16) and 0xff).toByte())
        input.put(((p shr 8) and 0xff).toByte())
        input.put((p and 0xff).toByte())
    }
    if (scaled !== bitmap) scaled.recycle()
    input.rewind()
    val output = Array(1) { ByteArray(OUTPUTS) }
    model.run(input, output)
    return output[0].withIndex()
        .filter { (it.value.toInt() and 0xff) / 255f >= PhotoTopics.MIN_SCORE }
        .flatMap { PhotoTopics.fromImageNet(it.index).toList() }
}

private fun open(): Interpreter? {
    val bytes = runCatching { AndroidPlatform.requireContext().assets.open(MODEL).use { it.readBytes() } }.getOrNull() ?: return null
    val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
    return Interpreter(buffer, Interpreter.Options().setNumThreads(2))
}

/** Whether at least one face is visible. Nothing is stored or compared. */
private fun hasFace(bitmap: Bitmap): Boolean {
    // FaceDetector needs RGB_565 and an even width.
    val width = bitmap.width and 1.inv()
    if (width < 2 || bitmap.height < 2) return false
    val rgb = Bitmap.createBitmap(width, bitmap.height, Bitmap.Config.RGB_565)
    android.graphics.Canvas(rgb).drawBitmap(bitmap, 0f, 0f, null)
    val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
    val found = FaceDetector(width, bitmap.height, MAX_FACES).findFaces(rgb, faces)
    rgb.recycle()
    return faces.take(found).any { it != null && it.confidence() >= MIN_FACE_CONFIDENCE }
}
