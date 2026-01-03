package com.example.sentinel.logic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream

class AudioInjector(
    private val context: Context,
    private val geminiClient: GeminiLiveClient
) {

    private var injectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private val CHUNK_SIZE = 3200 // 100ms at 16kHz 16-bit mono (16000 * 2 bytes * 0.1s)

    fun startInjection(resourceId: Int) {
        stopInjection()
        
        injectionJob = scope.launch {
            Log.d("AudioInjector", "Starting injection from resource: $resourceId")
            var inputStream: InputStream? = null
            
            try {
                inputStream = context.resources.openRawResource(resourceId)
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                
                // Read chunks and send
                while (isActive) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) {
                        Log.d("AudioInjector", "End of audio file, looping or stopping")
                        inputStream.close()
                        inputStream = context.resources.openRawResource(resourceId) // Loop
                        continue
                    }
                    
                    // If bytesRead < CHUNK_SIZE, we should probably pad or just send what we have
                    val chunkToSend = if (bytesRead == CHUNK_SIZE) {
                        buffer
                    } else {
                        buffer.copyOfRange(0, bytesRead)
                    }

                    // This simulates the "mic" feeding the websocket
                    geminiClient.sendAudioData(chunkToSend)
                    
                    // Throttle to match real-time
                    // We sent 3200 bytes. 
                    // 16000 Hz * 2 bytes/sample = 32000 bytes/sec
                    // 3200 bytes is 0.1 seconds (100ms)
                    delay(100) 
                }
                
            } catch (e: Exception) {
                Log.e("AudioInjector", "Injection failed", e)
            } finally {
                inputStream?.close()
            }
        }
    }

    fun stopInjection() {
        injectionJob?.cancel()
        injectionJob = null
        Log.d("AudioInjector", "Stopped injection")
    }
}
