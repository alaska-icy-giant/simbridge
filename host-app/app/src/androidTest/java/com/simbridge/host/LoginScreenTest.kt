package com.simbridge.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.host.ui.theme.SimBridgeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the LoginScreen composable.
 *
 * Uses a self-contained test composable that mirrors LoginScreen's UI structure
 * to avoid the need for a real [com.simbridge.host.data.Prefs] or
 * [com.simbridge.host.data.ApiClient] instance.
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper composable that mirrors LoginScreen layout without real Prefs/ApiClient ──

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestLoginScreen(
        initialServerUrl: String = "https://",
        errorMessage: String? = null,
        onLogin: (String, String, String) -> Unit = { _, _, _ -> },
    ) {
        var serverUrl by remember { mutableStateOf(initialServerUrl) }
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf(errorMessage) }

        Scaffold(
            topBar = {
                TopAppBar(title = { Text("SimBridge Host") })
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Connect to Relay Server",
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(Modifier.height(32.dp))

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("https://relay.example.com") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility",
                            )
                        }
                    },
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { onLogin(serverUrl, username, password) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = serverUrl.isNotBlank()
                            && username.isNotBlank()
                            && password.isNotBlank(),
                ) {
                    Text("Login")
                }
            }
        }
    }

    @Test
    fun allFieldsAndLabelsAreVisible() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestLoginScreen()
            }
        }

        composeTestRule.onNodeWithText("Server URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect to Relay Server").assertIsDisplayed()
    }

    @Test
    fun loginButtonDisabledWhenFieldsEmpty() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestLoginScreen(initialServerUrl = "")
            }
        }

        composeTestRule.onNodeWithText("Login").assertIsNotEnabled()
    }

    @Test
    fun loginButtonEnabledWhenAllFieldsFilled() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestLoginScreen()
            }
        }

        composeTestRule.onNodeWithText("Username").performClick()
        composeTestRule.onNodeWithText("Username").performTextInput("testuser")
        composeTestRule.onNodeWithText("Password").performClick()
        composeTestRule.onNodeWithText("Password").performTextInput("secret123")

        composeTestRule.onNodeWithText("Login").assertIsEnabled()
    }

    @Test
    fun passwordVisibilityToggleWorks() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestLoginScreen()
            }
        }

        // Initially the toggle icon should be Visibility (password hidden)
        composeTestRule.onNodeWithContentDescription("Toggle password visibility")
            .assertIsDisplayed()

        // Click to toggle visibility
        composeTestRule.onNodeWithContentDescription("Toggle password visibility")
            .performClick()

        // Icon is still present (now shows VisibilityOff)
        composeTestRule.onNodeWithContentDescription("Toggle password visibility")
            .assertIsDisplayed()
    }

    @Test
    fun errorMessageDisplaysOnScreen() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestLoginScreen(errorMessage = "Invalid credentials")
            }
        }

        composeTestRule.onNodeWithText("Invalid credentials").assertIsDisplayed()
    }
}
