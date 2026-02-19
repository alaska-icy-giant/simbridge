package com.simbridge.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simbridge.client.data.SimInfo
import com.simbridge.client.data.SmsEntry
import com.simbridge.client.service.ClientService
import com.simbridge.client.ui.component.SimSelector
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(service: ClientService?, onBack: () -> Unit) {
    var phoneNumber by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var selectedSim by remember { mutableStateOf(1) }
    var sims by remember { mutableStateOf(service?.hostSims ?: emptyList()) }
    var history by remember { mutableStateOf(service?.smsHistory ?: emptyList()) }
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // C-06: Capture service in a local val so dispose always cleans up the correct instance
    DisposableEffect(service) {
        val svc = service
        svc?.onSimsUpdated = { sims = it }
        svc?.onSmsReceived = { history = svc.smsHistory }
        sims = svc?.hostSims ?: emptyList()
        history = svc?.smsHistory ?: emptyList()
        onDispose {
            svc?.onSimsUpdated = null
            svc?.onSmsReceived = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send SMS") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // SIM selector
            SimSelector(sims = sims, selected = selectedSim, onSelect = { selectedSim = it })
            Spacer(Modifier.height(12.dp))

            // Phone number
            OutlinedTextField(
                value = phoneNumber, onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))

            // Message body + send button
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = messageBody, onValueChange = { messageBody = it },
                    label = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (phoneNumber.isNotBlank() && messageBody.isNotBlank()) {
                            service?.commandSender?.sendSms(selectedSim, phoneNumber, messageBody)
                            messageBody = ""
                        }
                    },
                    enabled = phoneNumber.isNotBlank() && messageBody.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send",
                        tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Text("Recent Messages", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            // SMS history
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(history, key = { it.timestamp }) { sms ->
                    SmsEntryRow(sms, dateFormat)
                }
            }
        }
    }
}

@Composable
private fun SmsEntryRow(sms: SmsEntry, dateFormat: SimpleDateFormat) {
    val isIncoming = sms.direction == "received"
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isIncoming) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (isIncoming) "From: ${sms.address}" else "To: ${sms.address}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    dateFormat.format(Date(sms.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(sms.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
