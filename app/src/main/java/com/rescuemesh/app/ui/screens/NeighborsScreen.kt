package com.rescuemesh.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rescuemesh.app.mesh.NeighborLiveness
import com.rescuemesh.app.mesh.NeighborUiModel
import com.rescuemesh.app.ui.components.GlassPanel
import com.rescuemesh.app.ui.components.SectionTitle
import com.rescuemesh.app.ui.theme.EmergencyAmber
import com.rescuemesh.app.ui.theme.EmergencyRed
import com.rescuemesh.app.ui.theme.EmergencyTeal
import com.rescuemesh.app.ui.theme.TextSecondary

@Composable
fun NeighborsScreen(neighbors: List<NeighborUiModel>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .padding(18.dp)
    ) {
        Text("Nearby Nodes", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Active devices in your BLE range", color = TextSecondary)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (neighbors.isEmpty()) {
                item {
                    Text("No nodes discovered yet. Scanning...", color = TextSecondary, modifier = Modifier.padding(16.dp))
                }
            }
            items(neighbors) { neighbor ->
                NeighborItem(neighbor)
            }
        }
    }
}

@Composable
private fun NeighborItem(neighbor: NeighborUiModel) {
    val accent = when (neighbor.liveness) {
        NeighborLiveness.Active -> EmergencyTeal
        NeighborLiveness.Stale -> EmergencyAmber
        NeighborLiveness.Lost -> EmergencyRed
    }

    GlassPanel {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.1f))
                    .border(BorderStroke(1.dp, accent.copy(alpha = 0.5f)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(accent))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(neighbor.peerNodeId ?: "Connecting...", fontWeight = FontWeight.Bold)
                Text(neighbor.connectionState, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("RSSI: ${neighbor.rssi} dBm", style = MaterialTheme.typography.bodySmall, color = EmergencyAmber)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(neighbor.liveness.name, color = accent, style = MaterialTheme.typography.labelMedium)
                neighbor.name?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}
