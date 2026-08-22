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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rescuemesh.app.ble.BleAdvertiser
import com.rescuemesh.app.ble.BleScanner
import com.rescuemesh.app.ble.GattClient
import com.rescuemesh.app.ble.GattServer
import com.rescuemesh.app.data.AppDatabase
import com.rescuemesh.app.data.MessageRepository
import com.rescuemesh.app.identity.LocationProvider
import com.rescuemesh.app.identity.NodeIdentityProvider
import com.rescuemesh.app.identity.SecurityProvider
import com.rescuemesh.app.identity.SensorProvider
import com.rescuemesh.app.mesh.MeshCoordinator
import com.rescuemesh.app.ui.screens.*
import com.rescuemesh.app.ui.theme.RescueMeshTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RescueMeshTheme {
                RescueMeshApp()
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object SOS : Screen("sos", "SOS", Icons.Default.Info)
    object Neighbors : Screen("neighbors", "Nearby", Icons.Default.Person)
    object Messages : Screen("messages", "Messages", Icons.Default.List)
    object Topology : Screen("topology", "Topology", Icons.Default.LocationOn)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Diagnostics : Screen("diagnostics", "Debug", Icons.Default.Build)
}

@Composable
private fun RescueMeshApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    
    val coordinator = remember {
        val identityProvider = NodeIdentityProvider(context)
        val repository = MessageRepository(AppDatabase.get(context).messageDao())
        MeshCoordinator(
            identityProvider = identityProvider,
            securityProvider = SecurityProvider(),
            locationProvider = LocationProvider(context),
            sensorProvider = SensorProvider(context),
            advertiser = BleAdvertiser(context),
            scanner = BleScanner(context),
            gattServer = GattServer(context, identityProvider.nodeId, identityProvider),
            gattClient = GattClient(context, identityProvider.nodeId, identityProvider),
            repository = repository,
            scope = scope,
        )
    }
    
    val uiState by coordinator.uiState.collectAsStateWithLifecycle()
    val identityProvider = remember { NodeIdentityProvider(context) }
    var nodeName by remember { mutableStateOf(identityProvider.nodeName) }
    
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

    LaunchedEffect(Unit) {
        while (true) {
            coordinator.refreshNeighborLiveness()
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            coordinator.stopDiscovery()
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            
            NavigationBar {
                val items = listOf(Screen.Home, Screen.Neighbors, Screen.Messages, Screen.Topology, Screen.Diagnostics, Screen.Settings)
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) {
                HomeScreen(
                    state = uiState,
                    permissionsGranted = hasPermissions,
                    discoveryRequested = discoveryRequested,
                    localRole = identityProvider.nodeRole,
                    onNavigateToSos = { navController.navigate(Screen.SOS.route) },
                    onToggleDiscovery = {
                        if (!hasPermissions) {
                            discoveryRequested = true
                            permissionLauncher.launch(requiredBlePermissions())
                        } else {
                            discoveryRequested = !discoveryRequested
                            if (discoveryRequested) coordinator.startDiscovery() else coordinator.stopDiscovery()
                        }
                    }
                )
            }
            composable(Screen.SOS.route) {
                SosScreen(
                    onSendSos = { text, priority, category -> 
                        coordinator.createSos(text, priority, category) 
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Neighbors.route) {
                NeighborsScreen(uiState.neighbors)
            }
            composable(Screen.Messages.route) {
                MessagesScreen(uiState.sosMessages, identityProvider.nodeRole)
            }
            composable(Screen.Topology.route) {
                TopologyScreen(uiState.nodeId, uiState.neighbors)
            }
            composable(Screen.Diagnostics.route) {
                DiagnosticsScreen(uiState, onSetPowerMode = { coordinator.setPowerMode(it) })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    state = uiState,
                    currentNodeName = nodeName,
                    currentNodeRole = identityProvider.nodeRole,
                    onSaveName = { newName ->
                        identityProvider.nodeName = newName
                        nodeName = newName
                    },
                    onSaveRole = { newRole ->
                        identityProvider.nodeRole = newRole
                        if (discoveryRequested) {
                            coordinator.stopDiscovery()
                            coordinator.startDiscovery()
                        }
                    }
                )
            }
        }
    }
}

private fun Context.hasBlePermissions(): Boolean {
    return requiredBlePermissions().all { permission ->
        val granted = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        android.util.Log.d("PERM", "Permission $permission granted: $granted")
        granted
    }
}

private fun requiredBlePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
