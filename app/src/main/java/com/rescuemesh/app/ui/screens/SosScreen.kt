package com.rescuemesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rescuemesh.app.ui.components.GlassPanel
import com.rescuemesh.app.ui.theme.EmergencyRed
import com.rescuemesh.app.ui.theme.TextPrimary
import com.rescuemesh.app.ui.theme.TextSecondary

@Composable
fun SosScreen(
    onSendSos: (String) -> Unit,
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0202))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "BROADCAST SOS",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = EmergencyRed
            )
            
            Text(
                "Your message will be relayed through nearby RescueMesh nodes even without internet.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it.take(220) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Emergency Details") },
                        placeholder = { Text("e.g., Medical help needed at North Entrance") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = EmergencyRed,
                            unfocusedBorderColor = TextSecondary
                        ),
                        minLines = 4
                    )
                }
            }

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendSos(messageText)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                enabled = messageText.isNotBlank()
            ) {
                Text("SEND EMERGENCY SIGNAL", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
            ) {
                Text("CANCEL")
            }
        }
    }
}
