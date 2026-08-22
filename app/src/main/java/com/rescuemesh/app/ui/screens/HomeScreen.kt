package com.rescuemesh.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rescuemesh.app.mesh.MeshDiscoveryUiState
import com.rescuemesh.app.ui.components.GlassPanel
import com.rescuemesh.app.ui.components.StatusPill
import com.rescuemesh.app.ui.components.StatusTile
import com.rescuemesh.app.ui.theme.EmergencyAmber
import com.rescuemesh.app.ui.theme.EmergencyRed
import com.rescuemesh.app.ui.theme.EmergencyTeal
import com.rescuemesh.app.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    state: MeshDiscoveryUiState,
    permissionsGranted: Boolean,
    discoveryRequested: Boolean,
    onNavigateToSos: () -> Unit,
    onToggleDiscovery: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF090B10), Color(0xFF10141D))
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HomeHeader(state, permissionsGranted, discoveryRequested)
            }

            item {
                SosTriggerButton(onNavigateToSos)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusTile(
                        label = "NEIGHBORS",
                        value = state.activeNeighborCount.toString(),
                        accent = EmergencyTeal,
                        modifier = Modifier.weight(1f)
                    )
                    StatusTile(
                        label = "MESSAGES",
                        value = state.sosMessages.size.toString(),
                        accent = EmergencyAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                MeshStatusCard(state, discoveryRequested, onToggleDiscovery)
            }
            
            if (state.sosMessages.isNotEmpty()) {
                item {
                    Text(
                        "LATEST BROADCASTS", 
                        style = MaterialTheme.typography.labelLarge, 
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(state.sosMessages.take(3)) { message ->
                    RecentSosItem(message.text, message.state)
                }
            }
        }
    }
}

@Composable
private fun SosTriggerButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "TRIGGER EMERGENCY SOS", 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Black
            )
            Text(
                "BROADCAST TO NEARBY NODES", 
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun HomeHeader(
    state: MeshDiscoveryUiState,
    permissionsGranted: Boolean,
    discoveryRequested: Boolean,
) {
    GlassPanel {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("RescueMesh", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("SECURE OFFLINE MESH", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
                
                StatusPill(
                    label = "OFFLINE",
                    accent = EmergencyTeal
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    label = if (permissionsGranted) "BLE READY" else "BLE ERROR",
                    accent = if (permissionsGranted) EmergencyTeal else EmergencyAmber
                )
                StatusPill(
                    label = if (discoveryRequested) "ACTIVE" else "IDLE",
                    accent = if (discoveryRequested) EmergencyTeal else Color.Gray
                )
            }

            StatusTile(
                label = "LOCAL NODE ID",
                value = state.nodeId,
                accent = EmergencyTeal,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MeshStatusCard(
    state: MeshDiscoveryUiState,
    discoveryRequested: Boolean,
    onToggleDiscovery: () -> Unit
) {
    GlassPanel {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("MESH ENGINE", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (discoveryRequested) "Broadcasting & Scanning" else "Engine Paused",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${state.activeNeighborCount} active connections",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                
                Switch(
                    checked = discoveryRequested,
                    onCheckedChange = { onToggleDiscovery() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = EmergencyTeal,
                        checkedTrackColor = EmergencyTeal.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun RecentSosItem(text: String, state: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(EmergencyRed, CircleShape)
            )
            Column {
                Text(text, maxLines = 1, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                Text(state, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}
