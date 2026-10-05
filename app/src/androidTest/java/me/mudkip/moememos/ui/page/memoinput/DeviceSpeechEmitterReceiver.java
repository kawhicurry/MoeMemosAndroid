package me.mudkip.moememos.ui.page.memoinput;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;

/** Emits deterministic Mandarin from the device speaker; packaged only in the instrumentation APK. */
public final class DeviceSpeechEmitterReceiver extends BroadcastReceiver {
    public static final String EXTRA_PHRASE = "phrase";
    public static final String DEFAULT_PHRASE = "墨墨语音验收今天阳光很好";

    private PendingResult pendingResult;
    private TextToSpeech engine;
    private boolean finished;

    @Override
    public void onReceive(Context context, Intent intent) {
        pendingResult = goAsync();
        engine = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS || engine == null) {
                finish();
                return;
            }
            engine.setLanguage(Locale.SIMPLIFIED_CHINESE);
            engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) { finish(); }
                @Override @Deprecated public void onError(String utteranceId) { finish(); }
                @Override public void onError(String utteranceId, int errorCode) { finish(); }
            });
            String phrase = intent.getStringExtra(EXTRA_PHRASE);
            engine.speak(
                    phrase == null ? DEFAULT_PHRASE : phrase,
                    TextToSpeech.QUEUE_FLUSH,
                    new Bundle(),
                    "moe-ui-speech-emitter"
            );
        });
    }

    private synchronized void finish() {
        if (finished) return;
        finished = true;
        if (engine != null) engine.shutdown();
        if (pendingResult != null) pendingResult.finish();
    }
}
