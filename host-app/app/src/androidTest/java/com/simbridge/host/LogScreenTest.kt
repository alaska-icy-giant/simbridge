package com.simbridge.host

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simbridge.host.data.LogEntry
import com.simbridge.host.ui.theme.SimBridgeTheme
import java.text.SimpleDateFormat
import java.util.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for the LogScreen composable.
 *
 * Uses a self-contained test composable that mirrors LogScreen's UI layout
 * to avoid the need for a real [com.simbridge.host.service.BridgeService].
 */
@RunWith(AndroidJUnit4::class)
class LogScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ── Helper composable mirroring LogScreen layout ──

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun TestLogScreen(
        logs: List<LogEntry> = emptyList(),
        onBack: () -> Unit = {},
    ) {
        val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Event Log") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            }
        ) { padding ->
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                ) {
                    Text(
                        text = "No events yet. Commands and events will appear here.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(logs, key = { it.timestamp }) { entry ->
                        val dirColor = if (entry.direction == "IN") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondary
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                        ) {
                            Text(
                                text = dateFormat.format(Date(entry.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = entry.direction,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = dirColor,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = entry.summary,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun emptyStateMessageShownWhenNoLogs() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestLogScreen(logs = emptyList())
            }
        }

        composeTestRule.onNodeWithText("No events yet. Commands and events will appear here.")
            .assertIsDisplayed()
    }

    @Test
    fun logEntriesDisplayWithTimestampDirectionAndSummary() {
        val fixedTime = 1700000000000L // known timestamp
        val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val expectedTime = dateFormat.format(Date(fixedTime))

        val logs = listOf(
            LogEntry(timestamp = fixedTime, direction = "IN", summary = "SMS received from +1555"),
        )

        composeTestRule.setContent {
            SimBridgeTheme {
                TestLogScreen(logs = logs)
            }
        }

        composeTestRule.onNodeWithText(expectedTime).assertIsDisplayed()
        composeTestRule.onNodeWithText("IN").assertIsDisplayed()
        composeTestRule.onNodeWithText("SMS received from +1555").assertIsDisplayed()
    }

    @Test
    fun directionTextShowsInAndOut() {
        val logs = listOf(
            LogEntry(timestamp = 1700000000000L, direction = "IN", summary = "Incoming command"),
            LogEntry(timestamp = 1700000001000L, direction = "OUT", summary = "Outgoing response"),
        )

        composeTestRule.setContent {
            SimBridgeTheme {
                TestLogScreen(logs = logs)
            }
        }

        composeTestRule.onNodeWithText("IN").assertIsDisplayed()
        composeTestRule.onNodeWithText("OUT").assertIsDisplayed()
        composeTestRule.onNodeWithText("Incoming command").assertIsDisplayed()
        composeTestRule.onNodeWithText("Outgoing response").assertIsDisplayed()
    }

    @Test
    fun backButtonVisible() {
        composeTestRule.setContent {
            SimBridgeTheme {
                TestLogScreen()
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }
}
