package com.openvela.bluetoothtest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.AutoCenteringParams
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListAnchorType
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.rememberScalingLazyListState
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.items
import com.openvela.bluetoothtest.R

@Composable
fun MainWearRoute(
    destinations: List<MainDestination>,
    onOpenDestination: (MainDestination) -> Unit
) {
    MaterialTheme {
        val hasDestinations = destinations.isNotEmpty()
        val centeredIndex = if (hasDestinations) 1 else 0
        val listState = rememberScalingLazyListState(initialCenterItemIndex = centeredIndex)
        LaunchedEffect(centeredIndex) {
            listState.scrollToItem(centeredIndex)
        }
        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
        ) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = listState,
                anchorType = ScalingLazyListAnchorType.ItemCenter,
                autoCentering = AutoCenteringParams(itemIndex = centeredIndex)
            ) {
                item(key = "wear_title") {
                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.title2,
                        color = MaterialTheme.colors.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                    )
                }
                items(destinations.size, key = { destinations[it].id }) { index ->
                    val destination = destinations[index]
                    Chip(
                        onClick = { onOpenDestination(destination) },
                        label = {
                            Text(
                                text = stringResource(id = destination.titleRes),
                                style = MaterialTheme.typography.body1
                            )
                        },
                        secondaryLabel = {
                            Text(
                                text = stringResource(id = destination.descriptionRes),
                                style = MaterialTheme.typography.caption3
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        },
                        colors = ChipDefaults.primaryChipColors()
                    )
                }
            }
        }
    }
}
