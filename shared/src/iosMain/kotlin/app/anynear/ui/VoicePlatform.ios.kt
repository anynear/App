package app.anynear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import app.anynear.attachments.VoiceTranscript
import app.anynear.attachments.toByteArray
import app.anynear.attachments.toNSData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToURL
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.Speech.SFSpeechURLRecognitionRequest
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

/** Shorter is an accidental tap. */
private const val MIN_MILLIS = 500L

/** Recording and playback share one session; the speaker instead of the earpiece. */
@OptIn(ExperimentalForeignApi::class)
private fun activateSession(): Boolean {
    val session = AVAudioSession.sharedInstance()
    return session.setCategory(AVAudioSessionCategoryPlayAndRecord, withOptions = AVAudioSessionCategoryOptionDefaultToSpeaker, error = null) &&
        session.setActive(true, error = null)
}

@OptIn(ExperimentalForeignApi::class)
private class IosVoiceRecorder : VoiceRecorder {
    private var recorder: AVAudioRecorder? = null
    private var url: NSURL? = null

    override fun start(safetyMillis: Long): Boolean {
        cancel()
        if (!activateSession()) return false
        val target = NSURL.fileURLWithPath(NSTemporaryDirectory() + "voice-${NSDate().timeIntervalSince1970}.m4a")
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to kAudioFormatMPEG4AAC.toInt(),
            AVSampleRateKey to 16_000.0,
            AVNumberOfChannelsKey to 1,
            AVEncoderBitRateKey to 24_000,
        )
        val r = AVAudioRecorder(uRL = target, settings = settings, error = null)
        if (!r.prepareToRecord() || !r.recordForDuration(safetyMillis / 1000.0)) {
            NSFileManager.defaultManager.removeItemAtURL(target, error = null)
            return false
        }
        recorder = r
        url = target
        return true
    }

    override fun elapsedMillis(): Long = ((recorder?.currentTime ?: 0.0) * 1000).toLong()

    override fun stop(): RecordedVoice? {
        val r = recorder ?: return null
        val target = url
        val elapsed = elapsedMillis()
        r.stop()
        recorder = null
        url = null
        val data = target?.let { NSData.dataWithContentsOfURL(it) }
        target?.let { NSFileManager.defaultManager.removeItemAtURL(it, error = null) }
        if (data == null || data.length.toInt() == 0 || elapsed < MIN_MILLIS) return null
        val duration = AVAudioPlayer(data = data, error = null).duration
        return RecordedVoice(data.toByteArray(), if (duration > 0) (duration * 1000).toLong() else elapsed)
    }

    override fun cancel() {
        recorder?.stop()
        recorder?.deleteRecording()
        recorder = null
        url = null
    }
}

@Composable
internal actual fun rememberVoiceRecorder(): VoiceRecorder {
    val recorder = remember { IosVoiceRecorder() }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

@Composable
internal actual fun rememberMicPermission(): MicPermission = remember {
    object : MicPermission {
        override fun request(onResult: (Boolean) -> Unit) {
            val session = AVAudioSession.sharedInstance()
            if (session.recordPermission == AVAudioSessionRecordPermissionGranted) {
                onResult(true)
                return
            }
            session.requestRecordPermission { granted -> dispatch_async(dispatch_get_main_queue()) { onResult(granted) } }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IosVoicePlayer : VoicePlayer {
    private var player: AVAudioPlayer? = null
    private var source: ByteArray? = null

    override fun play(bytes: ByteArray, fromMillis: Long): Boolean {
        val p = player?.takeIf { source === bytes } ?: run {
            release()
            AVAudioPlayer(data = bytes.toNSData(), error = null).also {
                player = it
                source = bytes
            }
        }
        if (!activateSession() || !p.prepareToPlay()) return false.also { release() }
        p.currentTime = if (fromMillis / 1000.0 < p.duration) fromMillis / 1000.0 else 0.0
        return p.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun positionMillis(): Long = ((player?.currentTime ?: 0.0) * 1000).toLong()

    override fun isPlaying(): Boolean = player?.playing == true

    override fun release() {
        player?.stop()
        player = null
        source = null
    }
}

internal actual fun createVoicePlayer(): VoicePlayer = IosVoicePlayer()

/**
 * Local recognition with `SFSpeechRecognizer`, only on the device (`requiresOnDeviceRecognition`), never via
 * Apple's servers. Asks for permission the first time; denied or not possible: `null`, the
 * voice message then goes without a text preview.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun transcribeVoice(bytes: ByteArray, durationMillis: Long, log: (String) -> Unit): VoiceTranscript? {
    if (!speechAllowed()) return null.also { log("Spracherkennung nicht erlaubt") }
    val recognizer = SFSpeechRecognizer()
    if (!recognizer.isAvailable() || !recognizer.supportsOnDeviceRecognition) return null.also { log("keine Erkennung auf dem Gerät") }
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + "transcribe-${NSDate().timeIntervalSince1970}.m4a")
    if (!bytes.toNSData().writeToURL(url, atomically = true)) return null
    try {
        val request = SFSpeechURLRecognitionRequest(uRL = url)
        request.requiresOnDeviceRecognition = true
        request.shouldReportPartialResults = false
        val text = withTimeoutOrNull(RECOGNITION_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { cont ->
                val task = recognizer.recognitionTaskWithRequest(request) { result, error ->
                    when {
                        result != null && result.isFinal() -> if (cont.isActive) cont.resume(result.bestTranscription.formattedString)
                        error != null -> {
                            log("Fehler ${error.code}")
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
                cont.invokeOnCancellation { task.cancel() }
            }
        }
        return text?.takeIf { it.isNotBlank() }?.let { VoiceTranscript(it, complete = true) }
    } finally {
        NSFileManager.defaultManager.removeItemAtURL(url, error = null)
    }
}

private const val RECOGNITION_TIMEOUT_MILLIS = 60_000L

/** Permission for speech recognition, asked for the first time. */
private suspend fun speechAllowed(): Boolean = when (SFSpeechRecognizer.authorizationStatus()) {
    SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized -> true
    SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusNotDetermined -> suspendCancellableCoroutine { cont ->
        SFSpeechRecognizer.requestAuthorization { status ->
            if (cont.isActive) cont.resume(status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized)
        }
    }
    else -> false
}
