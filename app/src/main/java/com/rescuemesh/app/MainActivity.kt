package com.rescuemesh.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rescuemesh.app.ble.AdvertiserState
import com.rescuemesh.app.ble.BleAdvertiser
import com.rescuemesh.app.ble.BleScanner
import com.rescuemesh.app.ble.GattClient
import com.rescuemesh.app.ble.GattServer
import com.rescuemesh.app.ble.ScannerState
import com.rescuemesh.app.data.AppDatabase
import com.rescuemesh.app.data.MessageRepository
import com.rescuemesh.app.identity.NodeIdentityProvider
import com.rescuemesh.app.mesh.MeshCoordinator
import com.rescuemesh.app.mesh.MeshDiscoveryUiState
import com.rescuemesh.app.mesh.NeighborLiveness
import com.rescuemesh.app.mesh.NeighborUiModel
import com.rescuemesh.app.mesh.SosUiModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RescueMeshApp()
        }
    }
}

@Composable
private fun RescueMeshApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coordinator = remember {
        val identityProvider = NodeIdentityProvider(context)
        val repository = MessageRepository(AppDatabase.get(context).messageDao())
        MeshCoordinator(
            identityProvider = identityProvider,
            advertiser = BleAdvertiser(context),
            scanner = BleScanner(context),
            gattServer = GattServer(context, identityProvider.nodeId),
            gattClient = GattClient(context, identityProvider.nodeId),
            repository = repository,
            scope = scope,
        )
    }
    val uiState by coordinator.uiState.collectAsStateWithLifecycle()
    var hasPermissions by remember { mutableStateOf(context.hasBlePermissions()) }
    var discoveryRequested by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasPermissions = grants.values.all { it } || context.hasBlePermissions()
        if (hasPermissions && discoveryRequested) {
            coordinator.startDiscovery()
        }
    }

    LaunchedEffect(discoveryRequested, hasPermissions) {
        if (discoveryRequested && hasPermissions) {
            coordinator.startDiscovery()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            coordinator.refreshNeighborLiveness()
            delay(1_000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            coordinator.stopDiscovery()
        }
    }

    RescueMeshTheme {
        DiscoveryScreen(
            state = uiState,
            permissionsGranted = hasPermissions,
            discoveryRequested = discoveryRequested,
            onPrimaryAction = {
                if (!hasPermissions) {
                    discoveryRequested = true
                    permissionLauncher.launch(requiredBlePermissions())
                } else {
                    discoveryRequested = !discoveryRequested
                    if (discoveryRequested) {
                        coordinator.startDiscovery()
                    } else {
                        coordinator.stopDiscovery()
                    }
                }
            },
            onCreateSos = coordinator::createSos,
        )
    }
}

@Composable
private fun RescueMeshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.darkColorScheme(
            primary = Color(0xFF8EF7D4),
            secondary = Color(0xFFFFD166),
            tertiary = Color(0xFFB9A7FF),
            background = Color(0xFF090B10),
            surface = Color(0xFF111722),
            onPrimary = Color(0xFF04120E),
            onBackground = Color(0xFFF4F7FB),
            onSurface = Color(0xFFF4F7FB),
        ),
        content = content,
    )
}

@Composable
private fun DiscoveryScreen(
    state: MeshDiscoveryUiState,
    permissionsGranted: Boolean,
    discoveryRequested: Boolean,
    onPrimaryAction: () -> Unit,
    onCreateSos: (String) -> Unit,
) {
    var sosText by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF090B10),
                        Color(0xFF10211C),
                        Color(0xFF1B1727),
                        Color(0xFF090B10),
                    ),
                ),
            ),
    ) {
        LightRayBackdrop()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                HeaderCard(
                    state = state,
                    permissionsGranted = permissionsGranted,
                    discoveryRequested = discoveryRequested,
                    onPrimaryAction = onPrimaryAction,
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatusTile(
                        label = "Advertise",
                        value = advertiserLabel(state.advertiserState),
                        accent = statusColor(state.advertiserState),
                        modifier = Modifier.weight(1f),
                    )
                    StatusTile(
                        label = "Scan",
                        value = scannerLabel(state.scannerState),
                        accent = statusColor(state.scannerState),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                StatusTile(
                    label = "Nearby active nodes",
                    value = state.activeNeighborCount.toString(),
                    accent = Color(0xFF8EF7D4),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SosComposerCard(
                    text = sosText,
                    onTextChange = { sosText = it.take(220) },
                    onSend = {
                        val text = sosText.trim()
                        if (text.isNotEmpty()) {
                            onCreateSos(text)
                            sosText = ""
                        }
                    },
                )
            }
            item {
                SectionTitle("Nearby BLE Devices")
            }
            if (state.neighbors.isEmpty()) {
                item {
                    EmptyNeighborsCard(discoveryRequested, permissionsGranted)
                }
            } else {
                items(
                    items = state.neighbors,
                    key = { it.sessionId },
                ) { neighbor ->
                    NeighborCard(neighbor)
                }
            }
            item {
                SectionTitle("Stored SOS Messages")
            }
            if (state.sosMessages.isEmpty()) {
                item {
                    EmptySosCard()
                }
            } else {
                items(
                    items = state.sosMessages,
                    key = { it.id },
                ) { message ->
                    SosMessageCard(message)
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(
    state: MeshDiscoveryUiState,
    permissionsGranted: Boolean,
    discoveryRequested: Boolean,
    onPrimaryAction: () -> Unit,
) {
    GlassPanel {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "RescueMesh",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Offline BLE emergency mesh",
                color = Color(0xFFB9C8D8),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusPill(
                    label = if (permissionsGranted) "Permissions ready" else "Permissions needed",
                    accent = if (permissionsGranted) Color(0xFF8EF7D4) else Color(0xFFFFD166),
                )
                StatusPill(
                    label = if (discoveryRequested) "Live" else "Paused",
                    accent = if (discoveryRequested) Color(0xFFB9A7FF) else Color(0xFF7B8794),
                )
                StatusPill(
                    label = "No internet",
                    accent = Color(0xFF8EF7D4),
                )
            }
            StatusTile(
                label = "This device",
                value = state.nodeId,
                accent = Color(0xFF8EF7D4),
                modifier = Modifier.fillMaxWidth(),
            )
            StatusTile(
                label = "Mesh status",
                value = when {
                    !permissionsGranted -> "Needs BLE permissions"
                    discoveryRequested && state.activeNeighborCount > 0 -> "Peers nearby"
                    discoveryRequested -> "Scanning offline"
                    else -> "Paused"
                },
                accent = Color(0xFFB9A7FF),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (discoveryRequested) Color(0xFF27303D) else Color(0xFF8EF7D4),
                    contentColor = if (discoveryRequested) Color(0xFFF4F7FB) else Color(0xFF04120E),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(if (!permissionsGranted) "Grant BLE permissions" else if (discoveryRequested) "Stop discovery" else "Start discovery")
            }
        }
    }
}

@Composable
private fun StatusTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(label, color = Color(0xFFB9C8D8), style = MaterialTheme.typography.labelMedium)
            Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .height(2.dp)
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(accent, Color.Transparent)),
                    ),
            )
        }
    }
}

@Composable
private fun NeighborCard(neighbor: NeighborUiModel) {
    val accent = when (neighbor.liveness) {
        NeighborLiveness.Active -> Color(0xFF8EF7D4)
        NeighborLiveness.Stale -> Color(0xFFFFD166)
        NeighborLiveness.Lost -> Color(0xFFFF6B6B)
    }

    GlassPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.18f))
                    .border(BorderStroke(1.dp, accent.copy(alpha = 0.7f)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = neighbor.peerNodeId ?: neighbor.name?.takeIf { it.isNotBlank() } ?: "RescueMesh peer",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (neighbor.peerNodeId == null) "Node ID pending HELLO exchange" else neighbor.connectionState,
                    color = Color(0xFFB9C8D8),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "RSSI ${neighbor.rssi} dBm | ${neighbor.liveness.name} | ${ageLabel(neighbor.lastSeenElapsedMs)}",
                    color = Color(0xFFD8E1EC),
                    style = MaterialTheme.typography.bodySmall,
                )
                neighbor.lastPacketState?.let { packetState ->
                    Text(
                        text = packetState,
                        color = Color(0xFF8EF7D4),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SosComposerCard(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    GlassPanel {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Create SOS", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Need medical assistance near gate 2") },
                maxLines = 3,
            )
            Button(
                onClick = onSend,
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFD166),
                    contentColor = Color(0xFF171003),
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Send offline SOS")
            }
        }
    }
}

@Composable
private fun SosMessageCard(message: SosUiModel) {
    GlassPanel {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(message.text, fontWeight = FontWeight.SemiBold)
            Text(
                text = "${message.id} | ${message.state} | TTL ${message.ttl} | hops ${message.hopCount} | ${relayLabel(message.state)}",
                color = Color(0xFFB9C8D8),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptySosCard() {
    GlassPanel {
        Text(
            text = "No SOS messages stored yet.",
            modifier = Modifier.padding(16.dp),
            color = Color(0xFFB9C8D8),
        )
    }
}

private fun relayLabel(state: String): String {
    return when (state) {
        "PERSISTED" -> "stored locally"
        "QUEUED" -> "ready to relay"
        "RELAYED" -> "accepted by peer"
        else -> "relay status: $state"
    }
}

@Composable
private fun EmptyNeighborsCard(discoveryRequested: Boolean, permissionsGranted: Boolean) {
    GlassPanel {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    !permissionsGranted -> "BLE permissions are required before scanning."
                    !discoveryRequested -> "Discovery is paused."
                    else -> "Scanning for nearby RescueMesh devices."
                },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Only devices advertising the RescueMesh BLE service appear here.",
                color = Color(0xFFB9C8D8),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 6.dp),
        color = Color(0xFFE8EDF4),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StatusPill(label: String, accent: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.45f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                RoundedCornerShape(16.dp),
            ),
        color = Color.White.copy(alpha = 0.08f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

@Composable
private fun LightRayBackdrop() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 18.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                listOf(
                                    Color(0xFF8EF7D4),
                                    Color(0xFFFFD166),
                                    Color(0xFFB9A7FF),
                                )[index % 3].copy(alpha = 0.22f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
    }
}

private fun advertiserLabel(state: AdvertiserState): String {
    return when (state) {
        AdvertiserState.Idle -> "Idle"
        AdvertiserState.Starting -> "Starting"
        AdvertiserState.Advertising -> "Advertising"
        AdvertiserState.PermissionMissing -> "Permission needed"
        is AdvertiserState.Unavailable -> state.reason
        is AdvertiserState.Failed -> state.reason
    }
}

private fun scannerLabel(state: ScannerState): String {
    return when (state) {
        ScannerState.Idle -> "Idle"
        ScannerState.Scanning -> "Scanning"
        ScannerState.PermissionMissing -> "Permission needed"
        is ScannerState.Unavailable -> state.reason
        is ScannerState.Failed -> state.reason
    }
}

private fun statusColor(state: AdvertiserState): Color {
    return when (state) {
        AdvertiserState.Advertising -> Color(0xFF8EF7D4)
        AdvertiserState.Starting -> Color(0xFFFFD166)
        AdvertiserState.Idle -> Color(0xFF7B8794)
        AdvertiserState.PermissionMissing -> Color(0xFFFFD166)
        is AdvertiserState.Unavailable,
        is AdvertiserState.Failed,
        -> Color(0xFFFF6B6B)
    }
}

private fun statusColor(state: ScannerState): Color {
    return when (state) {
        ScannerState.Scanning -> Color(0xFF8EF7D4)
        ScannerState.Idle -> Color(0xFF7B8794)
        ScannerState.PermissionMissing -> Color(0xFFFFD166)
        is ScannerState.Unavailable,
        is ScannerState.Failed,
        -> Color(0xFFFF6B6B)
    }
}

private fun ageLabel(lastSeenElapsedMs: Long): String {
    val ageSeconds = ((android.os.SystemClock.elapsedRealtime() - lastSeenElapsedMs) / 1_000L)
        .coerceAtLeast(0L)
    return if (ageSeconds == 0L) "now" else "${ageSeconds}s ago"
}

private fun Context.hasBlePermissions(): Boolean {
    return requiredBlePermissions().all { permission ->
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requiredBlePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        emptyArray()
    }
}
