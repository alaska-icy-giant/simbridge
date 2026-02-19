package com.simbridge.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simbridge.client.data.CallState
import com.simbridge.client.service.ClientService
import com.simbridge.client.ui.component.SimSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(service: ClientService?, onBack: () -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var selectedSim by remember { mutableStateOf(1) }
    var sims by remember { mutableStateOf(service?.hostSims ?: emptyList()) }
    var callState by remember { mutableStateOf(service?.callState ?: CallState.IDLE) }
    var callNumber by remember { mutableStateOf(service?.callNumber) }

    DisposableEffect(service) {
        service?.onSimsUpdated = { sims = it }
        service?.onCallStateChange = { state, number ->
            callState = state
            callNumber = number
        }
        sims = service?.hostSims ?: emptyList()
        callState = service?.callState ?: CallState.IDLE
        onDispose {
            service?.onSimsUpdated = null
            service?.onCallStateChange = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SimSelector(sims = sims, selected = selectedSim, onSelect = { selectedSim = it })
            Spacer(Modifier.height(24.dp))

            if (callState == CallState.IDLE) {
                // Dialer
                OutlinedTextField(
                    value = phoneNumber, onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                Spacer(Modifier.height(32.dp))

                FloatingActionButton(
                    onClick = {
                        if (phoneNumber.isNotBlank()) {
                            service?.commandSender?.makeCall(selectedSim, phoneNumber)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Default.Call, "Call", tint = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                // Active call UI
                Spacer(Modifier.height(48.dp))
                Text(
                    callNumber ?: phoneNumber,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when (callState) {
                        CallState.DIALING -> "Dialing..."
                        CallState.RINGING -> "Ringing..."
                        CallState.ACTIVE -> "In Call"
                        CallState.IDLE -> ""
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(48.dp))

                FloatingActionButton(
                    onClick = { service?.commandSender?.hangUp() },
                    containerColor = MaterialTheme.colorScheme.error,
                ) {
                    Icon(Icons.Default.CallEnd, "Hang Up",
                        tint = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }
}
