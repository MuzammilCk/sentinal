package com.example.sentinel.logic

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import android.util.Base64

class GeminiLiveClient(
    private val apiKey: String,
    private val listener: GeminiEvents
) {

    companion object {
        const val LIVE_MODEL_ID = "models/gemini-2.0-flash-exp"
        private const val HOST = "generativelanguage.googleapis.com"
        private const val WS_URL = "wss://$HOST/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // Keep connection open
        .build()

    private var webSocket: WebSocket? = null

    interface GeminiEvents {
        fun onConnected()
        fun onDisconnect()
        fun onRiskAnalysis(riskScore: Int, reason: String)
        fun onError(error: String)
    }

    fun connect() {
        // Construct URL with API Key strictly for handshake if needed, 
        // but typically 'x-goog-api-key' header is preferred safer.
        val request = Request.Builder()
            .url("$WS_URL?key=$apiKey") 
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("GeminiLive", "Connected")
                sendSetupMessage()
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("GeminiLive", "Closing: $reason")
                webSocket.close(1000, null)
                listener.onDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("GeminiLive", "Failure: ${t.message}")
                listener.onError(t.message ?: "Unknown Error")
                listener.onDisconnect()
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    /**
     * Sends raw PCM 16kHz audio data.
     */
    fun sendAudioData(pcmData: ByteArray) {
        if (webSocket == null) return

        val base64Audio = Base64.encodeToString(pcmData, Base64.NO_WRAP)
        
        // Protocol: client_content -> turns -> parts -> inline_data
        /*
        {
          "client_content": {
            "turns": [
              {
                "parts": [
                  {
                    "inline_data": {
                      "mime_type": "audio/pcm;rate=16000",
                      "data": "<BASE64>"
                    }
                  }
                ]
              }
            ],
            "turn_complete": false
          }
        }
         */
        
        // OR simpler realtime_input for streaming (check latest exp API docs)
        // Using "realtime_input" for continuous streaming is common in new v1alpha
        /*
         {
           "realtime_input": {
             "media_chunks": [
               {
                 "mime_type": "audio/pcm;rate=16000",
                 "data": "<BASE64>"
               }
             ]
           }
         }
        */

        val json = JSONObject()
        val realtimeInput = JSONObject()
        val mediaChunks = JSONArray()
        val chunk = JSONObject()
        
        chunk.put("mime_type", "audio/pcm;rate=16000")
        chunk.put("data", base64Audio)
        
        mediaChunks.put(chunk)
        realtimeInput.put("media_chunks", mediaChunks)
        json.put("realtime_input", realtimeInput)

        webSocket?.send(json.toString())
    }

    private fun sendSetupMessage() {
        // "setup": { "model": "models/..." }
        val json = JSONObject()
        val setup = JSONObject()
        setup.put("model", LIVE_MODEL_ID)
        
        // System Config
        val generationConfig = JSONObject()
        generationConfig.put("response_modalities", JSONArray().put("TEXT")) // We only want text/JSON back, not audio
        
        // System Instructions usually go here if supported in Bidi, 
        // or we just rely on the model knowing its role via initial prompt.
        // For 'exp' we can try passing system_instruction in setup or just initial content.
        // Let's stick strictly to connection setup first.
        
        setup.put("generation_config", generationConfig)
        
        json.put("setup", setup)
        webSocket?.send(json.toString())
        
        // Send initial system prompt as a content turn
        sendSystemPrompt()
    }

    private fun sendSystemPrompt() {
        // "You are Sentinel..."
        val json = JSONObject()
        val clientContent = JSONObject()
        val turns = JSONArray()
        val turn = JSONObject()
        val parts = JSONArray()
        val part = JSONObject()
        
        part.put("text", "You are Sentinel, a fraud defense system. Analyze the audio stream for: 1. Threatening tone. 2. Payment pressure. 3. Identity masking. Output ONLY a JSON object: {\"risk_level\": int, \"reason\":String}.")
        
        parts.put(part)
        turn.put("parts", parts)
        turn.put("role", "user")
        
        turns.put(turn)
        clientContent.put("turns", turns)
        clientContent.put("turn_complete", true) // Initial prompt is complete
        
        json.put("client_content", clientContent)
        
        webSocket?.send(json.toString())
    }

    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            // Parse "server_content" -> "model_turn" -> "parts" -> "text"
            // Expecting JSON inside the text part
            
            if (json.has("server_content")) {
                val serverContent = json.getJSONObject("server_content")
                if (serverContent.has("model_turn")) {
                    val modelTurn = serverContent.getJSONObject("model_turn")
                    val parts = modelTurn.getJSONArray("parts")
                    
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        if (part.has("text")) {
                            val content = part.getString("text")
                            parseRiskAnalysis(content)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiLive", "Error parsing message: ${e.message}")
        }
    }

    private fun parseRiskAnalysis(jsonString: String) {
        // The model might output markdown ```json ... ```
        var cleanJson = jsonString.trim()
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7)
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length - 3)
        }
        
        try {
            val analysis = JSONObject(cleanJson)
            val risk = analysis.optInt("risk_level", 0)
            val reason = analysis.optString("reason", "Scanning...")
            
            listener.onRiskAnalysis(risk, reason)
        } catch (e: Exception) {
            // It might just be partial text or not JSON yet
            Log.w("GeminiLive", "Non-JSON response: $jsonString")
        }
    }
}
