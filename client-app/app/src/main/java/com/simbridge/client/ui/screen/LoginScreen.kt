package com.simbridge.client.ui.screen

import android.os.Build
import androidx.biometric.BiometricManager
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
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.simbridge.client.data.ApiClient
import com.simbridge.client.data.Prefs
import com.simbridge.client.data.SecureTokenStore
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
    var showBiometricOffer by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val api = remember { ApiClient(prefs) }
    val secureTokenStore = remember { SecureTokenStore(context) }

    val canUseBiometric = remember {
        val biometricManager = BiometricManager.from(context)
        biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

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
                                if (canUseBiometric && !prefs.biometricEnabled) {
                                    showBiometricOffer = true
                                } else {
                                    onLoginSuccess()
                                }
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

            // Divider
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "  or  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            // Google Sign-In button
            OutlinedButton(
                onClick = {
                    isLoading = true; error = null
                    scope.launch {
                        try {
                            val credentialManager = CredentialManager.create(context)
                            val googleIdOption = GetGoogleIdOption.Builder()
                                .setFilterByAuthorizedAccounts(false)
                                .setServerClientId(
                                    context.getString(
                                        context.resources.getIdentifier(
                                            "google_client_id", "string", context.packageName
                                        )
                                    )
                                )
                                .build()
                            val credRequest = GetCredentialRequest.Builder()
                                .addCredentialOption(googleIdOption)
                                .build()
                            val result = withContext(Dispatchers.IO) {
                                credentialManager.getCredential(context, credRequest)
                            }
                            val googleIdTokenCredential = GoogleIdTokenCredential
                                .createFrom(result.credential.data)
                            val idToken = googleIdTokenCredential.idToken

                            val url = serverUrl.trimEnd('/')
                            val loginResult = withContext(Dispatchers.IO) {
                                api.googleLogin(url, idToken)
                            }
                            loginResult.fold(
                                onSuccess = { auth ->
                                    prefs.serverUrl = url
                                    prefs.token = auth.token
                                    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                                    val devResult = withContext(Dispatchers.IO) { api.registerDevice(deviceName) }
                                    devResult.onSuccess { d -> prefs.deviceId = d.id; prefs.deviceName = d.name }
                                    isLoading = false
                                    if (canUseBiometric && !prefs.biometricEnabled) {
                                        showBiometricOffer = true
                                    } else {
                                        onLoginSuccess()
                                    }
                                },
                                onFailure = { e -> isLoading = false; error = e.message ?: "Google login failed" },
                            )
                        } catch (e: Exception) {
                            isLoading = false; error = e.message ?: "Google Sign-In failed"
                            Log.e("LoginScreen", "Google Sign-In failed", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && serverUrl.isNotBlank(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Sign in with Google")
            }
        }
    }

    if (showBiometricOffer) {
        AlertDialog(
            onDismissRequest = {
                showBiometricOffer = false
                onLoginSuccess()
            },
            title = { Text("Enable Biometric Unlock?") },
            text = { Text("Use fingerprint or face recognition to unlock SimBridge next time.") },
            confirmButton = {
                TextButton(onClick = {
                    secureTokenStore.saveToken(prefs.token)
                    prefs.biometricEnabled = true
                    showBiometricOffer = false
                    onLoginSuccess()
                }) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBiometricOffer = false
                    onLoginSuccess()
                }) {
                    Text("Not now")
                }
            },
        )
    }
}
