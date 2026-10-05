package me.mudkip.moememos.ui.page.memoinput

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Pixel smoke test for the actual configured RecognitionService; it never changes system packages. */
@RunWith(AndroidJUnit4::class)
class SystemSpeechRecognitionSmokeTest {
    @Test
    fun configuredServiceBecomesReadyAndCanBeCancelled() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(SpeechRecognizer.isRecognitionAvailable(context))

        val permissionWasGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (!permissionWasGranted) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.RECORD_AUDIO,
            )
        }

        val ttsReady = CountDownLatch(1)
        val recognitionReady = CountDownLatch(1)
        val recognitionFinished = CountDownLatch(1)
        val recognizedText = AtomicReference<String?>(null)
        val recognitionError = AtomicInteger(Int.MIN_VALUE)
        val phrase = "墨墨语音验收今天阳光很好"
        var textToSpeech: TextToSpeech? = null
        var recognizer: SpeechRecognizer? = null

        try {
            instrumentation.runOnMainSync {
                textToSpeech = TextToSpeech(context) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        textToSpeech?.language = Locale.SIMPLIFIED_CHINESE
                    }
                    ttsReady.countDown()
                }
            }
            assertTrue("The configured TTS engine did not initialize", ttsReady.await(8, TimeUnit.SECONDS))

            instrumentation.runOnMainSync {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context)
                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        recognitionReady.countDown()
                        // A single reproducible device-speaker attempt. AEC is allowed to suppress it;
                        // readiness and clean cancellation remain the required smoke assertions.
                        textToSpeech?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "moe-speech-smoke")
                    }

                    override fun onResults(results: Bundle?) {
                        recognizedText.set(
                            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull(),
                        )
                        recognitionFinished.countDown()
                    }

                    override fun onError(error: Int) {
                        recognitionError.set(error)
                        recognitionFinished.countDown()
                    }

                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                recognizer?.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
                    },
                )
            }

            assertTrue(
                "The configured RecognitionService never became ready",
                recognitionReady.await(8, TimeUnit.SECONDS),
            )
            recognitionFinished.await(8, TimeUnit.SECONDS)
            Log.i(
                "MoeSpeechSmoke",
                "ready=true result=${recognizedText.get()} error=${recognitionError.get()}",
            )
        } finally {
            instrumentation.runOnMainSync {
                recognizer?.cancel()
                recognizer?.destroy()
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            }
            // Keep the permission on this disposable debug-only package. Revoking a runtime
            // permission kills the target process and makes AndroidJUnitRunner report a crash
            // after an otherwise successful test.
        }
    }
}
