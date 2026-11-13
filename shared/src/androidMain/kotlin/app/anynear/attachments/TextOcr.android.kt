package app.anynear.attachments

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import app.anynear.AndroidPlatform
import org.json.JSONArray
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Text recognition on Android (ADR 0078 §2): PP-OCRv5 mobile from PaddleOCR (Apache-2.0), converted to LiteRT
 * (`tools/ocr-models/convert.sh`), without Google services. Two steps: one model finds the text lines in the image, a
 * second reads each line. In between this code finds the lines as connected regions, puts a rotated
 * box around them (principal axes) and cuts them out straight; so it also reads documents photographed at an angle.
 */

/** Side length to which an image is brought for the line search (as fixed during conversion). */
private const val DET_SIZE = 960

/** Height and maximum width of a line for reading. */
private const val REC_HEIGHT = 48
private const val REC_WIDTH = 960

/** Thresholds as in PaddleOCR: pixel belongs to text, region is certainly text, margin around the region. */
private const val PIXEL_MIN = 0.3f
private const val BOX_MIN = 0.6f
private const val UNCLIP = 1.5f

/** A read line only counts from this confidence on (filters crumbs from patterns). */
private const val LINE_MIN = 0.5f

/** Nobody reads more lines in one file; protects against images full of patterns. */
private const val MAX_LINES = 300

/** For the orientation the longest lines are enough; each is read in both directions. */
private const val ORIENT_LINES = 12

/** This many lines must be unambiguous, otherwise the image stays as it is (photos with little text). */
private const val ORIENT_MIN_VOTES = 3

/** A line is unambiguous if one direction is read this much more confidently. */
private const val ORIENT_LINE_MARGIN = 0.15f

/** Share of the unambiguous lines that must vote for the same direction. */
private const val ORIENT_AGREE = 0.7f

internal class TextOcr private constructor(private val det: Interpreter, private val rec: Interpreter, private val chars: List<String>) {
    private val detIn = ByteBuffer.allocateDirect(DET_SIZE * DET_SIZE * 3 * 4).order(ByteOrder.nativeOrder())
    private val detOut = ByteBuffer.allocateDirect(DET_SIZE * DET_SIZE * 4).order(ByteOrder.nativeOrder())
    private val recIn = ByteBuffer.allocateDirect(REC_HEIGHT * REC_WIDTH * 3 * 4).order(ByteOrder.nativeOrder())
    private val recSteps = REC_WIDTH / 8
    private val recOut = ByteBuffer.allocateDirect(recSteps * chars.size * 4).order(ByteOrder.nativeOrder())

    /** A found line in the original image: centre, length, height, direction (radians). */
    private class Line(val cx: Float, val cy: Float, val w: Float, val h: Float, val angle: Float)

    /** Text of the image, line by line in reading direction; empty if there is nothing to read. */
    fun read(bitmap: Bitmap): String {
        val lines = find(bitmap)
        val read = lines.mapNotNull { line -> readLine(bitmap, line)?.let { line to it } }
        if (read.isEmpty()) return ""
        // Reading direction from the mean direction of the lines: so the order is right for rotated images too.
        val main = read.map { it.first.angle }.sorted()[read.size / 2]
        val c = cos(-main)
        val s = sin(-main)
        val placed = read.map { (l, t) -> Triple(l.cx * s + l.cy * c, l.cx * c - l.cy * s, l.h) to t }
            .sortedBy { it.first.first }
        val rows = mutableListOf<MutableList<Pair<Triple<Float, Float, Float>, String>>>()
        for (p in placed) {
            val row = rows.lastOrNull()
            if (row != null && abs(p.first.first - row.first().first.first) < 0.5f * p.first.third) row += p else rows += mutableListOf(p)
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.first.second }.joinToString(" ") { it.second } }
    }

    /**
     * How far the image is to be rotated clockwise (0, 90, 180, 270) so that the text is upright. Each of the
     * longest lines is read forwards and backwards, the more confident direction votes. 0 if there is too little text
     * or the lines do not agree: then rather rotate nothing.
     */
    fun uprightTurn(bitmap: Bitmap): Int {
        val lines = find(bitmap).filter { it.w >= 3 * it.h }.sortedByDescending { it.w }.take(ORIENT_LINES)
        val votes = IntArray(4)
        for (line in lines) {
            val forward = readAt(bitmap, line, line.angle)
            val backward = readAt(bitmap, line, line.angle + Math.PI.toFloat())
            val (winner, loser, angle) = if (forward.first >= backward.first) {
                Triple(forward, backward, line.angle)
            } else {
                Triple(backward, forward, line.angle + Math.PI.toFloat())
            }
            if (winner.first < LINE_MIN || winner.first - loser.first < ORIENT_LINE_MARGIN) continue
            if (winner.second.count { it.isLetterOrDigit() } < 2) continue
            votes[readingQuarter(angle)]++
        }
        val total = votes.sum()
        val best = votes.indices.maxBy { votes[it] }
        if (total < ORIENT_MIN_VOTES || votes[best] < ORIENT_AGREE * total) return 0
        return uprightFromReading(best)
    }

    private fun find(bitmap: Bitmap): List<Line> {
        val scale = minOf(1f, DET_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height))
        val sw = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val sh = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        if (small !== bitmap) small.recycle()
        detIn.rewind()
        for (y in 0 until DET_SIZE) for (x in 0 until DET_SIZE) {
            if (x < sw && y < sh) {
                val p = pixels[y * sw + x]
                detIn.putFloat((((p shr 16) and 0xff) / 255f - 0.485f) / 0.229f)
                detIn.putFloat((((p shr 8) and 0xff) / 255f - 0.456f) / 0.224f)
                detIn.putFloat(((p and 0xff) / 255f - 0.406f) / 0.225f)
            } else {
                detIn.putFloat(0f); detIn.putFloat(0f); detIn.putFloat(0f)
            }
        }
        detIn.rewind()
        detOut.rewind()
        det.run(detIn, detOut)
        detOut.rewind()
        val prob = FloatArray(DET_SIZE * DET_SIZE) { detOut.float }
        val seen = BooleanArray(prob.size)
        val stack = IntArray(prob.size)
        val lines = ArrayList<Line>()
        for (start in prob.indices) {
            if (seen[start] || prob[start] <= PIXEL_MIN) continue
            // Connected region (four-neighbourhood), with sums for centre and spread.
            var top = 0
            stack[top++] = start
            seen[start] = true
            var n = 0
            var sum = 0.0
            var sx = 0.0
            var sy = 0.0
            var sxx = 0.0
            var syy = 0.0
            var sxy = 0.0
            val members = ArrayList<Int>()
            while (top > 0) {
                val i = stack[--top]
                members += i
                val x = i % DET_SIZE
                val y = i / DET_SIZE
                n++
                sum += prob[i]
                sx += x; sy += y; sxx += x.toDouble() * x; syy += y.toDouble() * y; sxy += x.toDouble() * y
                if (x > 0) push(i - 1, prob, seen, stack, top)?.let { top = it }
                if (x < DET_SIZE - 1) push(i + 1, prob, seen, stack, top)?.let { top = it }
                if (y > 0) push(i - DET_SIZE, prob, seen, stack, top)?.let { top = it }
                if (y < DET_SIZE - 1) push(i + DET_SIZE, prob, seen, stack, top)?.let { top = it }
            }
            if (n < 10 || sum / n < BOX_MIN) continue
            val mx = sx / n
            val my = sy / n
            val cxx = sxx / n - mx * mx
            val cyy = syy / n - my * my
            val cxy = sxy / n - mx * my
            var angle = 0.5 * atan2(2 * cxy, cxx - cyy)
            var ux = cos(angle)
            var uy = sin(angle)
            var amin = Double.MAX_VALUE; var amax = -Double.MAX_VALUE; var bmin = Double.MAX_VALUE; var bmax = -Double.MAX_VALUE
            for (i in members) {
                val dx = i % DET_SIZE - mx
                val dy = i / DET_SIZE - my
                val a = dx * ux + dy * uy
                val b = -dx * uy + dy * ux
                if (a < amin) amin = a; if (a > amax) amax = a; if (b < bmin) bmin = b; if (b > bmax) bmax = b
            }
            var w = amax - amin + 1
            var h = bmax - bmin + 1
            val cx = mx + ux * (amax + amin) / 2 - uy * (bmax + bmin) / 2
            val cy = my + uy * (amax + amin) / 2 + ux * (bmax + bmin) / 2
            if (h > w) {
                val t = w; w = h; h = t
                angle += Math.PI / 2
            }
            if (h < 3) continue
            val d = w * h * UNCLIP / (2 * (w + h))
            lines += Line((cx / scale).toFloat(), (cy / scale).toFloat(), ((w + 2 * d) / scale).toFloat(), ((h + 2 * d) / scale).toFloat(), angle.toFloat())
            if (lines.size >= MAX_LINES) break
        }
        return lines
    }

    private fun push(i: Int, prob: FloatArray, seen: BooleanArray, stack: IntArray, top: Int): Int? {
        if (seen[i] || prob[i] <= PIXEL_MIN) return null
        seen[i] = true
        stack[top] = i
        return top + 1
    }

    /** Reads a line; steep lines in both directions, the more confident one applies. `null` if nothing is confident. */
    private fun readLine(bitmap: Bitmap, line: Line): String? {
        val angles = if (abs(cos(line.angle)) < 0.7f) listOf(line.angle, line.angle + Math.PI.toFloat()) else listOf(line.angle)
        var best: Pair<Float, String>? = null
        for (a in angles) {
            val r = readAt(bitmap, line, a)
            if (best == null || r.first > best.first) best = r
        }
        return best?.takeIf { it.first >= LINE_MIN && it.second.isNotBlank() }?.second?.trim()
    }

    private fun readAt(bitmap: Bitmap, line: Line, angle: Float): Pair<Float, String> {
        val width = (REC_HEIGHT * line.w / line.h).roundToInt().coerceIn(8, REC_WIDTH)
        val crop = Bitmap.createBitmap(width, REC_HEIGHT, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            postTranslate(-line.cx, -line.cy)
            postRotate(-Math.toDegrees(angle.toDouble()).toFloat())
            postScale(width / line.w, REC_HEIGHT / line.h)
            postTranslate(width / 2f, REC_HEIGHT / 2f)
        }
        Canvas(crop).drawBitmap(bitmap, m, Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(width * REC_HEIGHT)
        crop.getPixels(pixels, 0, width, 0, 0, width, REC_HEIGHT)
        crop.recycle()
        recIn.rewind()
        for (y in 0 until REC_HEIGHT) for (x in 0 until REC_WIDTH) {
            if (x < width) {
                val p = pixels[y * width + x]
                // Outside the image the cutout stays transparent: then grey like the padding.
                if ((p ushr 24) == 0) {
                    recIn.putFloat(0f); recIn.putFloat(0f); recIn.putFloat(0f)
                } else {
                    recIn.putFloat(((p shr 16) and 0xff) / 127.5f - 1f)
                    recIn.putFloat(((p shr 8) and 0xff) / 127.5f - 1f)
                    recIn.putFloat((p and 0xff) / 127.5f - 1f)
                }
            } else {
                recIn.putFloat(0f); recIn.putFloat(0f); recIn.putFloat(0f)
            }
        }
        recIn.rewind()
        recOut.rewind()
        rec.run(recIn, recOut)
        recOut.rewind()
        val classes = chars.size
        val text = StringBuilder()
        var confidence = 0f
        var count = 0
        var previous = 0
        val row = FloatArray(classes)
        for (t in 0 until recSteps) {
            for (k in 0 until classes) row[k] = recOut.float
            var best = 0
            for (k in 1 until classes) if (row[k] > row[best]) best = k
            if (best != 0 && best != previous) {
                text.append(chars[best])
                confidence += row[best]
                count++
            }
            previous = best
        }
        return (if (count == 0) 0f else confidence / count) to text.toString()
    }

    companion object {
        /** Quarter (0 to 3) of the reading direction [angle] (radians, in the image y points down): 0 means rightwards. */
        internal fun readingQuarter(angle: Float): Int {
            val degrees = ((Math.toDegrees(angle.toDouble()) % 360 + 360) % 360)
            return (degrees / 90).roundToInt() % 4
        }

        /**
         * Clockwise rotation that sets text with reading direction in quarter [quarter] upright: if it runs downwards
         * (90°), the image lies rotated by 90° clockwise and must go 270° further.
         */
        internal fun uprightFromReading(quarter: Int): Int = (360 - quarter * 90) % 360

        /** Loads both models from the assets of :androidApp; `null` if they are missing. */
        fun open(): TextOcr? = runCatching {
            val assets = AndroidPlatform.requireContext().assets
            fun model(name: String): Interpreter {
                val bytes = assets.open("ocr/$name").use { it.readBytes() }
                val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }
                return Interpreter(buffer, Interpreter.Options().setNumThreads(4))
            }
            val keys = JSONArray(assets.open("ocr/keys.json").use { it.readBytes().decodeToString() })
            // Position 0 is "nothing" (CTC), then the dictionary, at the end the space character.
            val chars = listOf("") + List(keys.length()) { keys.getString(it) } + listOf(" ")
            TextOcr(model("det.tflite"), model("rec.tflite"), chars)
        }.getOrNull()
    }
}
