package com.simbridge.host.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simbridge.host.data.ConnectionStatus

@Composable
fun StatusCard(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
) {
    val (icon, label, color) = when (status) {
        ConnectionStatus.CONNECTED -> Triple(
            Icons.Default.CheckCircle, "Connected",
            MaterialTheme.colorScheme.primary
        )
        ConnectionStatus.CONNECTING -> Triple(
            Icons.Default.Cloud, "Connecting...",
            MaterialTheme.colorScheme.tertiary
        )
        ConnectionStatus.DISCONNECTED -> Triple(
            Icons.Default.CloudOff, "Offline",
            MaterialTheme.colorScheme.error
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = color,
            )
        }
    }
}
