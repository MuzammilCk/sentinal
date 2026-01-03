package com.example.sentinel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SentinelState(
    val riskLevel: Int = 0,
    val reason: String = "Monitoring...",
    val isDemoMode: Boolean = false,
    val isConnected: Boolean = false,
    val isInjectingScenario: Boolean = false
)

@Composable
fun SentinelScreen(
    state: SentinelState,
    onToggleDemoMode: (Boolean) -> Unit,
    onSimulateScam: () -> Unit
) {
    val isDanger = state.riskLevel > 85
    val backgroundColor = if (isDanger) Color(0xFFFFCDD2) else Color(0xFFC8E6C9) // Light Red/Green
    val statusColor = if (isDanger) Color(0xFFD32F2F) else Color(0xFF388E3C) // Dark Red/Green
    val statusText = if (isDanger) "CAUTION: SCAM SUSPICIOUS" else "SAFE - MONITORING"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Header
            Text(
                text = statusText,
                style = MaterialTheme.typography.displaySmall,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Risk Level
            Text(
                text = "Risk Level: ${state.riskLevel}%",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Reason Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Reasoning Engine:",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.reason,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Black
                    )
                }
            }
        }
        
        // Footer Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Connection Status
            Text(
                text = if (state.isConnected) "● Connected to Gemini Live" else "○ Disconnected",
                color = if (state.isConnected) Color(0xFF006400) else Color.Gray,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 12.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEEEEEE))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Scenario Injector (Demo Mode)", fontWeight = FontWeight.Bold)
                        Switch(
                            checked = state.isDemoMode,
                            onCheckedChange = onToggleDemoMode
                        )
                    }
                    
                    if (state.isDemoMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onSimulateScam,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isInjectingScenario
                        ) {
                            Text(text = if (state.isInjectingScenario) "Injecting Audio..." else "Inject 'Digital Arrest' Scenario")
                        }
                    }
                }
            }
        }
    }
}
