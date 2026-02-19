package com.simbridge.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.simbridge.client.data.LogEntry
import com.simbridge.client.service.ClientService
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(service: ClientService?, onBack: () -> Unit) {
    var logs by remember { mutableStateOf(service?.logs ?: emptyList()) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    DisposableEffect(service) {
        service?.onLogEntry = { logs = service.logs }
        logs = service?.logs ?: emptyList()
        onDispose { service?.onLogEntry = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        if (logs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp)) {
                Text("No events yet.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(logs, key = { it.timestamp }) { entry ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(dateFormat.format(Date(entry.timestamp)),
                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(entry.direction, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (entry.direction == "OUT") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(8.dp))
                        Text(entry.summary, style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
