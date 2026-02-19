package com.simbridge.client

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.simbridge.client.data.Prefs
import com.simbridge.client.ui.screen.LoginScreen
import com.simbridge.client.ui.theme.SimBridgeClientTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var prefs: Prefs

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = Prefs(context)
        prefs.clear()
    }

    private fun setContent() {
        rule.setContent {
            SimBridgeClientTheme {
                LoginScreen(prefs = prefs, onLoginSuccess = {})
            }
        }
    }

    // ── Fields Visible ──

    @Test
    fun serverUrlFieldIsVisible() {
        setContent()
        rule.onNodeWithText("Server URL").assertIsDisplayed()
    }

    @Test
    fun usernameFieldIsVisible() {
        setContent()
        rule.onNodeWithText("Username").assertIsDisplayed()
    }

    @Test
    fun passwordFieldIsVisible() {
        setContent()
        rule.onNodeWithText("Password").assertIsDisplayed()
    }

    @Test
    fun loginButtonIsVisible() {
        setContent()
        rule.onNodeWithText("Login").assertIsDisplayed()
    }

    @Test
    fun titleIsVisible() {
        setContent()
        rule.onNodeWithText("Connect to Relay Server").assertIsDisplayed()
    }

    // ── Button Disabled When Fields Empty ──

    @Test
    fun loginButtonDisabledWhenUsernameAndPasswordEmpty() {
        setContent()
        rule.onNodeWithText("Login").assertIsNotEnabled()
    }

    @Test
    fun loginButtonDisabledWhenOnlyUsernameEntered() {
        setContent()
        rule.onNodeWithText("Username").performTextInput("admin")
        rule.onNodeWithText("Login").assertIsNotEnabled()
    }

    @Test
    fun loginButtonDisabledWhenOnlyPasswordEntered() {
        setContent()
        rule.onNodeWithText("Password").performTextInput("secret")
        rule.onNodeWithText("Login").assertIsNotEnabled()
    }

    @Test
    fun loginButtonEnabledWhenAllFieldsFilled() {
        setContent()
        rule.onNodeWithText("Username").performTextInput("admin")
        rule.onNodeWithText("Password").performTextInput("secret")
        rule.onNodeWithText("Login").assertIsEnabled()
    }

    // ── Password Toggle ──

    @Test
    fun passwordToggleButtonExists() {
        setContent()
        rule.onNodeWithContentDescription("Toggle").assertIsDisplayed()
    }

    @Test
    fun passwordToggleCanBeClicked() {
        setContent()
        rule.onNodeWithContentDescription("Toggle").performClick()
        // Should not crash; second click toggles back
        rule.onNodeWithContentDescription("Toggle").performClick()
    }
}
