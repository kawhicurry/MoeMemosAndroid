package me.mudkip.moememos.ui.page.memoinput

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Small lifecycle-safe recorder used by the memo editor.
 *
 * Speech-to-text is handled by the system recognizer. This recorder is the
 * lossless fallback path: the user can keep the original voice note as an
 * attachment even when recognition is unavailable or inaccurate.
 */
internal class MemoAudioRecorder(
    private val context: Context,
    private val onMaxDurationReached: (File?) -> Unit = {},
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean
        get() = recorder != null

    @Synchronized
    fun start(): File {
        check(recorder == null) { "A memo recording is already active" }

        val directory = File(context.cacheDir, "recordings").also { it.mkdirs() }
        val file = File.createTempFile("memo_voice_", ".m4a", directory)
        val nextRecorder = createRecorder()
        try {
            nextRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96_000)
                setAudioSamplingRate(44_100)
                setMaxDuration(MAX_RECORDING_DURATION_MILLIS)
                setOnInfoListener { source, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        finishAfterSystemStop(source)
                    }
                }
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (error: Throwable) {
            runCatching { nextRecorder.release() }
            file.delete()
            throw error
        }

        outputFile = file
        recorder = nextRecorder
        return file
    }

    /** Returns a non-empty recording, or null when the capture was too short. */
    @Synchronized
    fun stop(): File? {
        val activeRecorder = recorder ?: return null
        val file = outputFile
        recorder = null
        outputFile = null

        val stopped = runCatching { activeRecorder.stop() }.isSuccess
        runCatching { activeRecorder.release() }
        if (!stopped || file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            return null
        }
        return file
    }

    @Synchronized
    fun cancel() {
        val activeRecorder = recorder
        val file = outputFile
        recorder = null
        outputFile = null
        if (activeRecorder != null) {
            runCatching { activeRecorder.stop() }
            runCatching { activeRecorder.release() }
        }
        file?.delete()
    }

    /**
     * Android stops MediaRecorder itself after [setMaxDuration]. Calling stop() again can throw and
     * used to make us delete an otherwise valid ten-minute recording. Detach the exact recorder
     * that raised the event, release it, and hand the finalized file back to the editor instead.
     */
    private fun finishAfterSystemStop(source: MediaRecorder) {
        val file = synchronized(this) {
            if (recorder !== source) {
                return
            }
            recorder = null
            outputFile.also { outputFile = null }
        }
        runCatching { source.release() }
        val completed = file?.takeIf { it.exists() && it.length() > 0L }
        if (completed == null) {
            file?.delete()
        }
        onMaxDurationReached(completed)
    }

    @Suppress("DEPRECATION")
    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }
    }

    private companion object {
        const val MAX_RECORDING_DURATION_MILLIS = 10 * 60 * 1_000
    }
}
