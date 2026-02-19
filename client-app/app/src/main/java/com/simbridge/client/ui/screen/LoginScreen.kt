package com.simbridge.client.ui.screen

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.simbridge.client.data.ApiClient
import com.simbridge.client.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(prefs: Prefs, onLoginSuccess: () -> Unit) {
    var serverUrl by remember { mutableStateOf(prefs.serverUrl.ifBlank { "https://" }) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val api = remember { ApiClient(prefs) }

    Scaffold(topBar = { TopAppBar(title = { Text("SimBridge Client") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Connect to Relay Server", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = serverUrl, onValueChange = { serverUrl = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://relay.example.com") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = "Toggle",
                        )
                    }
                },
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
                        val url = serverUrl.trimEnd('/')
                        val result = withContext(Dispatchers.IO) { api.login(url, username, password) }
                        result.fold(
                            onSuccess = { auth ->
                                prefs.serverUrl = url
                                prefs.token = auth.token
                                val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                                val devResult = withContext(Dispatchers.IO) { api.registerDevice(deviceName) }
                                devResult.onSuccess { d -> prefs.deviceId = d.id; prefs.deviceName = d.name }
                                isLoading = false
                                onLoginSuccess()
                            },
                            onFailure = { e -> isLoading = false; error = e.message ?: "Login failed" },
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Login")
            }
        }
    }
}
