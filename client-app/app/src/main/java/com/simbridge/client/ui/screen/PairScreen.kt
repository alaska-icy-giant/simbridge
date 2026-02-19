package com.simbridge.client.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simbridge.client.data.ApiClient
import com.simbridge.client.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairScreen(prefs: Prefs, onPaired: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val api = remember { ApiClient(prefs) }

    Scaffold(topBar = { TopAppBar(title = { Text("Pair with Host") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Enter Pairing Code",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Open the Host app on Phone A and generate a pairing code from the dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it.filter { c -> c.isDigit() } },
                label = { Text("6-digit code") },
                modifier = Modifier.width(200.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    isLoading = true; error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { api.confirmPairing(code) }
                        result.fold(
                            onSuccess = { resp ->
                                if (resp.hostDeviceId != null) {
                                    prefs.pairedHostId = resp.hostDeviceId
                                    // Fetch host device name
                                    val devicesResult = withContext(Dispatchers.IO) { api.listDevices() }
                                    devicesResult.onSuccess { devices ->
                                        devices.find { it.id == resp.hostDeviceId }?.let {
                                            prefs.pairedHostName = it.name
                                        }
                                    }
                                    isLoading = false
                                    onPaired()
                                } else {
                                    isLoading = false
                                    error = "Pairing failed: ${resp.status}"
                                }
                            },
                            onFailure = { e -> isLoading = false; error = e.message ?: "Pairing failed" },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && code.length == 6,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Pair")
            }
        }
    }
}
