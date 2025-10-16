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
    
    // Previous command context for multi-turn conversations
    private var previousCommand: Map<String, String>? = null
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
        binding.recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }
        
        binding.compareButton.setOnClickListener {
            compareTranscription()
        }
        
        binding.clearCacheButton.setOnClickListener {
            clearAllRecordings()
        }
        
        // Long press Record button to toggle Wake Word mode
        binding.recordButton.setOnLongClickListener {
            toggleWakeWordMode()
            true
        }
        
        // TTS Mode toggle - double tap Compare button
        updateTtsModeButton()
        
        // Clear previous recordings on app start
        clearOldRecordings()
    }
    
    private fun updateTtsModeButton() {
        val emoji = if (useFastTts) "⚡" else "⭐"
        val currentText = binding.recordButton.text.toString()
        val baseText = currentText.replace("⚡ ", "").replace("⭐ ", "")
        binding.recordButton.text = "$emoji $baseText"
    }
    
    private fun compareTranscription() {
        val expectedText = binding.expectedTextInput.text.toString().trim()
        val actualText = binding.transcriptionText.text.toString().trim()
        
        if (expectedText.isNotEmpty() && actualText.isNotEmpty()) {
            val similarity = calculateSimilarity(expectedText, actualText)
            val accuracy = (similarity * 100).toInt()
            
            binding.statusText.text = "Accuracy: $accuracy%"
            
            if (accuracy < 70) {
                Toast.makeText(this, "Low accuracy - try speaking more clearly", Toast.LENGTH_LONG).show()
            } else if (accuracy < 90) {
                Toast.makeText(this, "Good accuracy", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Excellent accuracy!", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Please enter expected text and get transcription first", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1
        
        if (longer.isEmpty()) return 1.0
        
        val editDistance = levenshteinDistance(longer.lowercase(), shorter.lowercase())
        return (longer.length - editDistance).toDouble() / longer.length
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1)
        for (i in 0..s1.length) {
            var lastValue = i
            for (j in 0..s2.length) {
                if (i == 0) {
                    costs[j] = j
                } else if (j > 0) {
                    var newValue = costs[j - 1]
                    if (s1[i - 1] != s2[j - 1]) {
                        newValue = minOf(newValue, lastValue, costs[j]) + 1
                    }
                    costs[j - 1] = lastValue
                    lastValue = newValue
                }
            }
            if (i > 0) costs[s2.length] = lastValue
        }
        return costs[s2.length]
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
    
    private fun clearAllRecordings() {
        try {
            val audioDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "VoiceRecordings")
            if (audioDir.exists()) {
                val files = audioDir.listFiles()
                var deletedCount = 0
                files?.forEach { file ->
                    if (file.isFile && file.name.startsWith("recording_")) {
                        if (file.delete()) {
                            deletedCount++
                            Log.d("MainActivity", "Deleted recording: ${file.name}")
                        }
                    }
                }
                Toast.makeText(this, "Deleted $deletedCount recordings", Toast.LENGTH_SHORT).show()
                binding.statusText.text = "Cache cleared: $deletedCount files deleted"
            } else {
                Toast.makeText(this, "No recordings found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error clearing all recordings", e)
            Toast.makeText(this, "Error clearing cache", Toast.LENGTH_SHORT).show()
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
        binding.statusText.text = "Ready to record"
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
                    binding.statusText.text = getString(R.string.recording_status)
                    binding.transcriptionText.text = ""
                    
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
            binding.statusText.text = getString(R.string.processing_status)
            
            // Process the recorded audio
            audioFile?.let { file ->
                if (file.exists() && file.length() > 0) {
                    val fileSizeKB = file.length() / 1024
                    val fullPath = file.absolutePath
                    val recordingDuration = (System.currentTimeMillis() - recordingStartTime) / 1000.0
                    val expectedSizeKB = (recordingDuration * 32) / 8  // 32 kbps for optimized AAC
                    
                    Log.d("MainActivity", "Audio file full path: $fullPath")
                    Log.d("MainActivity", "Audio file size: ${fileSizeKB}KB")
                    Log.d("MainActivity", "Recording duration: ${recordingDuration}s")
                    Log.d("MainActivity", "Expected size: ${expectedSizeKB.toInt()}KB")
                    Log.d("MainActivity", "File last modified: ${file.lastModified()}")
                    
                    val sizeRatio = if (expectedSizeKB > 0) (fileSizeKB / expectedSizeKB * 100).toInt() else 0
                    binding.statusText.text = "Audio: ${fileSizeKB}KB (${recordingDuration}s)\nExpected: ${expectedSizeKB.toInt()}KB\nRatio: ${sizeRatio}%\nPath: ${file.name}"
                    
                    if (sizeRatio < 50) {
                        Toast.makeText(this, "Warning: File size seems too small!", Toast.LENGTH_LONG).show()
                    }
                    
                    transcribeAudio(file)
                } else {
                    Toast.makeText(this, "No audio recorded", Toast.LENGTH_SHORT).show()
                    binding.statusText.text = "No audio recorded"
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
                        binding.transcriptionText.text = transcription
                        binding.statusText.text = "Transcription completed"
                        
                        // Generate TTS response
                        generateAndPlayTtsResponse(transcription)
                    } else {
                        binding.transcriptionText.text = "No transcription available"
                        binding.statusText.text = "Transcription failed"
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Transcription failed", e)
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "Transcription failed"
                    Toast.makeText(this@MainActivity, getString(R.string.error_transcription), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun generateAndPlayTtsResponse(transcription: String) {
        lifecycleScope.launch {
            try {
                // Send transcription to OpenAI Workflow with previous context
                binding.statusText.text = "AI elemzés folyamatban..."
                
                val workflowResponse = withContext(Dispatchers.IO) {
                    workflowService.sendMessageToWorkflow(transcription, previousCommand)
                }
                
                Log.d("MainActivity", "AI JSON response: $workflowResponse")
                
                if (workflowResponse.isEmpty() || workflowResponse.startsWith("Hiba")) {
                    withContext(Dispatchers.Main) {
                        binding.statusText.text = "AI hiba: $workflowResponse"
                        Toast.makeText(this@MainActivity, workflowResponse, Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                
                // Parse full server response (including has_missing_info)
                val (humanResponse, jsonData, hasMissing) = parseFullServerResponse(workflowResponse)
                
                // Update UI with parsed data
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "Command: ${jsonData["room"]} - ${jsonData["device"]} - ${jsonData["command"]}"
                }
                
                // Handle missing information
                if (hasMissing) {
                    // Store current command as context for next request
                    previousCommand = jsonData.filterValues { it.isNotEmpty() }
                    hasMissingInfo = true
                    
                    Log.d("MainActivity", "Missing info detected. Stored context: $previousCommand")
                } else {
                    // Command complete, reset context
                    previousCommand = null
                    hasMissingInfo = false
                }
                
                Log.d("MainActivity", "Human response: $humanResponse")
                
                // Now read the human-friendly response using TTS
                if (useFastTts) {
                    // Fast mode: Android TTS (instant)
                    binding.statusText.text = "Válasz lejátszása (gyors mód)..."
                    
                    withContext(Dispatchers.Main) {
                        androidTtsService.speak(humanResponse) {
                            binding.statusText.text = "Command executed: ${jsonData["room"]} - ${jsonData["device"]}"
                        }
                    }
                } else {
                    // Quality mode: OpenAI TTS
                    binding.statusText.text = "Beszéd generálása (minőségi mód)..."
                    
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
                            binding.statusText.text = "Válasz lejátszása..."
                            playAudio(ttsFile)
                        } else {
                            binding.statusText.text = "Beszéd generálás sikertelen"
                            Toast.makeText(this@MainActivity, "Beszéd generálás sikertelen", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Workflow vagy TTS hiba", e)
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "Hiba: ${e.message}"
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
            
            Log.d("MainActivity", "Parsed - Room: $room, Device: $device, Command: $command, Missing: $hasMissingInfo")
            
            // Create human-friendly response
            val humanResponse = if (hasMissingInfo) {
                // Build question about missing information
                val missingArray = result.optJSONArray("missing_information")
                val missingFields = mutableListOf<String>()
                
                if (missingArray != null) {
                    for (i in 0 until missingArray.length()) {
                        val item = missingArray.getJSONObject(i)
                        val fieldName = item.optString("fieldName", "")
                        missingFields.add(fieldName)
                    }
                }
                
                when {
                    missingFields.contains("room") && missingFields.contains("device") -> 
                        "Which room and which device?"
                    missingFields.contains("room") -> 
                        "Which room?"
                    missingFields.contains("device") -> 
                        "Which device?"
                    else -> 
                        "Please provide more details."
                }
            } else {
                // Simple property-value format
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
                    binding.statusText.text = "Playback completed"
                    release()
                    mediaPlayer = null
                    
                    // Clean up old TTS files
                    cleanupOldTtsFiles()
                }
                setOnErrorListener { mp, what, extra ->
                    Log.e("MainActivity", "MediaPlayer error: what=$what, extra=$extra")
                    binding.statusText.text = "Playback error"
                    release()
                    mediaPlayer = null
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error playing audio", e)
            binding.statusText.text = "Playback failed"
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
        
        binding.recordButton.text = "🎤 Listening for 'Hello Al'"
        binding.recordButton.setBackgroundColor(getColor(android.R.color.holo_green_light))
        binding.statusText.text = "Wake word mode active. Say 'Hello Al' to start recording."
        
        Toast.makeText(this, "Wake Word Mode Started\nSay 'Hello Al' to trigger recording", Toast.LENGTH_LONG).show()
        Log.i("MainActivity", "Wake word listening started")
    }
    
    private fun stopWakeWordListening() {
        WakeWordService.stopService(this)
        isWakeWordListening = false
        
        binding.recordButton.text = getString(R.string.record_button)
        binding.recordButton.setBackgroundColor(getColor(R.color.record_button))
        binding.statusText.text = "Wake word mode stopped. Press button to record manually."
        
        Toast.makeText(this, "Wake Word Mode Stopped", Toast.LENGTH_SHORT).show()
        Log.i("MainActivity", "Wake word listening stopped")
    }
    
    private fun onWakeWordDetected() {
        Log.i("MainActivity", "🎯 Wake word detected in MainActivity!")
        
        // Visual feedback
        runOnUiThread {
            binding.statusText.text = "✅ Wake word detected! Starting auto-recording..."
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
                        binding.statusText.text = "🎤 Listening for 'Hello Al'..."
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
