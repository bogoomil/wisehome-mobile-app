package com.example.voicetranscriptionapp

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

class TranscriptionService {
    private val client = OkHttpClient()
    // TODO: Add your OpenAI API key here or pass it via constructor
    private val apiKey = "YOUR_OPENAI_API_KEY_HERE"
    private val apiUrl = "https://api.openai.com/v1/audio/transcriptions"

    suspend fun transcribeAudio(audioFile: File): String {
        return try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/m4a".toMediaType())
                )
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", "hu")
                .addFormDataPart("response_format", "json")
                .addFormDataPart("temperature", "0.0")
                .build()

            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Cache-Control", "no-cache")
                .addHeader("Pragma", "no-cache")
                .post(requestBody)
                .build()

            Log.d("TranscriptionService", "Sending request to OpenAI API")
            Log.d("TranscriptionService", "File: ${audioFile.name}, Size: ${audioFile.length()} bytes")
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d("TranscriptionService", "Response: $responseBody")
                
                try {
                    val jsonResponse = JSONObject(responseBody ?: "")
                    jsonResponse.optString("text", "").trim()
                } catch (e: Exception) {
                    Log.e("TranscriptionService", "Error parsing JSON response", e)
                    responseBody ?: ""
                }
            } else {
                Log.e("TranscriptionService", "API request failed: ${response.code} - ${response.message}")
                Log.e("TranscriptionService", "Response body: ${response.body?.string()}")
                ""
            }
        } catch (e: IOException) {
            Log.e("TranscriptionService", "Network error", e)
            ""
        } catch (e: Exception) {
            Log.e("TranscriptionService", "Unexpected error", e)
            ""
        }
    }
}
