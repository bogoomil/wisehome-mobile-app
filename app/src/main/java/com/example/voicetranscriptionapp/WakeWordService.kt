package com.example.voicetranscriptionapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineException
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback

/**
 * Foreground service that continuously listens for wake word
 * When wake word is detected, triggers transcription flow
 */
class WakeWordService : Service() {
    
    private var porcupineManager: PorcupineManager? = null
    private var isListening = false
    
    companion object {
        private const val TAG = "WakeWordService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "WakeWordServiceChannel"
        
        // Porcupine Access Key - Replace with your key from Picovoice Console
        // Sign up at: https://console.picovoice.ai/
        private const val ACCESS_KEY = "YOUR_PORCUPINE_ACCESS_KEY_HERE"
        
        // Available built-in wake words:
        // "alexa", "americano", "blueberry", "bumblebee", "computer",
        // "grapefruit", "grasshopper", "hey google", "hey siri", "jarvis",
        // "ok google", "picovoice", "porcupine", "terminator"
        
        const val ACTION_START_LISTENING = "START_LISTENING"
        const val ACTION_STOP_LISTENING = "STOP_LISTENING"
        const val ACTION_WAKE_WORD_DETECTED = "WAKE_WORD_DETECTED"
        
        fun startService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_START_LISTENING
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).apply {
                action = ACTION_STOP_LISTENING
            }
            context.stopService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> {
                startListening()
            }
            ACTION_STOP_LISTENING -> {
                stopListening()
                stopSelf()
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startListening() {
        if (isListening) {
            Log.d(TAG, "Already listening")
            return
        }
        
        try {
            // Start foreground service with notification
            startForeground(NOTIFICATION_ID, createNotification("Listening for wake word..."))
            
            // Initialize Porcupine
            val callback = PorcupineManagerCallback { keywordIndex ->
                onWakeWordDetected(keywordIndex)
            }
            
            // Built-in wake words - using "porcupine" as default
            // You can change this to: Porcupine.BuiltInKeyword.COMPUTER, 
            // Porcupine.BuiltInKeyword.JARVIS, etc.
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey(ACCESS_KEY)
                .setKeyword(Porcupine.BuiltInKeyword.PORCUPINE) // Change wake word here
                .build(applicationContext, callback)
            
            porcupineManager?.start()
            isListening = true
            
            Log.i(TAG, "Wake word detection started")
            updateNotification("🎤 Listening... Say 'Porcupine'")
            
        } catch (e: PorcupineException) {
            Log.e(TAG, "Failed to initialize Porcupine", e)
            
            // Show error notification
            updateNotification("❌ Error: ${e.message}")
            
            // If access key is invalid, provide guidance
            if (e.message?.contains("AccessKey", ignoreCase = true) == true) {
                Log.e(TAG, "Invalid Porcupine Access Key!")
                Log.e(TAG, "Get your free key at: https://console.picovoice.ai/")
            }
        }
    }
    
    private fun stopListening() {
        if (!isListening) return
        
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
            porcupineManager = null
            isListening = false
            
            Log.i(TAG, "Wake word detection stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Porcupine", e)
        }
    }
    
    private fun onWakeWordDetected(keywordIndex: Int) {
        Log.i(TAG, "🎯 Wake word detected! Index: $keywordIndex")
        
        // Update notification
        updateNotification("✅ Wake word detected! Processing...")
        
        // Send broadcast to MainActivity
        val intent = Intent(ACTION_WAKE_WORD_DETECTED)
        sendBroadcast(intent)
        
        // Play feedback sound (optional)
        // mediaPlayer.start()
        
        // After a short delay, return to listening state
        android.os.Handler(mainLooper).postDelayed({
            if (isListening) {
                updateNotification("🎤 Listening... Say 'Porcupine'")
            }
        }, 3000)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous wake word monitoring"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Stop action
        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP_LISTENING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Voice Assistant Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        Log.d(TAG, "Service destroyed")
    }
}

