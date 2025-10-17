package com.example.voicetranscriptionapp

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class OpenAiWorkflowService {
    private val client = OkHttpClient()
    private val serverUrl = "http://wisehome.hu:5000/analyze"

    /**
     * Sends transcribed text to wisehome.hu server and gets JSON response
     * 
     * @param userMessage The transcribed user message
     * @param conversationHistory Previous messages in the conversation
     * @return Full server response as JSON string
     */
    suspend fun sendMessageToWorkflow(
        userMessage: String, 
        conversationHistory: List<String> = emptyList()
    ): String {
        return try {
            Log.d("OpenAiWorkflow", "Sending to wisehome.hu server")
            Log.d("OpenAiWorkflow", "User message: $userMessage")
            Log.d("OpenAiWorkflow", "Conversation history: $conversationHistory")
            Log.d("OpenAiWorkflow", "Server URL: $serverUrl")
            
            // Build enhanced message with conversation history if available
            val enhancedMessage = if (conversationHistory.isNotEmpty()) {
                val historyText = conversationHistory.joinToString(". ")
                "$historyText. $userMessage"
            } else {
                userMessage
            }
            
            Log.d("OpenAiWorkflow", "Enhanced message: $enhancedMessage")
            
            val jsonBody = JSONObject().apply {
                put("text", enhancedMessage)
            }
            
            val requestBody = jsonBody.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url(serverUrl)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()

            Log.d("OpenAiWorkflow", "Sending request to wisehome.hu...")

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d("OpenAiWorkflow", "Response received: $responseBody")
                
                // Return the full response (not just result) so we can check has_missing_info
                return responseBody ?: ""
            } else {
                val errorBody = response.body?.string()
                Log.e("OpenAiWorkflow", "Server error: ${response.code} - ${response.message}")
                Log.e("OpenAiWorkflow", "Error body: $errorBody")
                "Hiba történt a szerver válasz generálásakor."
            }
            
        } catch (e: Exception) {
            Log.e("OpenAiWorkflow", "Error in server communication", e)
            "Hiba történt a szerverrel való kommunikáció során: ${e.message}"
        }
    }

    // Removed workflow-specific methods - now using Chat Completions API
    
    /* Old workflow implementation - kept for reference
    private fun executeWorkflow(input: String): String {
        return try {
            // Try the workflows/runs endpoint (most likely)
            val jsonBody = JSONObject().apply {
                put("workflow_id", workflowId)
                put("input", JSONObject().apply {
                    put("message", input)
                })
            }
            
            val requestBody = jsonBody.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/workflows/runs")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("OpenAI-Beta", "workflows=v1")
                .post(requestBody)
                .build()

            Log.d("OpenAiWorkflow", "Request URL: $baseUrl/workflows/runs")
            Log.d("OpenAiWorkflow", "Request body: ${jsonBody.toString()}")

            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d("OpenAiWorkflow", "Execute response: $responseBody")
                val jsonResponse = JSONObject(responseBody ?: "")
                jsonResponse.optString("id", "")
            } else {
                Log.e("OpenAiWorkflow", "Execute workflow failed: ${response.code} - ${response.message}")
                val errorBody = response.body?.string()
                Log.e("OpenAiWorkflow", "Error response: $errorBody")
                
                // Try alternative endpoint if first one fails
                tryAlternativeWorkflowExecution(input)
            }
        } catch (e: Exception) {
            Log.e("OpenAiWorkflow", "Error executing workflow", e)
            tryAlternativeWorkflowExecution(input)
        }
    }
    
    private fun tryAlternativeWorkflowExecution(input: String): String {
        return try {
            // Try direct workflow execution endpoint
            val jsonBody = JSONObject().apply {
                put("input", JSONObject().apply {
                    put("message", input)
                    put("text", input) // Some workflows might use 'text' instead
                })
            }
            
            val requestBody = jsonBody.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/workflows/$workflowId/runs")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .addHeader("OpenAI-Beta", "workflows=v1")
                .post(requestBody)
                .build()

            Log.d("OpenAiWorkflow", "Alternative request URL: $baseUrl/workflows/$workflowId/runs")
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d("OpenAiWorkflow", "Alternative response: $responseBody")
                val jsonResponse = JSONObject(responseBody ?: "")
                jsonResponse.optString("id", "")
            } else {
                Log.e("OpenAiWorkflow", "Alternative failed: ${response.code} - ${response.message}")
                Log.e("OpenAiWorkflow", "Response: ${response.body?.string()}")
                ""
            }
        } catch (e: Exception) {
            Log.e("OpenAiWorkflow", "Error in alternative execution", e)
            ""
        }
    }

    private fun waitForWorkflowCompletion(runId: String, maxAttempts: Int = 60): String {
        return try {
            var attempts = 0
            while (attempts < maxAttempts) {
                Thread.sleep(1000) // Wait 1 second between checks
                
                // Try to get run status
                val request = Request.Builder()
                    .url("$baseUrl/workflows/runs/$runId")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("OpenAI-Beta", "workflows=v1")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonResponse = JSONObject(responseBody ?: "")
                    val status = jsonResponse.optString("status", "")
                    
                    Log.d("OpenAiWorkflow", "Run status: $status (attempt ${attempts + 1}/$maxAttempts)")
                    
                    when (status) {
                        "completed", "succeeded", "success" -> {
                            // Extract the result
                            return extractWorkflowResult(jsonResponse)
                        }
                        "failed", "error" -> {
                            Log.e("OpenAiWorkflow", "Workflow failed: ${jsonResponse.optString("error", "Unknown error")}")
                            return ""
                        }
                        "running", "in_progress", "queued" -> {
                            // Continue waiting
                        }
                    }
                } else {
                    Log.e("OpenAiWorkflow", "Check status failed: ${response.code}")
                }
                
                attempts++
            }
            
            Log.e("OpenAiWorkflow", "Workflow did not complete within timeout")
            ""
        } catch (e: Exception) {
            Log.e("OpenAiWorkflow", "Error waiting for completion", e)
            ""
        }
    }

    private fun extractWorkflowResult(jsonResponse: JSONObject): String {
        return try {
            // Try multiple possible result locations
            
            // 1. Try output.message
            val output = jsonResponse.optJSONObject("output")
            if (output != null) {
                val message = output.optString("message", "")
                if (message.isNotEmpty()) {
                    Log.d("OpenAiWorkflow", "Found result in output.message")
                    return message
                }
                
                val text = output.optString("text", "")
                if (text.isNotEmpty()) {
                    Log.d("OpenAiWorkflow", "Found result in output.text")
                    return text
                }
                
                val response = output.optString("response", "")
                if (response.isNotEmpty()) {
                    Log.d("OpenAiWorkflow", "Found result in output.response")
                    return response
                }
            }
            
            // 2. Try result field
            val result = jsonResponse.optString("result", "")
            if (result.isNotEmpty()) {
                Log.d("OpenAiWorkflow", "Found result in result field")
                return result
            }
            
            // 3. Try response field
            val responseField = jsonResponse.optString("response", "")
            if (responseField.isNotEmpty()) {
                Log.d("OpenAiWorkflow", "Found result in response field")
                return responseField
            }
            
            // 4. If nothing found, log the full response for debugging
            Log.w("OpenAiWorkflow", "Could not find result in expected fields. Full response: ${jsonResponse.toString()}")
            ""
        } catch (e: Exception) {
            Log.e("OpenAiWorkflow", "Error extracting result", e)
            ""
        }
    }
    */
}

