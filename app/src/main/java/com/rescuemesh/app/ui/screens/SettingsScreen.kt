package com.rescuemesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rescuemesh.app.mesh.MeshDiscoveryUiState
import com.rescuemesh.app.protocol.NodeRole
import com.rescuemesh.app.ui.components.GlassPanel
import com.rescuemesh.app.ui.theme.EmergencyAmber
import com.rescuemesh.app.ui.theme.EmergencyTeal
import com.rescuemesh.app.ui.theme.EmergencyRed
import com.rescuemesh.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    state: MeshDiscoveryUiState,
    currentNodeName: String,
    currentNodeRole: NodeRole,
    onSaveName: (String) -> Unit,
    onSaveRole: (NodeRole) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentNodeName) }
    var authCode by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var showAuthError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .padding(18.dp)
    ) {
        Text("Node Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Customize your identity on the mesh", color = TextSecondary)

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SectionHeader("IDENTITY")
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Rename your node", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it.take(20) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. ALPHA-1") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EmergencyTeal,
                                unfocusedBorderColor = TextSecondary
                            )
                        )
                        Button(
                            onClick = { onSaveName(nameInput) },
                            modifier = Modifier.align(androidx.compose.ui.Alignment.End),
                            colors = ButtonDefaults.buttonColors(containerColor = EmergencyTeal),
                            enabled = nameInput != currentNodeName
                        ) {
                            Text("SAVE NAME", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                SectionHeader("NODE ROLE (RESPONDER AUTH REQUIRED)")
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Current: ${currentNodeRole.name.replace("NODE_ROLE_", "").replace("_", " ")}", color = EmergencyTeal)
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { expanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("CHANGE ROLE")
                            }
                            
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                NodeRole.values().filter { it != NodeRole.UNRECOGNIZED }.forEach { role ->
                                    DropdownMenuItem(
                                        text = { Text(role.name.replace("NODE_ROLE_", "").replace("_", " ")) },
                                        onClick = {
                                            if (role == NodeRole.NODE_ROLE_PUBLIC_RELAY) {
                                                onSaveRole(role)
                                            } else {
                                                // Trigger auth flow
                                            }
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (currentNodeRole == NodeRole.NODE_ROLE_PUBLIC_RELAY) {
                            Text("Enter Auth Code to enable Responder capabilities", style = MaterialTheme.typography.labelSmall)
                            OutlinedTextField(
                                value = authCode,
                                onValueChange = { authCode = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Auth Token") },
                                label = { Text("Responder Verification") },
                                singleLine = true
                            )
                            if (showAuthError) {
                                Text("Invalid Auth Code", color = EmergencyRed, style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = {
                                    // Hardcoded prototype auth code
                                    if (authCode == "RESCUE112") {
                                        onSaveRole(NodeRole.NODE_ROLE_COORDINATOR)
                                        showAuthError = false
                                    } else {
                                        showAuthError = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyAmber)
                            ) {
                                Text("VERIFY & OPT-IN AS RESPONDER", color = Color.Black)
                            }
                        } else {
                            Button(
                                onClick = { onSaveRole(NodeRole.NODE_ROLE_PUBLIC_RELAY) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                            ) {
                                Text("EXIT RESPONDER MODE")
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader("DIAGNOSTICS")
                DiagnosticItem("Raw Node ID", state.nodeId)
            }
            item {
                DiagnosticItem("Advertiser Status", state.advertiserState.toString())
            }
            item {
                DiagnosticItem("Scanner Status", state.scannerState.toString())
            }
            item {
                DiagnosticItem("Local Database", "Operational")
            }
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "PROTOTYPE NOTICE: Responder roles in this version use a static auth code (RESCUE112) for verification. True production requires a signed certificate from an emergency coordinator.",
                    style = MaterialTheme.typography.bodySmall,
                    color = EmergencyAmber
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = TextSecondary,
        modifier = Modifier.padding(bottom = 8.dp, top = 8.dp),
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun DiagnosticItem(label: String, value: String) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = EmergencyTeal)
        }
    }
}
