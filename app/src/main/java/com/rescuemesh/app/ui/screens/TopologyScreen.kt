package com.rescuemesh.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rescuemesh.app.mesh.NeighborUiModel
import com.rescuemesh.app.ui.theme.TextSecondary
import com.rescuemesh.app.ui.topology.TopologyView

@Composable
fun TopologyScreen(localNodeId: String, neighbors: List<NeighborUiModel>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(18.dp)
    ) {
        Text("Mesh Topology", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Real-time node relationships and connections", color = TextSecondary)
        
        Box(modifier = Modifier.weight(1f).padding(top = 24.dp)) {
            TopologyView(localNodeId, neighbors)
        }
    }
}
