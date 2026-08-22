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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
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
import com.rescuemesh.app.identity.NodeIdentityProvider
import com.rescuemesh.app.mesh.MeshCoordinator
import com.rescuemesh.app.ui.screens.HomeScreen
import com.rescuemesh.app.ui.screens.MessagesScreen
import com.rescuemesh.app.ui.screens.NeighborsScreen
import com.rescuemesh.app.ui.screens.SettingsScreen
import com.rescuemesh.app.ui.screens.SosScreen
import com.rescuemesh.app.ui.screens.TopologyScreen
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
            advertiser = BleAdvertiser(context),
            scanner = BleScanner(context),
            gattServer = GattServer(context, identityProvider.nodeId),
            gattClient = GattClient(context, identityProvider.nodeId),
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
                val items = listOf(Screen.Home, Screen.Neighbors, Screen.Messages, Screen.Topology, Screen.Settings)
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
                    onSendSos = { coordinator.createSos(it) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Neighbors.route) {
                NeighborsScreen(uiState.neighbors)
            }
            composable(Screen.Messages.route) {
                MessagesScreen(uiState.sosMessages)
            }
            composable(Screen.Topology.route) {
                TopologyScreen(uiState.nodeId, uiState.neighbors)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    state = uiState,
                    currentNodeName = nodeName,
                    onSaveName = { newName ->
                        identityProvider.nodeName = newName
                        nodeName = newName
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
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
