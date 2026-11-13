package app.anynear.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.anynear.AndroidPlatform
import app.anynear.attachments.VoicePreview
import app.anynear.attachments.VoiceTranscript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/** Recording settings (ADR 0030). */
private const val SAMPLE_RATE = 16_000
private const val BIT_RATE = 24_000

/** Anything shorter is an accidental tap. */
private const val MIN_MILLIS = 500L

private class AndroidVoiceRecorder(private val context: Context) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var limitReached = false

    override fun start(safetyMillis: Long): Boolean {
        cancel()
        val target = File(context.cacheDir, "voice-${SystemClock.elapsedRealtimeNanos()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        return runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(SAMPLE_RATE)
            r.setAudioEncodingBitRate(BIT_RATE)
            r.setMaxDuration(safetyMillis.toInt())
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) limitReached = true
            }
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = target
            startedAt = SystemClock.elapsedRealtime()
            limitReached = false
            this.safetyMillis = safetyMillis
            true
        }.getOrElse {
            r.release()
            target.delete()
            false
        }
    }

    override fun elapsedMillis(): Long =
        if (recorder == null) 0 else (SystemClock.elapsedRealtime() - startedAt).coerceAtMost(safetyMillis)

    private var safetyMillis = Long.MAX_VALUE

    override fun stop(): RecordedVoice? {
        val r = recorder ?: return null
        val f = file
        val elapsed = elapsedMillis()
        recorder = null
        file = null
        // After reaching the maximum duration MediaRecorder has already stopped by itself; stop() then throws.
        val stopped = runCatching { r.stop() }.isSuccess || limitReached
        r.release()
        val bytes = f?.takeIf { stopped && elapsed >= MIN_MILLIS }?.let { runCatching { it.readBytes() }.getOrNull() }
        f?.delete()
        if (bytes == null || bytes.isEmpty()) return null
        return RecordedVoice(bytes, durationOf(bytes) ?: elapsed)
    }

    override fun cancel() {
        recorder?.let { r ->
            runCatching { r.stop() }
            r.release()
        }
        recorder = null
        file?.delete()
        file = null
    }
}

/** Actual length from the file; `null` if it cannot be read. */
private fun durationOf(bytes: ByteArray): Long? = runCatching {
    val player = MediaPlayer()
    try {
        player.setDataSource(BytesDataSource(bytes))
        player.prepare()
        player.duration.toLong().takeIf { it > 0 }
    } finally {
        player.release()
    }
}.getOrNull()

@Composable
internal actual fun rememberVoiceRecorder(): VoiceRecorder {
    val context = LocalContext.current
    val recorder = remember { AndroidVoiceRecorder(context.applicationContext) }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

@Composable
internal actual fun rememberMicPermission(): MicPermission {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.invoke(granted)
        pending[0] = null
    }
    return remember(launcher) {
        object : MicPermission {
            override fun request(onResult: (Boolean) -> Unit) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    onResult(true)
                } else {
                    pending[0] = onResult
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}

/** Reads the recording directly from memory, without an unencrypted file. */
private class BytesDataSource(private val bytes: ByteArray) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= bytes.size) return -1
        val n = minOf(size.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, n)
        return n
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}

private class AndroidVoicePlayer : VoicePlayer {
    private var player: MediaPlayer? = null
    private var source: ByteArray? = null

    override fun play(bytes: ByteArray, fromMillis: Long): Boolean = runCatching {
        val p = player?.takeIf { source === bytes } ?: MediaPlayer().also { p ->
            release()
            p.setDataSource(BytesDataSource(bytes))
            p.prepare()
            player = p
            source = bytes
        }
        if (fromMillis > 0 && fromMillis < p.duration) p.seekTo(fromMillis.toInt()) else p.seekTo(0)
        p.start()
        true
    }.getOrElse {
        release()
        false
    }

    override fun pause() {
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    override fun positionMillis(): Long = runCatching { player?.currentPosition?.toLong() }.getOrNull() ?: 0

    override fun isPlaying(): Boolean = runCatching { player?.isPlaying }.getOrNull() == true

    override fun release() {
        player?.release()
        player = null
        source = null
    }
}

internal actual fun createVoicePlayer(): VoicePlayer = AndroidVoicePlayer()

/** Decoded recording for speech recognition: 16-bit PCM. */
private class Pcm(val data: ByteArray, val sampleRate: Int, val channels: Int)

/**
 * Local recognition from Android 13 on: the recording is decoded to PCM and passed through a pipe to the
 * on-device recognition, never to a service on the internet. Older Android versions and phones
 * without a speech model return `null`; the voice message then goes without a text preview.
 */
internal actual suspend fun transcribeVoice(bytes: ByteArray, durationMillis: Long, log: (String) -> Unit): VoiceTranscript? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null.also { log("nicht möglich vor Android 13") }
    val context = AndroidPlatform.context ?: return null
    val available = withContext(Dispatchers.Main) { runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false) }
    if (!available) return null.also { log("keine Erkennung auf dem Gerät") }
    val pcm = withContext(Dispatchers.Default) { decodePcm(bytes, VoicePreview.RECOGNIZE_MILLIS) }
        ?: return null.also { log("Aufnahme ließ sich nicht dekodieren") }
    log("Erkennung startet (${pcm.sampleRate} Hz, ${pcm.channels} Kanal, ${Locale.getDefault().toLanguageTag()})")
    val recognized = withTimeoutOrNull(RECOGNITION_TIMEOUT_MILLIS) { recognize(context, pcm, log) }
        ?: return null.also { log("nichts erkannt") }
    if (recognized.text.isBlank()) return null.also { log("nichts erkannt") }
    return VoiceTranscript(recognized.text, complete = recognized.complete && durationMillis <= VoicePreview.RECOGNIZE_MILLIS)
}

private const val RECOGNITION_TIMEOUT_MILLIS = 60_000L

private class Recognized(val text: String, val complete: Boolean)

/** AAC from memory to PCM, at most the first [maxMillis]; `null` on an error. */
private fun decodePcm(bytes: ByteArray, maxMillis: Long): Pcm? {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(BytesDataSource(bytes))
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        val out = ByteArrayOutputStream()
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            // Upper limit against an endless loop with a broken decoder.
            var rounds = 0
            while (!outputDone && rounds++ < 100_000) {
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(CODEC_WAIT_MICROS)
                    if (i >= 0) {
                        val n = extractor.readSampleData(codec.getInputBuffer(i)!!, 0)
                        if (n < 0 || extractor.sampleTime > maxMillis * 1000) {
                            codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, CODEC_WAIT_MICROS)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (o >= 0) {
                    val buffer = codec.getOutputBuffer(o)!!
                    val chunk = ByteArray(info.size)
                    buffer.position(info.offset)
                    buffer.get(chunk)
                    out.write(chunk)
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
        out.toByteArray().takeIf { it.isNotEmpty() }?.let { Pcm(it, sampleRate, channels) }
    } catch (e: Exception) {
        null
    } finally {
        extractor.release()
    }
}

private const val CODEC_WAIT_MICROS = 10_000L

/** On-device recognition from a pipe; several segments (pauses) are appended to one another. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private suspend fun recognize(context: Context, pcm: Pcm, log: (String) -> Unit): Recognized? = withContext(Dispatchers.Main) {
    suspendCancellableCoroutine { cont ->
        val pipe = ParcelFileDescriptor.createPipe()
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val segments = mutableListOf<String>()
        fun text(bundle: Bundle?) =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }
        fun finish(result: Recognized?) {
            if (cont.isActive) cont.resume(result)
            recognizer.destroy()
            runCatching { pipe[0].close() }
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onSegmentResults(segmentResults: Bundle) {
                text(segmentResults)?.let(segments::add)
            }

            override fun onEndOfSegmentedSession() = finish(Recognized(segments.joinToString(" "), complete = true))

            override fun onResults(results: Bundle?) {
                text(results)?.let(segments::add)
                finish(Recognized(segments.joinToString(" "), complete = true))
            }

            // If recognition aborts midway, what was recognised counts as the beginning, not as the whole.
            override fun onError(error: Int) {
                log("Fehler $error nach ${segments.size} Abschnitten")
                finish(segments.takeIf { it.isNotEmpty() }?.let { Recognized(it.joinToString(" "), complete = false) })
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, pcm.channels)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, pcm.sampleRate)
            putExtra(RecognizerIntent.EXTRA_SEGMENTED_SESSION, RecognizerIntent.EXTRA_AUDIO_SOURCE)
        }
        recognizer.startListening(intent)
        thread(name = "voice-pcm") {
            runCatching { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(pcm.data) } }
        }
        cont.invokeOnCancellation {
            Handler(Looper.getMainLooper()).post {
                recognizer.destroy()
                runCatching { pipe[0].close() }
            }
        }
    }
}
