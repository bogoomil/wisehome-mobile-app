package com.example.voicetranscriptionapp

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.voicetranscriptionapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private lateinit var transcriptionService: TranscriptionService
    private lateinit var ttsService: OpenAiTtsService
    private lateinit var androidTtsService: AndroidTtsService
    private lateinit var workflowService: OpenAiWorkflowService
    private var mediaPlayer: MediaPlayer? = null
    private var recordingStartTime: Long = 0
    private var useFastTts = true // Toggle between Android TTS (fast) and OpenAI TTS (quality)
    private var isWakeWordListening = false
    private var isAutoRecording = false
    
    // Conversation history for multi-turn conversations
    private val conversationHistory = mutableListOf<String>()
    private var hasMissingInfo = false
    
    // BroadcastReceiver for wake word detection
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WakeWordService.ACTION_WAKE_WORD_DETECTED -> {
                    onWakeWordDetected()
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            setupRecording()
        } else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transcriptionService = TranscriptionService()
        ttsService = OpenAiTtsService()
        androidTtsService = AndroidTtsService(this)
        workflowService = OpenAiWorkflowService()
        
        // Initialize Android TTS early for faster first use
        androidTtsService.initialize {
            Log.d("MainActivity", "Android TTS ready")
        }
        
        // Register wake word broadcast receiver
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(wakeWordReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(wakeWordReceiver, filter)
        }

        setupUI()
        checkPermission()
    }

    private fun setupUI() {
        // Single tap: record/stop, Double tap: toggle TTS mode
        var lastTapTime = 0L
        binding.recordButton.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 300) {
                // Double tap detected - toggle TTS mode
                toggleTtsMode()
            } else {
                // Single tap - record/stop
                if (isRecording) {
                    stopRecording()
                } else {
                    startRecording()
                }
            }
            lastTapTime = currentTime
        }
        
        binding.resetConversationButton.setOnClickListener {
            resetConversationHistory()
        }
        
        // Long press Record button to toggle Wake Word mode
        binding.recordButton.setOnLongClickListener {
            toggleWakeWordMode()
            true
        }
        
        // Clear previous recordings on app start
        clearOldRecordings()
    }
    
    private fun toggleTtsMode() {
        useFastTts = !useFastTts
        val mode = if (useFastTts) "⚡ Gyors mód" else "⭐ Minőségi mód"
        Toast.makeText(this, "TTS: $mode", Toast.LENGTH_SHORT).show()
        Log.d("MainActivity", "TTS mode toggled: useFastTts=$useFastTts")
    }
    
    private fun appendToConversation(text: String, isRequest: Boolean = false) {
        val currentText = binding.conversationText.text.toString()
        val prefix = if (isRequest) "\n\n>>> KÉRÉS:\n" else "\n\n<<< VÁLASZ:\n"
        val formattedText = if (currentText == getString(R.string.conversation_placeholder)) {
            (if (isRequest) ">>> KÉRÉS:\n" else "<<< VÁLASZ:\n") + text
        } else {
            currentText + prefix + text
        }
        binding.conversationText.text = formattedText
        
        // Auto-scroll to bottom
        binding.conversationScrollView.post {
            binding.conversationScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
    
    private fun clearOldRecordings() {
        try {
            val audioDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "VoiceRecordings")
            if (audioDir.exists()) {
                val files = audioDir.listFiles()
                files?.forEach { file ->
                    if (file.isFile && file.name.startsWith("recording_")) {
                        val fileAge = System.currentTimeMillis() - file.lastModified()
                        // Delete files older than 1 hour
                        if (fileAge > 3600000) {
                            file.delete()
                            Log.d("MainActivity", "Deleted old recording: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error clearing old recordings", e)
        }
    }
    
    private fun resetConversationHistory() {
        try {
            val previousSize = conversationHistory.size
            conversationHistory.clear()
            hasMissingInfo = false
            
            // Clear the conversation text
            binding.conversationText.text = getString(R.string.conversation_placeholder)
            
            Log.d("MainActivity", "Conversation history reset. Cleared $previousSize messages.")
            Toast.makeText(this, "Előzmények törölve ($previousSize üzenet)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error resetting conversation history", e)
            Toast.makeText(this, "Hiba az előzmények törlésekor", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                setupRecording()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun setupRecording() {
        binding.recordButton.isEnabled = true
    }

    private fun startRecording() {
        try {
            // Create audio file
            val audioDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "VoiceRecordings")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            val randomSuffix = (1000..9999).random()
            audioFile = File(audioDir, "recording_${timestamp}_${randomSuffix}.m4a")
            
            // Setup MediaRecorder with optimized settings for Whisper
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)  // MP4 format
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)  // AAC encoder
                setAudioSamplingRate(16000)  // 16kHz - Whisper optimal sampling rate
                setAudioEncodingBitRate(32000)  // 32 kbps - smaller file, faster upload
                setAudioChannels(1)  // Mono
                setOutputFile(audioFile!!.absolutePath)
                
                try {
                    prepare()
                    start()
                    
                    isRecording = true
                    recordingStartTime = System.currentTimeMillis()
                    binding.recordButton.text = getString(R.string.stop_recording)
                    binding.recordButton.setBackgroundColor(getColor(R.color.stop_button))
                    
                    Log.d("MainActivity", "Recording started at: $recordingStartTime")
                    
                } catch (e: IOException) {
                    Log.e("MainActivity", "prepare() failed", e)
                    Toast.makeText(this@MainActivity, getString(R.string.error_recording), Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "startRecording failed", e)
            Toast.makeText(this, getString(R.string.error_recording), Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            isRecording = false
            binding.recordButton.text = getString(R.string.record_button)
            binding.recordButton.setBackgroundColor(getColor(R.color.record_button))
            
            // Process the recorded audio
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    val fileSizeKB = file.length() / 1024
                    val fullPath = file.absolutePath
                    val recordingDuration = (System.currentTimeMillis() - recordingStartTime) / 1000.0
                    
                    Log.d("MainActivity", "Audio file: $fullPath, size: ${fileSizeKB}KB, duration: ${recordingDuration}s")
                    
                    transcribeAudio(file)
                } else {
                    Toast.makeText(this, "Nem sikerült a felvétel", Toast.LENGTH_SHORT).show()
                }
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "stopRecording failed", e)
            Toast.makeText(this, "Error stopping recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun transcribeAudio(audioFile: File) {
        lifecycleScope.launch {
            try {
                val transcription = withContext(Dispatchers.IO) {
                    transcriptionService.transcribeAudio(audioFile)
                }
                
                withContext(Dispatchers.Main) {
                    if (transcription.isNotEmpty()) {
                        // Show transcription in conversation
                        appendToConversation(transcription, isRequest = true)
                        
                        // Generate TTS response
                        generateAndPlayTtsResponse(transcription)
                    } else {
                        Toast.makeText(this@MainActivity, "Nem sikerült az átírás", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Transcription failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.error_transcription), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun generateAndPlayTtsResponse(transcription: String) {
        lifecycleScope.launch {
            try {
                // Send transcription to OpenAI Workflow with conversation history
                val workflowResponse = withContext(Dispatchers.IO) {
                    workflowService.sendMessageToWorkflow(transcription, conversationHistory)
                }
                
                Log.d("MainActivity", "AI JSON response: $workflowResponse")
                
                if (workflowResponse.isEmpty() || workflowResponse.startsWith("Hiba")) {
                    withContext(Dispatchers.Main) {
                        appendToConversation("HIBA: $workflowResponse", isRequest = false)
                        Toast.makeText(this@MainActivity, workflowResponse, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                // Parse full server response (including has_missing_info)
                val (humanResponse, jsonData, hasMissing) = parseFullServerResponse(workflowResponse)
                
                // Display JSON response in conversation
                withContext(Dispatchers.Main) {
                    val formattedJson = try {
                        JSONObject(workflowResponse).toString(2)
                    } catch (e: Exception) {
                        workflowResponse
                    }
                    appendToConversation(formattedJson, isRequest = false)
                }
                
                // Handle missing information
                if (hasMissing) {
                    // Add current transcription to conversation history
                    conversationHistory.add(transcription)
                    hasMissingInfo = true
                    
                    Log.d("MainActivity", "Missing info detected. Added to history: $transcription")
                    Log.d("MainActivity", "Conversation history: $conversationHistory")
                } else {
                    // Command complete, clear conversation history for next command
                    Log.d("MainActivity", "Command complete. Clearing conversation history.")
                    conversationHistory.clear()
                    hasMissingInfo = false
                }
                
                Log.d("MainActivity", "Human response: $humanResponse")
                
                // Now read the human-friendly response using TTS
                if (useFastTts) {
                    // Fast mode: Android TTS (instant)
                    withContext(Dispatchers.Main) {
                        androidTtsService.speak(humanResponse) {}
                    }
                } else {
                    // Quality mode: OpenAI TTS
                    // Create output file for TTS audio
                    val ttsDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "TtsAudio")
                    if (!ttsDir.exists()) {
                        ttsDir.mkdirs()
                    }
                    val ttsFile = File(ttsDir, "tts_response_${System.currentTimeMillis()}.mp3")
                    
                    // Generate TTS audio
                    val success = withContext(Dispatchers.IO) {
                        ttsService.textToSpeech(humanResponse, ttsFile)
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (success && ttsFile.exists()) {
                            playAudio(ttsFile)
                        } else {
                            Toast.makeText(this@MainActivity, "Beszéd generálás sikertelen", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Workflow vagy TTS hiba", e)
                withContext(Dispatchers.Main) {
                    appendToConversation("HIBA: ${e.message}", isRequest = false)
                    Toast.makeText(this@MainActivity, "Hiba történt: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun parseFullServerResponse(serverResponse: String): Triple<String, Map<String, String>, Boolean> {
        return try {
            val fullResponse = JSONObject(serverResponse)
            val success = fullResponse.optBoolean("success", false)
            val hasMissingInfo = fullResponse.optBoolean("has_missing_info", false)
            
            if (!success) {
                return Triple("Hiba történt a feldolgozás során.", emptyMap(), false)
            }
            
            val result = fullResponse.optJSONObject("result")
            if (result == null) {
                return Triple("Nem sikerült értelmezni a választ.", emptyMap(), false)
            }
            
            val room = result.optString("room", "")
            val device = result.optString("device", "")
            val command = result.optString("command", "")
            val missingInformation = result.optString("missing_information", "")
            val resultSummary = result.optString("result", "")
            
            Log.d("MainActivity", "Parsed - Room: $room, Device: $device, Command: $command")
            Log.d("MainActivity", "Missing info: $missingInformation, Result: $resultSummary")
            
            // Choose what to read based on missing_information
            val humanResponse = if (missingInformation.isNotEmpty()) {
                // Read the missing information question
                missingInformation
            } else if (resultSummary.isNotEmpty()) {
                // Read the result summary
                resultSummary
            } else {
                // Fallback to property-value format
                "Room: $room. Device: $device. Command: $command."
            }
            
            val dataMap = mapOf(
                "room" to room,
                "device" to device,
                "command" to command
            )
            
            Triple(humanResponse, dataMap, hasMissingInfo)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error parsing server response", e)
            Triple("Nem sikerült értelmezni a választ.", emptyMap(), false)
        }
    }
    
    private fun parseJsonResponse(jsonResponse: String): Pair<String, Map<String, String>> {
        return try {
            val json = JSONObject(jsonResponse)
            // A szerver angol mezőneveket küld
            val room = json.optString("room", "unknown")
            val device = json.optString("device", "unknown")
            val command = json.optString("command", "unknown")
            
            Log.d("MainActivity", "Parsed - Room: $room, Device: $device, Command: $command")
            
            // Simple property-value format response
            val humanResponse = "Room: $room. Device: $device. Command: $command."
            
            val dataMap = mapOf(
                "room" to room,
                "device" to device,
                "command" to command
            )
            
            Pair(humanResponse, dataMap)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error parsing JSON response", e)
            Pair("Nem sikerült értelmezni a választ.", mapOf(
                "room" to "error",
                "device" to "error",
                "command" to "error"
            ))
        }
    }
    
    private fun playAudio(audioFile: File) {
        try {
            // Release previous player if exists
            mediaPlayer?.release()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnPreparedListener {
                    start()
                    Log.d("MainActivity", "Playing TTS audio: ${audioFile.absolutePath}")
                }
                setOnCompletionListener {
                    release()
                    mediaPlayer = null
                    
                    // Clean up old TTS files
                    cleanupOldTtsFiles()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e("MainActivity", "MediaPlayer error: what=$what, extra=$extra")
                    release()
                    mediaPlayer = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing audio", e)
            Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun cleanupOldTtsFiles() {
        try {
            val ttsDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "TtsAudio")
            if (ttsDir.exists()) {
                val files = ttsDir.listFiles()
                files?.forEach { file ->
                    if (file.isFile && file.name.startsWith("tts_response_")) {
                        val fileAge = System.currentTimeMillis() - file.lastModified()
                        // Delete files older than 10 minutes
                        if (fileAge > 600000) {
                            file.delete()
                            Log.d("MainActivity", "Deleted old TTS file: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error cleaning up TTS files", e)
        }
    }

    private fun toggleWakeWordMode() {
        if (isWakeWordListening) {
            stopWakeWordListening()
        } else {
            startWakeWordListening()
        }
    }
    
    private fun startWakeWordListening() {
        // Check notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Please grant notification permission first", Toast.LENGTH_LONG).show()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
                return
            }
        }
        
        WakeWordService.startService(this)
        isWakeWordListening = true
        
        binding.recordButton.text = "🎤 'Hello Al'"
        binding.recordButton.setBackgroundColor(getColor(android.R.color.holo_green_light))
        
        Toast.makeText(this, "Wake Word Mode Started\nSay 'Hello Al' to trigger recording", Toast.LENGTH_LONG).show()
        Log.i("MainActivity", "Wake word listening started")
    }
    
    private fun stopWakeWordListening() {
        WakeWordService.stopService(this)
        isWakeWordListening = false
        
        binding.recordButton.text = getString(R.string.record_button)
        binding.recordButton.setBackgroundColor(getColor(R.color.record_button))
        
        Toast.makeText(this, "Wake Word Mode Stopped", Toast.LENGTH_SHORT).show()
        Log.i("MainActivity", "Wake word listening stopped")
    }
    
    private fun onWakeWordDetected() {
        Log.i("MainActivity", "🎯 Wake word detected in MainActivity!")
        
        // Visual feedback
        runOnUiThread {
            binding.recordButton.setBackgroundColor(getColor(android.R.color.holo_orange_dark))
            
            // Haptic feedback
            @Suppress("DEPRECATION")
            binding.recordButton.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
        
        // Start auto-recording
        startAutoRecording()
    }
    
    private fun startAutoRecording() {
        if (isAutoRecording || isRecording) {
            Log.w("MainActivity", "Already recording, ignoring wake word")
            return
        }
        
        isAutoRecording = true
        
        lifecycleScope.launch {
            try {
                // Start recording
                withContext(Dispatchers.Main) {
                    startRecording()
                }
                
                // Auto-stop after 5 seconds
                delay(5000)
                
                withContext(Dispatchers.Main) {
                    if (isRecording) {
                        stopRecording()
                    }
                }
                
                // Wait a bit before allowing next wake word detection
                delay(1000)
                
                isAutoRecording = false
                
                // Return to listening mode
                withContext(Dispatchers.Main) {
                    if (isWakeWordListening) {
                        binding.recordButton.setBackgroundColor(getColor(android.R.color.holo_green_light))
                        binding.recordButton.text = "🎤 'Hello Al'"
                    }
                }
                
            } catch (e: Exception) {
                Log.e("MainActivity", "Auto-recording failed", e)
                isAutoRecording = false
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        try {
            unregisterReceiver(wakeWordReceiver)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error unregistering receiver", e)
        }
        
        // Stop wake word service if running
        if (isWakeWordListening) {
            WakeWordService.stopService(this)
        }
        
        mediaRecorder?.release()
        mediaPlayer?.release()
        androidTtsService.shutdown()
    }
}
