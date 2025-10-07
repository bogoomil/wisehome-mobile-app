package com.example.voicetranscriptionapp

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class OpenAiTtsService {
    private val client = OkHttpClient()
    // TODO: Add your OpenAI API key here or pass it via constructor
    private val apiKey = "YOUR_OPENAI_API_KEY_HERE"
    private val apiUrl = "https://api.openai.com/v1/audio/speech"

    suspend fun textToSpeech(text: String, outputFile: File): Boolean {
        return try {
            val jsonBody = JSONObject().apply {
                put("model", "tts-1")  // Use tts-1 for faster response, tts-1-hd for higher quality
                put("input", text)
                put("voice", "nova")  // Options: alloy, echo, fable, onyx, nova, shimmer
                put("response_format", "mp3")
                put("speed", 1.0)
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            Log.d("OpenAiTtsService", "Sending TTS request to OpenAI API")
            Log.d("OpenAiTtsService", "Text: $text")
            Log.d("OpenAiTtsService", "Output file: ${outputFile.absolutePath}")

            val response = client.newCall(request).execute()

            if (response.isSuccessful) {
                response.body?.byteStream()?.use { inputStream ->
                    outputFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                Log.d("OpenAiTtsService", "TTS audio saved successfully: ${outputFile.length()} bytes")
                true
            } else {
                Log.e("OpenAiTtsService", "API request failed: ${response.code} - ${response.message}")
                Log.e("OpenAiTtsService", "Response body: ${response.body?.string()}")
                false
            }
        } catch (e: IOException) {
            Log.e("OpenAiTtsService", "Network error", e)
            false
        } catch (e: Exception) {
            Log.e("OpenAiTtsService", "Unexpected error", e)
            false
        }
    }
}

