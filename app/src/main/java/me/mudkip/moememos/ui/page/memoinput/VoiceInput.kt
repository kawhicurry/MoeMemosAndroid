package me.mudkip.moememos.ui.page.memoinput

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.util.Locale

internal enum class SpeechInputAction {
    BLOCKED,
    LAUNCH_ACTIVITY,
    START_SERVICE,
    REQUEST_AUDIO_PERMISSION,
    UNAVAILABLE,
}

internal enum class SpeechRecognitionFailure {
    CANCELLED,
    BUSY,
    NETWORK,
    PERMISSION_DENIED,
    NO_MATCH,
    LANGUAGE_UNAVAILABLE,
    UNAVAILABLE,
    GENERIC,
}

internal enum class SpeechRecognizerStartResult {
    STARTED,
    BUSY,
    PERMISSION_DENIED,
    UNAVAILABLE,
}

/**
 * Chooses the public Android speech API to use without depending on a vendor package name.
 *
 * The external activity remains the preferred path because it owns its UI and permission flow.
 * A device that exposes only a [android.speech.RecognitionService] falls back to the in-app
 * [SpeechRecognizer] path, which needs this app's microphone permission.
 */
internal fun selectSpeechInputAction(
    activityAvailable: Boolean,
    serviceAvailable: Boolean,
    audioPermissionGranted: Boolean,
): SpeechInputAction = when {
    activityAvailable -> SpeechInputAction.LAUNCH_ACTIVITY
    !serviceAvailable -> SpeechInputAction.UNAVAILABLE
    audioPermissionGranted -> SpeechInputAction.START_SERVICE
    else -> SpeechInputAction.REQUEST_AUDIO_PERMISSION
}

/**
 * Coordinates availability checks and launch without binding the decision logic to Android UI.
 * Resolvers are lazy so an active audio recording prevents every lookup, permission request and
 * launch; the service is only queried when the Activity path is absent or disappears at launch.
 */
internal fun coordinateSpeechInput(
    isRecording: Boolean,
    activityAvailable: () -> Boolean,
    launchActivity: () -> Unit,
    isActivityNotFound: (RuntimeException) -> Boolean,
    serviceAvailable: () -> Boolean,
    audioPermissionGranted: () -> Boolean,
): SpeechInputAction {
    if (isRecording) return SpeechInputAction.BLOCKED

    if (activityAvailable()) {
        try {
            launchActivity()
            return SpeechInputAction.LAUNCH_ACTIVITY
        } catch (error: RuntimeException) {
            if (!isActivityNotFound(error)) throw error
        }
    }

    val hasService = serviceAvailable()
    return selectSpeechInputAction(
        activityAvailable = false,
        serviceAvailable = hasService,
        audioPermissionGranted = hasService && audioPermissionGranted(),
    )
}

internal fun speechLanguageTag(locale: Locale): String =
    locale.stripExtensions().toLanguageTag()

internal fun mapSpeechRecognizerError(errorCode: Int): SpeechRecognitionFailure = when (errorCode) {
    SpeechRecognizer.ERROR_CLIENT -> SpeechRecognitionFailure.CANCELLED
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> SpeechRecognitionFailure.BUSY
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechRecognitionFailure.NETWORK
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechRecognitionFailure.PERMISSION_DENIED
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    SpeechRecognizer.ERROR_NO_MATCH -> SpeechRecognitionFailure.NO_MATCH
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechRecognitionFailure.LANGUAGE_UNAVAILABLE
    SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
    SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> SpeechRecognitionFailure.UNAVAILABLE
    else -> SpeechRecognitionFailure.GENERIC
}

internal fun firstSpeechResult(results: List<String>?): String? =
    results?.firstOrNull { it.isNotBlank() }?.trim()

internal fun speechPermissionFailure(granted: Boolean): SpeechRecognitionFailure? =
    if (granted) null else SpeechRecognitionFailure.PERMISSION_DENIED

/** Owns one platform recognizer and releases it with the editor lifecycle. */
internal class PlatformSpeechRecognizer(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onFailure: (SpeechRecognitionFailure) -> Unit,
    private val onActiveChanged: (Boolean) -> Unit,
) : RecognitionListener {
    private val applicationContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var destroyed = false

    fun start(intent: Intent): SpeechRecognizerStartResult {
        if (destroyed) return SpeechRecognizerStartResult.UNAVAILABLE
        if (active) return SpeechRecognizerStartResult.BUSY
        val serviceAvailable = runCatching {
            SpeechRecognizer.isRecognitionAvailable(applicationContext)
        }.getOrDefault(false)
        if (!serviceAvailable) return SpeechRecognizerStartResult.UNAVAILABLE

        val platformRecognizer = try {
            recognizer ?: SpeechRecognizer.createSpeechRecognizer(applicationContext).also {
                it.setRecognitionListener(this)
                recognizer = it
            }
        } catch (_: RuntimeException) {
            return SpeechRecognizerStartResult.UNAVAILABLE
        }

        active = true
        onActiveChanged(true)
        return try {
            platformRecognizer.startListening(intent)
            SpeechRecognizerStartResult.STARTED
        } catch (_: SecurityException) {
            finishListening()
            SpeechRecognizerStartResult.PERMISSION_DENIED
        } catch (_: RuntimeException) {
            finishListening()
            platformRecognizer.destroy()
            recognizer = null
            SpeechRecognizerStartResult.UNAVAILABLE
        }
    }

    fun cancel() {
        if (!active) return
        active = false
        recognizer?.cancel()
        onActiveChanged(false)
        onFailure(SpeechRecognitionFailure.CANCELLED)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        val wasActive = active
        active = false
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        if (wasActive) onActiveChanged(false)
    }

    override fun onResults(results: Bundle?) {
        if (!active) return
        finishListening()
        val recognized = firstSpeechResult(
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        )
        if (recognized == null) {
            onFailure(SpeechRecognitionFailure.NO_MATCH)
        } else {
            onResult(recognized)
        }
    }

    override fun onError(error: Int) {
        if (!active) return
        finishListening()
        onFailure(mapSpeechRecognizerError(error))
    }

    private fun finishListening() {
        if (!active) return
        active = false
        onActiveChanged(false)
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}

/** Inserts recognized speech at the current selection without destroying text. */
internal fun insertSpeechResult(
    value: TextFieldValue,
    recognizedText: String,
): TextFieldValue {
    val spoken = recognizedText.trim()
    if (spoken.isEmpty()) return value

    val start = minOf(value.selection.start, value.selection.end).coerceIn(0, value.text.length)
    val end = maxOf(value.selection.start, value.selection.end).coerceIn(start, value.text.length)
    val before = value.text.substring(0, start)
    val after = value.text.substring(end)
    val prefix = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
    val suffix = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
    val insertion = prefix + spoken + suffix
    val updated = before + insertion + after
    // When the cursor was immediately before an existing separator, place it
    // after that separator so continued dictation does not split the sentence.
    val trailingSeparator = if (prefix.isNotEmpty() && suffix.isEmpty() && after.firstOrNull()?.isWhitespace() == true) 1 else 0
    return TextFieldValue(
        text = updated,
        selection = TextRange(before.length + insertion.length + trailingSeparator),
    )
}
