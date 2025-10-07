package com.example.voicetranscriptionapp

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidTtsService(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var isReady = false
    
    fun initialize(onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    // Set Hungarian language
                    val result = engine.setLanguage(Locale("hu", "HU"))
                    
                    if (result == TextToSpeech.LANG_MISSING_DATA || 
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("AndroidTtsService", "Hungarian language not supported, falling back to default")
                        // Try to use any available language
                        engine.setLanguage(Locale.getDefault())
                    }
                    
                    // Optimize settings
                    engine.setSpeechRate(1.0f) // Normal speed
                    engine.setPitch(1.0f) // Normal pitch
                    
                    isReady = true
                    Log.d("AndroidTtsService", "TTS initialized successfully")
                    onReady()
                }
            } else {
                Log.e("AndroidTtsService", "TTS initialization failed")
            }
        }
    }
    
    fun isInitialized(): Boolean = isReady
    
    /**
     * Speak text immediately
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isReady) {
            Log.w("AndroidTtsService", "TTS not ready yet")
            onComplete?.invoke()
            return
        }
        
        tts?.let { engine ->
            if (onComplete != null) {
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d("AndroidTtsService", "Speaking started: $utteranceId")
                    }
                    
                    override fun onDone(utteranceId: String?) {
                        Log.d("AndroidTtsService", "Speaking completed: $utteranceId")
                        onComplete()
                    }
                    
                    override fun onError(utteranceId: String?) {
                        Log.e("AndroidTtsService", "Speaking error: $utteranceId")
                        onComplete()
                    }
                })
            }
            
            val utteranceId = "tts_${System.currentTimeMillis()}"
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }
    
    /**
     * Suspending version for coroutines
     */
    suspend fun speakSuspend(text: String): Boolean = suspendCancellableCoroutine { continuation ->
        if (!isReady) {
            Log.w("AndroidTtsService", "TTS not ready")
            continuation.resume(false)
            return@suspendCancellableCoroutine
        }
        
        tts?.let { engine ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                
                override fun onDone(utteranceId: String?) {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }
                
                override fun onError(utteranceId: String?) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            })
            
            val utteranceId = "tts_${System.currentTimeMillis()}"
            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            
            if (result == TextToSpeech.ERROR && continuation.isActive) {
                continuation.resume(false)
            }
        } ?: continuation.resume(false)
    }
    
    /**
     * Stop current speech
     */
    fun stop() {
        tts?.stop()
    }
    
    /**
     * Check if TTS is currently speaking
     */
    fun isSpeaking(): Boolean {
        return tts?.isSpeaking ?: false
    }
    
    /**
     * Release resources
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        Log.d("AndroidTtsService", "TTS shutdown")
    }
}

