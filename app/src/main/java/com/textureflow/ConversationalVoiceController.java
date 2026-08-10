package com.textureflow;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/** Coordinates bounded speech recognition and interruptible text-to-speech turns. */
public final class ConversationalVoiceController {
    public enum State {
        STOPPED,
        INITIALIZING,
        IDLE,
        SPEAKING,
        LISTENING,
        PROCESSING,
        UNAVAILABLE,
        ERROR,
        RELEASED
    }

    public enum Failure {
        RECORD_AUDIO_PERMISSION_REQUIRED,
        RECOGNITION_UNAVAILABLE,
        RECOGNITION_BUSY,
        RECOGNITION_NETWORK,
        RECOGNITION_TIMEOUT,
        RECOGNITION_FAILED,
        SPEECH_SYNTHESIS_UNAVAILABLE,
        SPEECH_SYNTHESIS_FAILED
    }

    public interface Callback {
        void onStateChanged(State state);

        void onPartialUtterance(String utterance);

        void onFinalUtterance(String utterance);

        default void onFailure(Failure failure) {
            // Optional: state changes still communicate that the turn ended.
        }
    }

    private static final long LISTEN_TIMEOUT_MILLIS = 20_000L;
    private static final long COMPLETE_SILENCE_MILLIS = 900L;
    private static final long POSSIBLY_COMPLETE_SILENCE_MILLIS = 650L;
    private static final long MINIMUM_SPEECH_MILLIS = 500L;
    private static final int MAX_RECOGNIZED_CHARACTERS = 4_000;
    private static final int MAX_SYNTHESIS_CHARACTERS = 3_500;
    private static final long INTERRUPT_SETTLE_MILLIS = 80L;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong utteranceCounter = new AtomicLong();
    private final Runnable listenTimeout = this::handleListenTimeout;

    private Callback callback;
    private SpeechRecognizer recognizer;
    private TextToSpeech synthesizer;
    private PendingSpeech pendingSpeech;
    private State state = State.STOPPED;
    private boolean started;
    private boolean released;
    private boolean synthesizerReady;
    private boolean recognizerActive;
    private boolean listenAfterSpeech;
    private long recognitionGeneration;
    private String activeUtteranceId;

    public ConversationalVoiceController(Context context, Callback callback) {
        appContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.callback = Objects.requireNonNull(callback, "callback");
    }

    /** Initializes local speech services. Safe to call repeatedly or from any thread. */
    public void start() {
        runOnMain(this::startInternal);
    }

    /** Stops the current turn while retaining speech service instances for a later start. */
    public void stop() {
        runOnMain(() -> {
            if (released) {
                return;
            }
            started = false;
            pendingSpeech = null;
            listenAfterSpeech = false;
            stopSynthesis();
            cancelRecognition();
            changeState(State.STOPPED);
        });
    }

    /** Permanently releases recognizer, synthesizer, callbacks, and queued work. */
    public void release() {
        runOnMain(() -> {
            if (released) {
                return;
            }
            started = false;
            pendingSpeech = null;
            listenAfterSpeech = false;
            stopSynthesis();
            cancelRecognition();
            if (recognizer != null) {
                recognizer.destroy();
                recognizer = null;
            }
            if (synthesizer != null) {
                synthesizer.shutdown();
                synthesizer = null;
            }
            synthesizerReady = false;
            released = true;
            changeState(State.RELEASED);
            callback = null;
        });
    }

    /** Speaks one bounded prompt. Any active recognition or speech is interrupted. */
    public void speak(String text) {
        speak(text, false);
    }

    /** Speaks one prompt, then automatically listens for the user's next turn. */
    public void speakAndListen(String text) {
        speak(text, true);
    }

    /** Starts a bounded recognition turn without speaking first. */
    public void listen() {
        runOnMain(() -> beginListening(false));
    }

    /**
     * UI tap behavior: interrupt speech or processing and listen immediately. While already
     * listening, the same tap ends audio capture and asks the recognizer for a final result.
     */
    public void tapToListenOrInterrupt() {
        runOnMain(() -> {
            if (!isOperational()) {
                return;
            }
            if (state == State.LISTENING && recognizerActive && recognizer != null) {
                mainHandler.removeCallbacks(listenTimeout);
                changeState(State.PROCESSING);
                recognizer.stopListening();
                return;
            }

            pendingSpeech = null;
            listenAfterSpeech = false;
            stopSynthesis();
            cancelRecognition();
            mainHandler.postDelayed(() -> beginListening(false), INTERRUPT_SETTLE_MILLIS);
        });
    }

    /** Interrupts the active speech/listening turn and returns to idle. */
    public void interrupt() {
        runOnMain(() -> {
            if (!isOperational()) {
                return;
            }
            pendingSpeech = null;
            listenAfterSpeech = false;
            stopSynthesis();
            cancelRecognition();
            changeState(State.IDLE);
        });
    }

    public State getState() {
        return state;
    }

    public boolean isRecognitionAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(appContext);
    }

    private void startInternal() {
        if (released || started) {
            return;
        }
        started = true;
        changeState(State.INITIALIZING);

        if (synthesizer == null) {
            synthesizer = new TextToSpeech(appContext, this::handleSynthesizerInitialized);
        } else if (synthesizerReady) {
            changeState(State.IDLE);
        }

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            reportFailure(Failure.RECOGNITION_UNAVAILABLE, State.UNAVAILABLE);
            return;
        }
        if (!hasRecordAudioPermission()) {
            reportFailure(Failure.RECORD_AUDIO_PERMISSION_REQUIRED, State.UNAVAILABLE);
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
        }
    }

    private void speak(String text, boolean listenAfter) {
        String boundedText = boundText(text, MAX_SYNTHESIS_CHARACTERS);
        runOnMain(() -> {
            if (!isOperational() || boundedText.isEmpty()) {
                return;
            }
            cancelRecognition();
            stopSynthesis();
            pendingSpeech = new PendingSpeech(boundedText, listenAfter);
            if (!synthesizerReady || synthesizer == null) {
                changeState(State.INITIALIZING);
                return;
            }
            playPendingSpeech();
        });
    }

    private void playPendingSpeech() {
        PendingSpeech speech = pendingSpeech;
        pendingSpeech = null;
        if (speech == null || synthesizer == null || !synthesizerReady || !started) {
            return;
        }

        listenAfterSpeech = speech.listenAfter;
        activeUtteranceId = "textureflow-turn-" + utteranceCounter.incrementAndGet();
        int result = synthesizer.speak(
                speech.text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                activeUtteranceId
        );
        if (result == TextToSpeech.SUCCESS) {
            changeState(State.SPEAKING);
        } else {
            activeUtteranceId = null;
            listenAfterSpeech = false;
            reportFailure(Failure.SPEECH_SYNTHESIS_FAILED, State.ERROR);
        }
    }

    private void beginListening(boolean delayedAfterSpeech) {
        if (!isOperational()) {
            return;
        }
        if (!hasRecordAudioPermission()) {
            reportFailure(Failure.RECORD_AUDIO_PERMISSION_REQUIRED, State.UNAVAILABLE);
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            reportFailure(Failure.RECOGNITION_UNAVAILABLE, State.UNAVAILABLE);
            return;
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext);
        }

        pendingSpeech = null;
        listenAfterSpeech = false;
        stopSynthesis();
        cancelRecognition();

        Runnable startRecognition = () -> {
            if (!isOperational() || recognizer == null) {
                return;
            }
            long generation = ++recognitionGeneration;
            recognizer.setRecognitionListener(new TurnRecognitionListener(generation));
            recognizerActive = true;
            try {
                recognizer.startListening(createRecognitionIntent());
                changeState(State.LISTENING);
                mainHandler.removeCallbacks(listenTimeout);
                mainHandler.postDelayed(listenTimeout, LISTEN_TIMEOUT_MILLIS);
            } catch (RuntimeException unavailable) {
                recognizerActive = false;
                reportFailure(Failure.RECOGNITION_FAILED, State.ERROR);
            }
        };

        if (delayedAfterSpeech) {
            mainHandler.postDelayed(startRecognition, INTERRUPT_SETTLE_MILLIS);
        } else {
            startRecognition.run();
        }
    }

    private Intent createRecognitionIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                COMPLETE_SILENCE_MILLIS);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                POSSIBLY_COMPLETE_SILENCE_MILLIS);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                MINIMUM_SPEECH_MILLIS);
        return intent;
    }

    private void handleSynthesizerInitialized(int status) {
        runOnMain(() -> {
            if (released || synthesizer == null) {
                return;
            }
            if (status != TextToSpeech.SUCCESS) {
                synthesizerReady = false;
                pendingSpeech = null;
                reportFailure(Failure.SPEECH_SYNTHESIS_UNAVAILABLE, State.ERROR);
                return;
            }

            int languageResult = synthesizer.setLanguage(Locale.getDefault());
            if (languageResult == TextToSpeech.LANG_MISSING_DATA
                    || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                synthesizerReady = false;
                pendingSpeech = null;
                reportFailure(Failure.SPEECH_SYNTHESIS_UNAVAILABLE, State.ERROR);
                return;
            }

            synthesizerReady = true;
            synthesizer.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    runOnMain(() -> {
                        if (isActiveUtterance(utteranceId)) {
                            changeState(State.SPEAKING);
                        }
                    });
                }

                @Override
                public void onDone(String utteranceId) {
                    runOnMain(() -> finishSynthesis(utteranceId, false));
                }

                @Override
                public void onError(String utteranceId) {
                    runOnMain(() -> finishSynthesis(utteranceId, true));
                }
            });

            if (pendingSpeech != null) {
                playPendingSpeech();
            } else if (started && state == State.INITIALIZING) {
                changeState(State.IDLE);
            }
        });
    }

    private void finishSynthesis(String utteranceId, boolean failed) {
        if (!isActiveUtterance(utteranceId)) {
            return;
        }
        activeUtteranceId = null;
        boolean shouldListen = listenAfterSpeech;
        listenAfterSpeech = false;
        if (failed) {
            reportFailure(Failure.SPEECH_SYNTHESIS_FAILED, State.ERROR);
            return;
        }
        if (shouldListen && started) {
            beginListening(true);
        } else if (started) {
            changeState(State.IDLE);
        }
    }

    private void handleListenTimeout() {
        if (!recognizerActive || recognizer == null) {
            return;
        }
        cancelRecognition();
        reportFailure(Failure.RECOGNITION_TIMEOUT, State.IDLE);
    }

    private void cancelRecognition() {
        mainHandler.removeCallbacks(listenTimeout);
        ++recognitionGeneration;
        recognizerActive = false;
        if (recognizer != null) {
            recognizer.cancel();
        }
    }

    private void stopSynthesis() {
        activeUtteranceId = null;
        if (synthesizer != null) {
            synthesizer.stop();
        }
    }

    private boolean isActiveRecognition(long generation) {
        return started && !released && recognizerActive && generation == recognitionGeneration;
    }

    private boolean isActiveUtterance(String utteranceId) {
        return started && !released && utteranceId != null && utteranceId.equals(activeUtteranceId);
    }

    private boolean isOperational() {
        return started && !released;
    }

    private boolean hasRecordAudioPermission() {
        return appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void reportFailure(Failure failure, State failureState) {
        changeState(failureState);
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onFailure(failure);
        }
    }

    private void changeState(State nextState) {
        if (state == nextState) {
            return;
        }
        state = nextState;
        Callback currentCallback = callback;
        if (currentCallback != null) {
            currentCallback.onStateChanged(nextState);
        }
    }

    private void dispatchPartial(String utterance) {
        Callback currentCallback = callback;
        if (currentCallback != null && !utterance.isEmpty()) {
            currentCallback.onPartialUtterance(utterance);
        }
    }

    private void dispatchFinal(String utterance) {
        Callback currentCallback = callback;
        if (currentCallback != null && !utterance.isEmpty()) {
            currentCallback.onFinalUtterance(utterance);
        }
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
        } else {
            mainHandler.post(action);
        }
    }

    private static String firstRecognition(Bundle results) {
        if (results == null) {
            return "";
        }
        ArrayList<String> matches = results.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            return "";
        }
        return boundText(matches.get(0), MAX_RECOGNIZED_CHARACTERS);
    }

    private static String boundText(String text, int limit) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= limit) {
            return trimmed;
        }
        return trimmed.substring(0, limit).trim();
    }

    private final class TurnRecognitionListener implements RecognitionListener {
        private final long generation;

        private TurnRecognitionListener(long generation) {
            this.generation = generation;
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
            if (isActiveRecognition(generation)) {
                changeState(State.LISTENING);
            }
        }

        @Override
        public void onBeginningOfSpeech() {
            // State remains LISTENING while audio is captured.
        }

        @Override
        public void onRmsChanged(float rmsdB) {
            // Audio amplitude is intentionally not retained.
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
            // Raw microphone audio is intentionally not retained.
        }

        @Override
        public void onEndOfSpeech() {
            if (isActiveRecognition(generation)) {
                mainHandler.removeCallbacks(listenTimeout);
                changeState(State.PROCESSING);
            }
        }

        @Override
        public void onError(int error) {
            if (!isActiveRecognition(generation)) {
                return;
            }
            recognizerActive = false;
            mainHandler.removeCallbacks(listenTimeout);
            if (error == SpeechRecognizer.ERROR_NO_MATCH
                    || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                reportFailure(Failure.RECOGNITION_TIMEOUT, State.IDLE);
            } else if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                reportFailure(Failure.RECOGNITION_BUSY, State.ERROR);
            } else if (error == SpeechRecognizer.ERROR_NETWORK
                    || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
                    || error == SpeechRecognizer.ERROR_SERVER) {
                reportFailure(Failure.RECOGNITION_NETWORK, State.ERROR);
            } else {
                reportFailure(Failure.RECOGNITION_FAILED, State.ERROR);
            }
        }

        @Override
        public void onResults(Bundle results) {
            if (!isActiveRecognition(generation)) {
                return;
            }
            recognizerActive = false;
            mainHandler.removeCallbacks(listenTimeout);
            String utterance = firstRecognition(results);
            changeState(State.IDLE);
            dispatchFinal(utterance);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            if (isActiveRecognition(generation)) {
                dispatchPartial(firstRecognition(partialResults));
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            // Vendor-specific events are not needed for bounded turn handling.
        }
    }

    private static final class PendingSpeech {
        private final String text;
        private final boolean listenAfter;

        private PendingSpeech(String text, boolean listenAfter) {
            this.text = text;
            this.listenAfter = listenAfter;
        }
    }
}
