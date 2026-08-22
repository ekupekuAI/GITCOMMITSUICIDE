package com.rescuemesh.app.ui.topology

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rescuemesh.app.mesh.NeighborLiveness
import com.rescuemesh.app.mesh.NeighborUiModel
import com.rescuemesh.app.ui.theme.EmergencyAmber
import com.rescuemesh.app.ui.theme.EmergencyTeal
import com.rescuemesh.app.ui.theme.TextPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun TopologyView(
    localNodeId: String,
    neighbors: List<NeighborUiModel>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val activeNeighbors = neighbors.filter { it.liveness != NeighborLiveness.Lost }
    
    // Parallax/3D Rotation state
    var rotX by remember { mutableFloatStateOf(0f) }
    var rotY by remember { mutableFloatStateOf(0f) }
    
    // Animation time state
    var animTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startTime = System.currentTimeMillis()
        while (true) {
            withFrameNanos { 
                animTime = (System.currentTimeMillis() - startTime) / 1000f
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    rotY += dragAmount.x * 0.15f
                    rotX -= dragAmount.y * 0.15f
                }
            }
            .graphicsLayer {
                rotationX = rotX
                rotationY = rotY
                cameraDistance = 12 * density
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = size.minDimension / 2.8f
            
            // Draw center glow
            drawCircle(
                brush = Brush.radialGradient(
                    0.0f to EmergencyTeal.copy(alpha = 0.15f),
                    1.0f to Color.Transparent,
                    center = Offset(centerX, centerY),
                    radius = radius * 1.5f
                ),
                radius = radius * 1.5f,
                center = Offset(centerX, centerY)
            )

            // Draw Local Node (Center)
            drawCircle(
                color = EmergencyTeal,
                radius = 28.dp.toPx(),
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = EmergencyTeal.copy(alpha = 0.1f),
                radius = 28.dp.toPx(),
                center = Offset(centerX, centerY)
            )
            
            drawText(
                textMeasurer = textMeasurer,
                text = "YOU\n$localNodeId",
                topLeft = Offset(centerX - 35.dp.toPx(), centerY - 16.dp.toPx()),
                style = TextStyle(
                    color = TextPrimary, 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Black,
                    lineHeight = 12.sp
                )
            )

            // Draw Neighbors
            activeNeighbors.forEachIndexed { index, neighbor ->
                val angle = (2 * PI * index / activeNeighbors.size).toFloat()
                
                // Orbital rotation
                val orbitAngle = angle + (animTime * 0.2f)
                
                val x = centerX + radius * cos(orbitAngle)
                val y = centerY + radius * sin(orbitAngle)
                val nodePos = Offset(x, y)
                
                val isConnected = neighbor.connectionState == "CONNECTED" || 
                                neighbor.connectionState == "ACK_RECEIVED" ||
                                neighbor.connectionState == "HELLO_RECEIVED"
                
                val nodeColor = if (isConnected) EmergencyTeal else EmergencyAmber
                
                // Pulsing animation
                val pulse = if (isConnected) (sin(animTime * 4f).toFloat() + 1f) * 0.15f else 0f
                
                // Connection line
                drawLine(
                    color = nodeColor.copy(alpha = 0.3f + pulse),
                    start = Offset(centerX, centerY),
                    end = nodePos,
                    strokeWidth = (1.5.dp.toPx() * (1 + pulse)),
                    pathEffect = if (!isConnected) PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f) else null
                )
                
                // Data transmission particles (moving dots)
                if (isConnected) {
                    val particlePos = (animTime * 0.5f) % 1.0f
                    val px = centerX + (x - centerX) * particlePos
                    val py = centerY + (y - centerY) * particlePos
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = 3.dp.toPx(),
                        center = Offset(px, py)
                    )
                }

                // Neighbor Node
                drawCircle(
                    color = nodeColor,
                    radius = 20.dp.toPx(),
                    center = nodePos,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = nodeColor.copy(alpha = 0.1f + pulse),
                    radius = 20.dp.toPx(),
                    center = nodePos
                )
                
                drawText(
                    textMeasurer = textMeasurer,
                    text = neighbor.peerNodeId?.take(10) ?: "DISCOVERING",
                    topLeft = Offset(x - 35.dp.toPx(), y + 25.dp.toPx()),
                    style = TextStyle(
                        color = TextPrimary, 
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
