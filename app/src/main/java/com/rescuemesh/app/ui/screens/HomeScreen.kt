package com.rescuemesh.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    localRole: com.rescuemesh.app.protocol.NodeRole,
    onNavigateToSos: () -> Unit,
    onToggleDiscovery: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isResponder = localRole != com.rescuemesh.app.protocol.NodeRole.NODE_ROLE_PUBLIC_RELAY
    
    // Background Animation
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val bgGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090F))
    ) {
        // Innovative Background Glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        0.0f to (if (isResponder) EmergencyTeal else EmergencyRed).copy(alpha = 0.1f * bgGlow),
                        1.0f to Color.Transparent,
                        center = androidx.compose.ui.geometry.Offset(0f, 0f)
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 24.dp, bottom = 32.dp)
        ) {
            item {
                HomeHeader(state, permissionsGranted, discoveryRequested, localRole)
            }

            if (!isResponder) {
                item {
                    SosTriggerButton(onNavigateToSos)
                }
            } else {
                item {
                    ResponderDashboardLabel()
                }
            }

            item {
                QuickActions(context)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatusTile(
                        label = "NEARBY",
                        value = state.activeNeighborCount.toString(),
                        accent = EmergencyTeal,
                        modifier = Modifier.weight(1f)
                    )
                    StatusTile(
                        label = "LOGS",
                        value = state.sosMessages.size.toString(),
                        accent = EmergencyAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                MeshStatusCard(state, discoveryRequested, onToggleDiscovery)
            }

            if (state.incidents.isNotEmpty()) {
                item {
                    SectionLabel("ACTIVE INCIDENTS")
                }
                items(state.incidents) { incident ->
                    IncidentItem(incident)
                }
            }
            
            if (state.sosMessages.isNotEmpty()) {
                item {
                    SectionLabel("LATEST ACTIVITY")
                }
                items(state.sosMessages.take(3)) { message ->
                    RecentSosItem(message.text, message.state)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, 
        style = MaterialTheme.typography.labelLarge, 
        fontWeight = FontWeight.Black,
        color = Color.White.copy(alpha = 0.4f),
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun ResponderDashboardLabel() {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        color = EmergencyTeal.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = EmergencyTeal)
            Text("AUTHORIZED RESPONDER DASHBOARD", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = EmergencyTeal)
        }
    }
}

@Composable
private fun QuickActions(context: android.content.Context) {
    OutlinedButton(
        onClick = { 
            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:112")
            }
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text("CALL EMERGENCY SERVICES (112)", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SosTriggerButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .scale(scale),
        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
        shape = RoundedCornerShape(24.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TRIGGER SOS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("BROADCAST OFFLINE SIGNAL", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@Composable
private fun HomeHeader(
    state: MeshDiscoveryUiState,
    permissionsGranted: Boolean,
    discoveryRequested: Boolean,
    localRole: com.rescuemesh.app.protocol.NodeRole,
) {
    val roleLabel = localRole.name.replace("NODE_ROLE_", "").replace("_", " ")
    
    GlassPanel {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text("RescueMesh", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                    Text(roleLabel, color = EmergencyTeal, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
                StatusPill("OFFLINE", EmergencyTeal)
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusPill(if (permissionsGranted) "BLE: OK" else "BLE: ERR", if (permissionsGranted) EmergencyTeal else EmergencyAmber)
                StatusPill(if (discoveryRequested) "ENGINE: LIVE" else "ENGINE: IDLE", if (discoveryRequested) EmergencyTeal else Color.Gray)
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("LOCAL NODE IDENTIFIER", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontWeight = FontWeight.Bold)
                Text(state.nodeId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color.White)
            }
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
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("MESH ENGINE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black, color = Color.White)
                Text(
                    if (discoveryRequested) "Relaying packets to ${state.activeNeighborCount} peers" else "Network engine is currently suspended",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Switch(
                checked = discoveryRequested,
                onCheckedChange = { onToggleDiscovery() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = EmergencyTeal,
                    checkedTrackColor = EmergencyTeal.copy(alpha = 0.2f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray.copy(alpha = 0.5f)
                )
            )
        }
    }
}

@Composable
private fun IncidentItem(incident: com.rescuemesh.app.data.IncidentEntity) {
    val categoryLabel = com.rescuemesh.app.protocol.EmergencyCategory.forNumber(incident.category).name.replace("EMERGENCY_CATEGORY_", "")
    
    GlassPanel(modifier = Modifier.fillMaxWidth(), color = EmergencyRed.copy(alpha = 0.05f)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(EmergencyRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(incident.corroboratingCount.toString(), color = Color.White, fontWeight = FontWeight.Black)
            }
            Column {
                Text(categoryLabel, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge, color = EmergencyRed)
                Text(incident.summary, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun RecentSosItem(text: String, state: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(10.dp).background(EmergencyRed, CircleShape))
            Column {
                Text(text, maxLines = 1, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Text(state, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
    }
}
