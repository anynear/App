package app.anynear.ui

import androidx.compose.runtime.Composable
import app.anynear.attachments.VoiceTranscript

/** Finished recording: AAC in MP4, mono, 16 kHz, 24 kbit/s (ADR 0030). */
internal class RecordedVoice(val bytes: ByteArray, val durationMillis: Long)

/**
 * Records a voice message from the microphone. Until [stop] the recording lies only in the private
 * cache of the app and is deleted immediately afterwards; it is kept encrypted as a draft.
 */
internal interface VoiceRecorder {
    /**
     * Starts the recording; `false` if the microphone does not work. The UI monitors the maximum duration;
     * [safetyMillis] is only the limit at which the system stops by itself.
     */
    fun start(safetyMillis: Long): Boolean

    /** Length of the running recording so far. */
    fun elapsedMillis(): Long

    /** Ends the recording; `null` if nothing usable came out. */
    fun stop(): RecordedVoice?

    /** Cancels and discards everything. */
    fun cancel()
}

@Composable
internal expect fun rememberVoiceRecorder(): VoiceRecorder

/** Asks for the microphone the first time. [onResult] with `true` if it is allowed. */
internal interface MicPermission {
    fun request(onResult: (Boolean) -> Unit)
}

@Composable
internal expect fun rememberMicPermission(): MicPermission

/** Plays a voice message that lies in memory. */
internal interface VoicePlayer {
    /** Plays [bytes] from [fromMillis]; `false` if that does not work. */
    fun play(bytes: ByteArray, fromMillis: Long): Boolean

    fun pause()

    fun positionMillis(): Long

    /** `false` after [pause] and when the recording has ended. */
    fun isPlaying(): Boolean

    fun release()
}

internal expect fun createVoicePlayer(): VoicePlayer

/**
 * Local speech recognition of the recording (ADR 0030): only on the phone, never via a service on the internet.
 * At most the beginning is recognized ([app.anynear.attachments.VoicePreview.RECOGNIZE_MILLIS]).
 * `null` if the phone cannot do it, it is not allowed or nothing was recognized; the reason (never the
 * text) goes to [log] for the log.
 */
internal expect suspend fun transcribeVoice(bytes: ByteArray, durationMillis: Long, log: (String) -> Unit): VoiceTranscript?
