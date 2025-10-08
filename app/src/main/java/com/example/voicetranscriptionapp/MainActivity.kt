package com.example.voicetranscriptionapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private lateinit var transcriptionService: TranscriptionService
    private lateinit var localWhisperService: LocalWhisperService
    private lateinit var ttsService: OpenAiTtsService
    private lateinit var androidTtsService: AndroidTtsService
    private var mediaPlayer: MediaPlayer? = null
    private var recordingStartTime: Long = 0
    private var useFastTts = true // Toggle between Android TTS (fast) and OpenAI TTS (quality)
    private var useLocalWhisper = true // Toggle between Local Whisper (offline) and Cloud Whisper (API)

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
        localWhisperService = LocalWhisperService(this)
        ttsService = OpenAiTtsService()
        androidTtsService = AndroidTtsService(this)
        
        // Initialize services
        androidTtsService.initialize {
            Log.d("MainActivity", "Android TTS ready")
        }
        
        // Initialize local Whisper in background
        lifecycleScope.launch {
            val initialized = localWhisperService.initialize()
            if (initialized) {
                Log.d("MainActivity", "Local Whisper ready")
                withContext(Dispatchers.Main) {
                    updateSttModeIndicator()
                }
            } else {
                Log.w("MainActivity", "Local Whisper not available, using cloud")
                useLocalWhisper = false
                withContext(Dispatchers.Main) {
                    updateSttModeIndicator()
                }
            }
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
        
        // TTS Mode toggle button
        updateTtsModeButton()
        updateSttModeIndicator()
        
        binding.recordButton.setOnLongClickListener {
            // Long press to toggle TTS mode
            useFastTts = !useFastTts
            updateTtsModeButton()
            val mode = if (useFastTts) "Fast Android TTS" else "Quality OpenAI TTS"
            Toast.makeText(this, "TTS: $mode", Toast.LENGTH_SHORT).show()
            true
        }
        
        // Double tap compare button to toggle STT mode
        var lastCompareClickTime = 0L
        binding.compareButton.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastCompareClickTime < 500) {
                // Double click detected - toggle STT mode
                if (localWhisperService.isReady()) {
                    useLocalWhisper = !useLocalWhisper
                    updateSttModeIndicator()
                    val mode = if (useLocalWhisper) "Local Whisper (Offline)" else "Cloud Whisper (API)"
                    Toast.makeText(this, "STT: $mode", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Local Whisper not available. Download model first.", Toast.LENGTH_LONG).show()
                }
            } else {
                // Single click - compare transcription
                compareTranscription()
            }
            lastCompareClickTime = currentTime
        }
        
        // Clear previous recordings on app start
        clearOldRecordings()
    }
    
    private fun updateTtsModeButton() {
        val emoji = if (useFastTts) "⚡" else "⭐"
        val currentText = binding.recordButton.text.toString()
        val baseText = currentText.replace("⚡ ", "").replace("⭐ ", "").replace("📱 ", "").replace("☁️ ", "")
        binding.recordButton.text = "$emoji $baseText"
    }
    
    private fun updateSttModeIndicator() {
        val sttEmoji = if (useLocalWhisper && localWhisperService.isReady()) "📱" else "☁️"
        val status = if (useLocalWhisper && localWhisperService.isReady()) {
            "STT: Local (Offline)"
        } else {
            "STT: Cloud (Online)"
        }
        binding.statusText.text = status
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
                val startTime = System.currentTimeMillis()
                
                val transcription = if (useLocalWhisper && localWhisperService.isReady()) {
                    // Use local Whisper (offline, faster)
                    binding.statusText.text = "Transcribing locally..."
                    withContext(Dispatchers.IO) {
                        localWhisperService.transcribe(audioFile, language = "hu")
                    }
                } else {
                    // Use cloud Whisper (OpenAI API)
                    binding.statusText.text = "Transcribing via cloud..."
                    withContext(Dispatchers.IO) {
                        transcriptionService.transcribeAudio(audioFile)
                    }
                }
                
                val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0
                
                withContext(Dispatchers.Main) {
                    if (transcription.isNotEmpty()) {
                        binding.transcriptionText.text = transcription
                        val mode = if (useLocalWhisper && localWhisperService.isReady()) "Local" else "Cloud"
                        binding.statusText.text = "Transcription completed ($mode, ${elapsedTime}s)"
                        
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
                // Create response text
                val responseText = "A következő mondatot értettem meg: $transcription"
                
                if (useFastTts) {
                    // Fast mode: Android TTS (instant)
                    binding.statusText.text = "Playing response (fast mode)..."
                    
                    withContext(Dispatchers.Main) {
                        androidTtsService.speak(responseText) {
                            binding.statusText.text = "Response completed"
                        }
                    }
                } else {
                    // Quality mode: OpenAI TTS
                    binding.statusText.text = "Generating speech response (quality mode)..."
                    
                    // Create output file for TTS audio
                    val ttsDir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "TtsAudio")
                    if (!ttsDir.exists()) {
                        ttsDir.mkdirs()
                    }
                    val ttsFile = File(ttsDir, "tts_response_${System.currentTimeMillis()}.mp3")
                    
                    // Generate TTS audio
                    val success = withContext(Dispatchers.IO) {
                        ttsService.textToSpeech(responseText, ttsFile)
                    }
                    
                    withContext(Dispatchers.Main) {
                        if (success && ttsFile.exists()) {
                            binding.statusText.text = "Playing response..."
                            playAudio(ttsFile)
                        } else {
                            binding.statusText.text = "Failed to generate speech"
                            Toast.makeText(this@MainActivity, "Failed to generate speech", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "TTS generation failed", e)
                withContext(Dispatchers.Main) {
                    binding.statusText.text = "Speech generation failed"
                    Toast.makeText(this@MainActivity, "Speech generation failed", Toast.LENGTH_SHORT).show()
                }
            }
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

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
        mediaPlayer?.release()
        androidTtsService.shutdown()
        localWhisperService.release()
    }
}
