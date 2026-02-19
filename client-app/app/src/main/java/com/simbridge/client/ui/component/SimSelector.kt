package com.simbridge.client.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.simbridge.client.data.SimInfo

/**
 * Horizontal toggle group for selecting which SIM to use on the Host phone.
 */
@Composable
fun SimSelector(
    sims: List<SimInfo>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sims.isEmpty()) return

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sims.forEach { sim ->
            FilterChip(
                selected = selected == sim.slot,
                onClick = { onSelect(sim.slot) },
                label = { Text("SIM ${sim.slot} · ${sim.carrier}") },
            )
        }
    }
}
