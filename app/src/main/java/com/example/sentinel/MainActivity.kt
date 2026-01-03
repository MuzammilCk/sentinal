package com.example.sentinel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.sentinel.logic.AudioInjector
import com.example.sentinel.logic.AudioRecorder
import com.example.sentinel.logic.GeminiLiveClient
import com.example.sentinel.ui.SentinelScreen
import com.example.sentinel.ui.SentinelState
import com.example.sentinel.ui.theme.SentinelTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), GeminiLiveClient.GeminiEvents {

    private lateinit var geminiClient: GeminiLiveClient
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var audioInjector: AudioInjector
    
    // UI State
    private var uiState by mutableStateOf(SentinelState())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startAudioCapture()
        } else {
            Toast.makeText(this, "Microphone permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Components
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey == "YOUR_API_KEY_HERE" || apiKey.isEmpty()) {
            Toast.makeText(this, "Please set GEMINI_API_KEY in local.properties", Toast.LENGTH_LONG).show()
        }
        
        geminiClient = GeminiLiveClient(apiKey, this)
        audioRecorder = AudioRecorder(geminiClient)
        audioInjector = AudioInjector(this, geminiClient)

        setContent {
            SentinelTheme {
                SentinelScreen(
                    state = uiState,
                    onToggleDemoMode = { isDemo ->
                        uiState = uiState.copy(isDemoMode = isDemo)
                        // If switching to Demo, stop Mic. If switching to Normal, start Mic.
                        if (isDemo) {
                            audioRecorder.stopRecording()
                        } else {
                            checkAndStartMic()
                        }
                    },
                    onSimulateScam = {
                        startScamSimulation()
                    }
                )
            }
        }
        
        // Auto-connect on start
        geminiClient.connect()
        // Check permissions and start mic per default behavior (unless in demo mode)
        if (!uiState.isDemoMode) {
            checkAndStartMic()
        }
    }

    private fun checkAndStartMic() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            startAudioCapture()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startAudioCapture() {
        if (!uiState.isDemoMode) {
            audioRecorder.startRecording()
        }
    }
    
    private fun startScamSimulation() {
        if (uiState.isDemoMode) {
            uiState = uiState.copy(isInjectingScenario = true)
            audioInjector.startInjection(R.raw.scam_scenario_digital_arrest)
            // Note: Auto-stop logic isn't fully implemented in IO, but we can assume loop or duration
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecorder.stopRecording()
        audioInjector.stopInjection()
        geminiClient.disconnect()
    }

    // Gemini Events
    override fun onConnected() {
        runOnUiThread {
            uiState = uiState.copy(isConnected = true)
        }
    }

    override fun onDisconnect() {
        runOnUiThread {
            uiState = uiState.copy(isConnected = false)
        }
    }

    override fun onRiskAnalysis(riskScore: Int, reason: String) {
        runOnUiThread {
            uiState = uiState.copy(
                riskLevel = riskScore,
                reason = reason
            )
        }
    }

    override fun onError(error: String) {
        runOnUiThread {
            Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_SHORT).show()
        }
    }
}
