package com.rescuemesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
fun MessagesScreen(messages: List<SosUiModel>) {
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
                SosItem(message)
            }
        }
    }
}

@Composable
private fun SosItem(message: SosUiModel) {
    val stateColor = when (message.state) {
        "PERSISTED", "QUEUED" -> EmergencyAmber
        "RELAYED" -> EmergencyTeal
        else -> TextSecondary
    }

    GlassPanel {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(message.id, fontWeight = FontWeight.Black, color = EmergencyRed)
                Text(message.state, color = stateColor, style = MaterialTheme.typography.labelMedium)
            }
            
            Text(message.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Hops: ${message.hopCount}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("TTL: ${message.ttl}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
