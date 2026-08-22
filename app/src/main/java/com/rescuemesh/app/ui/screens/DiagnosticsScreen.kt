package com.rescuemesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rescuemesh.app.mesh.MeshDiscoveryUiState
import com.rescuemesh.app.mesh.MeshConfig
import com.rescuemesh.app.ui.theme.EmergencyTeal
import com.rescuemesh.app.ui.theme.EmergencyRed
import com.rescuemesh.app.ui.theme.TextSecondary

@Composable
fun DiagnosticsScreen(
    state: MeshDiscoveryUiState,
    onSetPowerMode: (MeshConfig.PowerMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .padding(18.dp)
    ) {
        Text("System Diagnostics", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Real-time network and engine metrics", color = TextSecondary)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionHeader("ENGINE PERFORMANCE")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Discovery", state.performanceStats.totalDiscoveries.toString(), Modifier.weight(1f))
                    MetricCard("Success Rate", "%.1f%%".format(state.performanceStats.connectionSuccessRate), Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Avg Latency", "${state.performanceStats.avgLatencyMs}ms", Modifier.weight(1f))
                    MetricCard("Duplicates", state.performanceStats.duplicatesRejected.toString(), Modifier.weight(1f))
                }
            }

            item {
                SectionHeader("RADIO POWER MODE")
                DiagnosticsGlassPanel {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        PowerModeButton("LOW", MeshConfig.PowerMode.LOW_POWER, state.currentPowerMode == MeshConfig.PowerMode.LOW_POWER) { onSetPowerMode(it) }
                        PowerModeButton("NORMAL", MeshConfig.PowerMode.NORMAL, state.currentPowerMode == MeshConfig.PowerMode.NORMAL) { onSetPowerMode(it) }
                        PowerModeButton("URGENT", MeshConfig.PowerMode.EMERGENCY, state.currentPowerMode == MeshConfig.PowerMode.EMERGENCY) { onSetPowerMode(it) }
                    }
                }
            }

            if (state.impactDetected) {
                item {
                    DiagnosticsGlassPanel(modifier = Modifier.fillMaxWidth(), color = EmergencyRed.copy(alpha = 0.2f)) {
                        Text(
                            "SIGNAL: POTENTIAL IMPACT ALERT", 
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Black,
                            color = EmergencyRed
                        )
                    }
                }
            }

            item {
                SectionHeader("NEIGHBOR LOG")
            }
            items(state.neighbors) { neighbor ->
                DiagnosticsGlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(neighbor.peerNodeId ?: neighbor.sessionId, fontWeight = FontWeight.Bold)
                        Text("RSSI: ${neighbor.rssi} dBm | State: ${neighbor.connectionState}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Text("Last Packet: ${neighbor.lastPacketState ?: "None"}", style = MaterialTheme.typography.bodySmall, color = EmergencyTeal)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    DiagnosticsGlassPanel(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text(value, fontWeight = FontWeight.Bold, color = EmergencyTeal, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PowerModeButton(
    label: String, 
    mode: MeshConfig.PowerMode, 
    isSelected: Boolean, 
    onClick: (MeshConfig.PowerMode) -> Unit
) {
    Button(
        onClick = { onClick(mode) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) EmergencyTeal else Color.Transparent,
            contentColor = if (isSelected) Color.Black else Color.White
        ),
        modifier = Modifier.height(40.dp),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DiagnosticsGlassPanel(
    modifier: Modifier = Modifier, 
    color: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = color,
        content = content
    )
}
