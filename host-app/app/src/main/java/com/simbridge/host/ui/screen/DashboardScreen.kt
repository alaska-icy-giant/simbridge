package com.simbridge.host.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.simbridge.host.data.ConnectionStatus
import com.simbridge.host.data.SimInfo
import com.simbridge.host.service.BridgeService
import com.simbridge.host.service.SimInfoProvider
import com.simbridge.host.ui.component.SimCard
import com.simbridge.host.ui.component.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    service: BridgeService?,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onNavigateToLog: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val context = LocalContext.current
    var connectionStatus by remember { mutableStateOf(service?.connectionStatus ?: ConnectionStatus.DISCONNECTED) }
    var isServiceRunning by remember { mutableStateOf(service != null) }

    val simInfoProvider = remember { SimInfoProvider(context) }
    var sims by remember { mutableStateOf<List<SimInfo>>(emptyList()) }

    // Observe service status
    DisposableEffect(service) {
        service?.onStatusChange = { status ->
            connectionStatus = status
        }
        connectionStatus = service?.connectionStatus ?: ConnectionStatus.DISCONNECTED
        isServiceRunning = service != null
        onDispose {
            service?.onStatusChange = null
        }
    }

    // Load SIM info
    LaunchedEffect(Unit) {
        sims = simInfoProvider.getActiveSimCards()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SimBridge Host") },
                actions = {
                    IconButton(onClick = onNavigateToLog) {
                        Icon(Icons.Default.List, contentDescription = "Logs")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Connection status
            StatusCard(status = connectionStatus)

            // Start/Stop button
            Button(
                onClick = {
                    if (isServiceRunning) {
                        onStopService()
                        isServiceRunning = false
                        connectionStatus = ConnectionStatus.DISCONNECTED
                    } else {
                        onStartService()
                        isServiceRunning = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isServiceRunning) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(if (isServiceRunning) "Stop Service" else "Start Service")
            }

            // SIM info
            if (sims.isNotEmpty()) {
                Text(
                    text = "SIM Cards",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                sims.forEach { sim ->
                    SimCard(sim = sim)
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                ) {
                    Text(
                        text = "No SIM cards detected. Grant phone permissions to see SIM info.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
