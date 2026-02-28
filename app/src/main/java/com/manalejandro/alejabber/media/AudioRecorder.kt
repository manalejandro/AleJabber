package com.manalejandro.alejabber.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class RecordingState { IDLE, RECORDING, STOPPED }

/**
 * Wraps [MediaRecorder] to record audio from the microphone.
 *
 * Emits real-time elapsed duration via [durationMs] (updated every 100 ms while recording).
 * Output format: MPEG-4 / AAC, 44 100 Hz, 128 kbps.
 */
@Singleton
class AudioRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime: Long = 0L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /** Starts recording. Returns true on success, false if an error occurs. */
    fun startRecording(): Boolean {
        return try {
            val dir = File(context.cacheDir, "audio").also { it.mkdirs() }
            outputFile = File(dir, "audio_${System.currentTimeMillis()}.m4a")

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder!!.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile!!.absolutePath)
                prepare()
                start()
            }
            startTime = System.currentTimeMillis()
            _durationMs.value = 0L
            _state.value = RecordingState.RECORDING

            // Tick every 100 ms so the UI shows a live counter
            tickerJob = scope.launch {
                while (true) {
                    delay(100)
                    _durationMs.value = System.currentTimeMillis() - startTime
                }
            }
            true
        } catch (e: Exception) {
            cleanup()
            false
        }
    }

    /**
     * Stops recording and returns the output [File].
     * Returns null if recording was not active or an error occurs.
     */
    fun stopRecording(): File? {
        tickerJob?.cancel()
        tickerJob = null
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            _durationMs.value = System.currentTimeMillis() - startTime
            _state.value = RecordingState.STOPPED
            outputFile
        } catch (e: Exception) {
            cleanup()
            null
        }
    }

    /** Cancels the current recording and discards the file. */
    fun cancelRecording() {
        tickerJob?.cancel()
        tickerJob = null
        cleanup()
        outputFile?.delete()
        outputFile = null
        _state.value = RecordingState.IDLE
        _durationMs.value = 0L
    }

    /** Resets state back to IDLE after the file has been consumed. */
    fun reset() {
        _state.value = RecordingState.IDLE
        _durationMs.value = 0L
    }

    private fun cleanup() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }
}

