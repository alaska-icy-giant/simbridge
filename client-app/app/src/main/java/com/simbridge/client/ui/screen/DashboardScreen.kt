package com.simbridge.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simbridge.client.data.ConnectionStatus
import com.simbridge.client.data.SimInfo
import com.simbridge.client.service.ClientService
import com.simbridge.client.ui.component.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    service: ClientService?,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onNavigateToSms: () -> Unit,
    onNavigateToDialer: () -> Unit,
    onNavigateToLog: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    var status by remember { mutableStateOf(service?.connectionStatus ?: ConnectionStatus.DISCONNECTED) }
    var isRunning by remember { mutableStateOf(service != null) }
    var sims by remember { mutableStateOf(service?.hostSims ?: emptyList()) }

    DisposableEffect(service) {
        service?.onStatusChange = { status = it }
        service?.onSimsUpdated = { sims = it }
        status = service?.connectionStatus ?: ConnectionStatus.DISCONNECTED
        isRunning = service != null
        sims = service?.hostSims ?: emptyList()
        onDispose {
            service?.onStatusChange = null
            service?.onSimsUpdated = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SimBridge Client") },
                actions = {
                    IconButton(onClick = onNavigateToLog) {
                        Icon(Icons.Default.List, "Logs")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(status = status)

            // Start / Stop
            Button(
                onClick = {
                    if (isRunning) { onStopService(); isRunning = false; status = ConnectionStatus.DISCONNECTED }
                    else { onStartService(); isRunning = true }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isRunning) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                else ButtonDefaults.buttonColors(),
            ) {
                Text(if (isRunning) "Stop Service" else "Start Service")
            }

            // Host SIM info
            if (sims.isNotEmpty()) {
                Text("Host SIM Cards", style = MaterialTheme.typography.titleMedium)
                sims.forEach { sim ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp)) {
                            Icon(Icons.Default.SimCard, "SIM", tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("SIM ${sim.slot} — ${sim.carrier}", style = MaterialTheme.typography.titleSmall)
                                if (!sim.number.isNullOrBlank()) {
                                    Text(sim.number, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ElevatedButton(
                    onClick = onNavigateToSms,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Message, "SMS", Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SMS")
                }
                ElevatedButton(
                    onClick = onNavigateToDialer,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Call, "Call", Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Call")
                }
            }
        }
    }
}
