package com.simbridge.client.ui.screen

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.simbridge.client.data.Prefs
import com.simbridge.client.data.SecureTokenStore

@Composable
fun BiometricPromptScreen(
    prefs: Prefs,
    secureTokenStore: SecureTokenStore,
    onSuccess: () -> Unit,
    onFallbackToLogin: () -> Unit,
) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val activity = context as? FragmentActivity ?: run {
            onFallbackToLogin()
            return@LaunchedEffect
        }

        val executor = ContextCompat.getMainExecutor(context)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val token = secureTokenStore.getToken()
                if (token != null) {
                    prefs.token = token
                    onSuccess()
                } else {
                    prefs.biometricEnabled = false
                    secureTokenStore.clear()
                    onFallbackToLogin()
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onFallbackToLogin()
                } else {
                    errorMessage = errString.toString()
                }
            }

            override fun onAuthenticationFailed() {
                errorMessage = "Authentication failed. Try again."
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SimBridge")
            .setSubtitle("Verify your identity to continue")
            .setNegativeButtonText("Use password")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Biometric Unlock",
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Waiting for biometric authentication…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(32.dp))

            OutlinedButton(onClick = onFallbackToLogin) {
                Text("Use password instead")
            }
        }
    }
}
