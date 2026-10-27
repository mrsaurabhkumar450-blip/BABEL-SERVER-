package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class SpeechAndTtsManager(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onListeningStateChanged: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null
    var isListening = false
        private set
    var isSpeaking = false
        private set

    init {
        tts = TextToSpeech(context, this)
        initSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            // Jarvis crisp voice tuning: slightly raised pitch and swift cadence
            tts?.setPitch(1.04f)
            tts?.setSpeechRate(1.05f)

            // Try Hindi or fallback English
            val hindi = Locale("hi", "IN")
            val available = tts?.isLanguageAvailable(hindi)
            if (available == TextToSpeech.LANG_AVAILABLE || available == TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                tts?.language = hindi
            } else {
                tts?.language = Locale.US
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    isSpeaking = true
                }

                override fun onDone(utteranceId: String?) {
                    isSpeaking = false
                }

                override fun onError(utteranceId: String?) {
                    isSpeaking = false
                }
            })
            Log.d("JARVIS_TTS", "TTS Initialized Successfully")
        } else {
            Log.e("JARVIS_TTS", "TTS Initialization failed: $status")
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        onListeningStateChanged(true)
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                        onListeningStateChanged(false)
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        onListeningStateChanged(false)
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_NETWORK -> "Network required for speech recognition."
                            else -> "Voice input error: code $error"
                        }
                        onError(msg)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        onListeningStateChanged(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val voiceText = matches?.firstOrNull()
                        if (!voiceText.isNullOrBlank()) {
                            onSpeechRecognized(voiceText)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    fun startListening(languageLocale: String = "hi-IN") {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition service is not available on this device.")
            return
        }

        // Stop TTS if speaking
        stopSpeaking()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageLocale)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "JARVIS is listening... Speak your command")
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("JARVIS_STT", "Failed to start speech recognition", e)
            onError("Failed to start voice listener: ${e.localizedMessage}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
            isListening = false
            onListeningStateChanged(false)
        } catch (e: Exception) {
            Log.e("JARVIS_STT", "Error stopping listening", e)
        }
    }

    fun speak(text: String, preferHindi: Boolean = false) {
        if (!isTtsReady || text.isBlank()) return
        try {
            // Clean markdown syntax (asterisks, hashtags) for natural spoken output
            val cleanSpeech = text
                .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
                .replace(Regex("\\*([^*]+)\\*"), "$1")
                .replace(Regex("#+\\s*"), "")
                .replace(Regex("`[^`]+`"), "")
                .trim()

            // If prompt contains Devanagari or user prefers Hindi
            val containsHindi = cleanSpeech.any { it in '\u0900'..'\u097F' } || preferHindi
            if (containsHindi) {
                tts?.language = Locale("hi", "IN")
            } else {
                tts?.language = Locale.US
            }

            tts?.speak(cleanSpeech, TextToSpeech.QUEUE_FLUSH, null, "jarvis_speech_utt_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e("JARVIS_TTS", "Error during speak", e)
        }
    }

    fun stopSpeaking() {
        try {
            if (tts?.isSpeaking == true) {
                tts?.stop()
                isSpeaking = false
            }
        } catch (e: Exception) {
            Log.e("JARVIS_TTS", "Error stopping speech", e)
        }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("JARVIS_VOICE", "Error cleaning up voice manager", e)
        }
    }
}
