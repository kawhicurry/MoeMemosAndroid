package me.mudkip.moememos.ui.page.memoinput

import android.content.ActivityNotFoundException
import android.speech.SpeechRecognizer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class VoiceInputTest {
    @Test
    fun insertsAtCursorAndPreservesSurroundingText() {
        val result = insertSpeechResult(
            TextFieldValue("morning evening", TextRange(7)),
            "sunny day",
        )

        assertEquals("morning sunny day evening", result.text)
        assertEquals(TextRange(18), result.selection)
    }

    @Test
    fun replacesSelectionAndLeavesBlankResultUntouched() {
        val original = TextFieldValue("old draft", TextRange(0, 3))
        val replaced = insertSpeechResult(original, "new")

        assertEquals("new draft", replaced.text)
        assertEquals(TextRange(3), replaced.selection)
        assertEquals(original, insertSpeechResult(original, "   "))
    }

    @Test
    fun prefersExternalActivityWithoutRequiringOurMicrophonePermission() {
        assertEquals(
            SpeechInputAction.LAUNCH_ACTIVITY,
            selectSpeechInputAction(
                activityAvailable = true,
                serviceAvailable = true,
                audioPermissionGranted = false,
            ),
        )
    }

    @Test
    fun fallsBackToServiceOrPermissionWhenActivityIsMissing() {
        assertEquals(
            SpeechInputAction.START_SERVICE,
            selectSpeechInputAction(
                activityAvailable = false,
                serviceAvailable = true,
                audioPermissionGranted = true,
            ),
        )
        assertEquals(
            SpeechInputAction.REQUEST_AUDIO_PERMISSION,
            selectSpeechInputAction(
                activityAvailable = false,
                serviceAvailable = true,
                audioPermissionGranted = false,
            ),
        )
        assertEquals(
            SpeechInputAction.UNAVAILABLE,
            selectSpeechInputAction(
                activityAvailable = false,
                serviceAvailable = false,
                audioPermissionGranted = true,
            ),
        )
    }

    @Test
    fun activityDisappearingAtLaunchFallsBackToServiceOrPermission() {
        fun coordinate(permissionGranted: Boolean): SpeechInputAction = coordinateSpeechInput(
            isRecording = false,
            activityAvailable = { true },
            launchActivity = { throw ActivityNotFoundException("handler disappeared") },
            isActivityNotFound = { error -> error is ActivityNotFoundException },
            serviceAvailable = { true },
            audioPermissionGranted = { permissionGranted },
        )

        assertEquals(SpeechInputAction.START_SERVICE, coordinate(permissionGranted = true))
        assertEquals(SpeechInputAction.REQUEST_AUDIO_PERMISSION, coordinate(permissionGranted = false))
    }

    @Test
    fun recordingBlocksEverySpeechLookupLaunchAndPermissionDecision() {
        var calls = 0

        val action = coordinateSpeechInput(
            isRecording = true,
            activityAvailable = { calls += 1; true },
            launchActivity = { calls += 1 },
            isActivityNotFound = { calls += 1; true },
            serviceAvailable = { calls += 1; true },
            audioPermissionGranted = { calls += 1; true },
        )

        assertEquals(SpeechInputAction.BLOCKED, action)
        assertEquals(0, calls)
    }

    @Test
    fun mapsCancellationBusyAndPermissionErrors() {
        assertEquals(
            SpeechRecognitionFailure.CANCELLED,
            mapSpeechRecognizerError(SpeechRecognizer.ERROR_CLIENT),
        )
        assertEquals(
            SpeechRecognitionFailure.BUSY,
            mapSpeechRecognizerError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY),
        )
        assertEquals(
            SpeechRecognitionFailure.BUSY,
            mapSpeechRecognizerError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS),
        )
        assertEquals(
            SpeechRecognitionFailure.PERMISSION_DENIED,
            mapSpeechRecognizerError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS),
        )
        assertEquals(
            SpeechRecognitionFailure.PERMISSION_DENIED,
            speechPermissionFailure(granted = false),
        )
        assertNull(speechPermissionFailure(granted = true))
    }

    @Test
    fun mapsNetworkNoMatchLanguageAndUnavailableErrors() {
        listOf(
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
        ).forEach { error ->
            assertEquals(SpeechRecognitionFailure.NETWORK, mapSpeechRecognizerError(error))
        }
        listOf(
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH,
        ).forEach { error ->
            assertEquals(SpeechRecognitionFailure.NO_MATCH, mapSpeechRecognizerError(error))
        }
        listOf(
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        ).forEach { error ->
            assertEquals(SpeechRecognitionFailure.LANGUAGE_UNAVAILABLE, mapSpeechRecognizerError(error))
        }
        listOf(
            SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT,
            SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS,
        ).forEach { error ->
            assertEquals(SpeechRecognitionFailure.UNAVAILABLE, mapSpeechRecognizerError(error))
        }
        assertEquals(
            SpeechRecognitionFailure.GENERIC,
            mapSpeechRecognizerError(SpeechRecognizer.ERROR_AUDIO),
        )
    }

    @Test
    fun choosesFirstNonBlankTrimmedResult() {
        assertEquals("hello world", firstSpeechResult(listOf("  ", " hello world ", "backup")))
        assertNull(firstSpeechResult(emptyList()))
        assertNull(firstSpeechResult(null))
    }

    @Test
    fun stripsUnicodeExtensionsFromSpeechLanguageTag() {
        val extendedChinese = Locale.forLanguageTag("zh-CN-u-ca-chinese-nu-hanidec")

        assertEquals("zh-CN", speechLanguageTag(extendedChinese))
        assertEquals("en-US", speechLanguageTag(Locale.forLanguageTag("en-US")))
    }
}
