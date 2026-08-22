package com.rescuemesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rescuemesh.app.ui.components.GlassPanel
import com.rescuemesh.app.ui.theme.EmergencyAmber
import com.rescuemesh.app.ui.theme.EmergencyRed
import com.rescuemesh.app.ui.theme.EmergencyTeal
import com.rescuemesh.app.ui.theme.TextPrimary
import com.rescuemesh.app.ui.theme.TextSecondary
import com.rescuemesh.app.protocol.EmergencyCategory

@Composable
fun SosScreen(
    onSendSos: (String, Int, EmergencyCategory) -> Unit,
    onBack: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableIntStateOf(1) } // 0:LOW, 1:NORMAL, 2:HIGH, 3:CRITICAL
    var selectedCategory by remember { mutableStateOf(EmergencyCategory.EMERGENCY_CATEGORY_UNSPECIFIED) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0202))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "BROADCAST SOS",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = EmergencyRed
            )
            
            Text(
                "Your message and device location (when available) will be relayed through the mesh network.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )

            SectionHeader("EMERGENCY CATEGORY")
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(EmergencyCategory.values().filter { it != EmergencyCategory.UNRECOGNIZED }) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.name.replace("EMERGENCY_CATEGORY_", "").replace("_", " ")) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmergencyTeal,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }

            SectionHeader("SEVERITY LEVEL")
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriorityButton("CRITICAL", selectedPriority == 3, EmergencyRed) { selectedPriority = 3 }
                    PriorityButton("HIGH", selectedPriority == 2, EmergencyAmber) { selectedPriority = 2 }
                    PriorityButton("NORMAL", selectedPriority == 1, EmergencyTeal) { selectedPriority = 1 }
                    PriorityButton("LOW", selectedPriority == 0, Color.Gray) { selectedPriority = 0 }
                }
            }

            SectionHeader("DETAILS")
            GlassPanel(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it.take(220) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Describe the situation...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = EmergencyRed,
                            unfocusedBorderColor = TextSecondary
                        ),
                        minLines = 3
                    )
                }
            }

            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSendSos(messageText, selectedPriority, selectedCategory)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondary,
        modifier = Modifier.fillMaxWidth(),
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun RowScope.PriorityButton(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else Color.Transparent,
            contentColor = if (isSelected) Color.Black else TextSecondary
        ),
        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray) else null,
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
