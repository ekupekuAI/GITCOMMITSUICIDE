package com.rescuemesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rescuemesh.app.mesh.SosUiModel
import com.rescuemesh.app.ui.components.GlassPanel
import com.rescuemesh.app.ui.theme.EmergencyAmber
import com.rescuemesh.app.ui.theme.EmergencyRed
import com.rescuemesh.app.ui.theme.EmergencyTeal
import com.rescuemesh.app.ui.theme.TextSecondary

@Composable
fun MessagesScreen(
    messages: List<SosUiModel>,
    localRole: com.rescuemesh.app.protocol.NodeRole
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .padding(18.dp)
    ) {
        Text("SOS Broadcasts", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Emergency signals received via mesh", color = TextSecondary)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text("No messages in database.", color = TextSecondary, modifier = Modifier.padding(16.dp))
                }
            }
            items(messages) { message ->
                SosItem(message, localRole)
            }
        }
    }
}

@Composable
private fun SosItem(
    message: SosUiModel,
    localRole: com.rescuemesh.app.protocol.NodeRole
) {
    val isAuthorized = localRole != com.rescuemesh.app.protocol.NodeRole.NODE_ROLE_PUBLIC_RELAY
    
    val stateColor = when (message.state) {
        "PERSISTED", "QUEUED" -> EmergencyAmber
        "RELAYED" -> EmergencyTeal
        else -> TextSecondary
    }

    val priorityLabel = when(message.priority) {
        3 -> "CRITICAL"
        2 -> "HIGH"
        1 -> "NORMAL"
        else -> "LOW"
    }
    
    val priorityColor = when(message.priority) {
        3 -> EmergencyRed
        2 -> EmergencyAmber
        1 -> EmergencyTeal
        else -> Color.Gray
    }

    GlassPanel {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(message.id, fontWeight = FontWeight.Black, color = EmergencyRed)
                Text(priorityLabel, color = priorityColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            
            val displayedText = if (isAuthorized) message.text else "CONTENT MASKED (RELAY ONLY)"
            Text(displayedText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            
            if (message.latitude != null && message.longitude != null) {
                val displayedLoc = if (isAuthorized) {
                    "LOCATION: ${"%.4f".format(message.latitude)}, ${"%.4f".format(message.longitude)}"
                } else {
                    "LOCATION MASKED (RELAY ONLY)"
                }
                Text(
                    displayedLoc,
                    color = EmergencyTeal,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Hops: ${message.hopCount}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("TTL: ${message.ttl}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Text(message.state, color = stateColor, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
