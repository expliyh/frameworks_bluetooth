package com.openvela.bluetoothtest.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.openvela.bluetoothtest.R

@Composable
fun MainWearRoute(
    destinations: List<MainDestination>,
    onOpenDestination: (MainDestination) -> Unit
) {
    MaterialTheme {
        val scrollState = rememberScrollState()
        val chipColors = ChipDefaults.primaryChipColors()
        val context = LocalContext.current
        val destinationItems = remember(destinations, context) {
            destinations.map { destination ->
                WearDestinationUiModel(
                    destination = destination,
                    title = context.getString(destination.titleRes),
                    description = context.getString(destination.descriptionRes)
                )
            }
        }
        val appName = remember(context) { context.getString(R.string.app_name) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = appName,
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            destinationItems.forEachIndexed { index, item ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenDestination(item.destination) },
                    label = {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.body1
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.caption3
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = item.destination.icon,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    },
                    colors = chipColors
                )
            }
        }
    }
}

private data class WearDestinationUiModel(
    val destination: MainDestination,
    val title: String,
    val description: String
)
