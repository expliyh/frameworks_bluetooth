package com.openvela.bluetoothtest.bredr.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openvela.bluetoothtest.R
import com.openvela.bluetoothtest.bredr.spp.SppViewModel.BondedDeviceOption
import com.openvela.bluetoothtest.bredr.spp.SppViewModel.SppSessionUiState
import java.util.UUID

private val MAC_ADDRESS_REGEX = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

@Composable
fun SppDetailScreen(
    session: SppSessionUiState,
    modifier: Modifier = Modifier,
    onServiceUuidChange: (String) -> Unit,
    onRemoteAddressChange: (String) -> Unit,
    onDataToSendChange: (String) -> Unit,
    onCyclesChange: (String) -> Unit,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSend: () -> Unit,
    onClearLog: () -> Unit,
    rawLogEnabled: Boolean,
    onRawLogToggle: (Boolean) -> Unit,
    bondedDevices: List<BondedDeviceOption>,
    deviceMenuExpanded: Boolean,
    onOpenDevicePicker: () -> Unit,
    onDismissDeviceMenu: () -> Unit,
    onSelectDevice: (BondedDeviceOption) -> Unit
) {
    val isUuidValid = remember(session.serviceUuid) {
        session.serviceUuid.isBlank() || runCatching { UUID.fromString(session.serviceUuid) }.isSuccess
    }
    val isAddressValid = remember(session.remoteAddress) {
        session.remoteAddress.isBlank() || MAC_ADDRESS_REGEX.matches(session.remoteAddress)
    }
    val isCyclesValid = remember(session.cycles) {
        session.cycles.isBlank() || session.cycles.toIntOrNull()?.let { it > 0 } == true
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            StatusSection(session = session)
        }
        item {
            ServiceConfigurationSection(
                session = session,
                isUuidValid = isUuidValid,
                isAddressValid = isAddressValid,
                onServiceUuidChange = onServiceUuidChange,
                onRemoteAddressChange = onRemoteAddressChange,
                bondedDevices = bondedDevices,
                deviceMenuExpanded = deviceMenuExpanded,
                onOpenDevicePicker = onOpenDevicePicker,
                onDismissDeviceMenu = onDismissDeviceMenu,
                onSelectDevice = onSelectDevice,
                onRegister = onRegister,
                onUnregister = onUnregister,
                onConnect = onConnect,
                onDisconnect = onDisconnect
            )
        }
        item {
            PayloadSection(
                session = session,
                isCyclesValid = isCyclesValid,
                onDataToSendChange = onDataToSendChange,
                onCyclesChange = onCyclesChange,
                onSend = onSend
            )
        }
        item {
            LogSection(
                logEntries = session.logEntries,
                rawLogEnabled = rawLogEnabled,
                onRawLogToggle = onRawLogToggle,
                onClearLog = onClearLog
            )
        }
    }
}

@Composable
private fun StatusSection(session: SppSessionUiState) {
    SectionCard(
        title = stringResource(id = R.string.spp_status_section_title),
        subtitle = stringResource(id = R.string.spp_status_section_subtitle)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusBadge(
                text = stringResource(
                    id = if (session.isRegistered) R.string.registered else R.string.not_registered
                ),
                isPositive = session.isRegistered
            )
            StatusBadge(
                text = stringResource(
                    id = if (session.isConnected) R.string.connected else R.string.disconnected
                ),
                isPositive = session.isConnected
            )
        }
    }
}

@Composable
private fun ServiceConfigurationSection(
    session: SppSessionUiState,
    isUuidValid: Boolean,
    isAddressValid: Boolean,
    onServiceUuidChange: (String) -> Unit,
    onRemoteAddressChange: (String) -> Unit,
    bondedDevices: List<BondedDeviceOption>,
    deviceMenuExpanded: Boolean,
    onOpenDevicePicker: () -> Unit,
    onDismissDeviceMenu: () -> Unit,
    onSelectDevice: (BondedDeviceOption) -> Unit,
    onRegister: () -> Unit,
    onUnregister: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    SectionCard(
        title = stringResource(id = R.string.spp_service_section_title),
        subtitle = stringResource(id = R.string.spp_service_section_subtitle)
    ) {
        OutlinedTextField(
            value = session.serviceUuid,
            onValueChange = onServiceUuidChange,
            label = { Text(text = stringResource(id = R.string.service_uuid)) },
            supportingText = {
                Text(
                    text = stringResource(
                        id = if (isUuidValid) R.string.service_uuid_hint else R.string.service_uuid_error_invalid
                    )
                )
            },
            isError = !isUuidValid,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
        )

        Box {
            OutlinedTextField(
                value = session.remoteAddress,
                onValueChange = onRemoteAddressChange,
                label = { Text(text = stringResource(id = R.string.remote_address)) },
                supportingText = {
                    Text(
                        text = stringResource(
                            id = if (isAddressValid || session.remoteAddress.isBlank()) {
                                R.string.remote_address_hint
                            } else {
                                R.string.remote_address_error_invalid
                            }
                        )
                    )
                },
                isError = !isAddressValid && session.remoteAddress.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii
                ),
                trailingIcon = {
                    IconButton(onClick = onOpenDevicePicker) {
                        Icon(
                            imageVector = Icons.Default.Bluetooth,
                            contentDescription = stringResource(id = R.string.select_bonded_device)
                        )
                    }
                }
            )

            DropdownMenu(
                expanded = deviceMenuExpanded,
                onDismissRequest = onDismissDeviceMenu
            ) {
                if (bondedDevices.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(id = R.string.no_bonded_devices)) },
                        onClick = {},
                        enabled = false
                    )
                } else {
                    bondedDevices.forEach { device ->
                        val suffix = stringResource(id = R.string.connected_device_suffix)
                        val label = if (device.isConnected) {
                            "${device.name} (${device.address}) - $suffix"
                        } else {
                            "${device.name} (${device.address})"
                        }
                        DropdownMenuItem(
                            text = { Text(text = label) },
                            onClick = { onSelectDevice(device) },
                            leadingIcon = if (device.isConnected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null
                                    )
                                }
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val registerLabel = if (session.isRegistered) {
                stringResource(id = R.string.unregister)
            } else {
                stringResource(id = R.string.register)
            }
            val registerAction = if (session.isRegistered) onUnregister else onRegister
            val registerEnabled = session.isRegistered || isUuidValid
            val registerColors = if (session.isRegistered) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
            val registerIcon = if (session.isRegistered) Icons.Default.Close else Icons.Default.PlayArrow

            Button(
                onClick = registerAction,
                enabled = registerEnabled,
                colors = registerColors,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = registerIcon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = registerLabel)
            }

            val connectLabel = if (session.isConnected) {
                stringResource(id = R.string.disconnect)
            } else {
                stringResource(id = R.string.connect)
            }
            val connectAction = if (session.isConnected) onDisconnect else onConnect
            val connectEnabled = session.isConnected || (isUuidValid && isAddressValid)
            val connectColors = if (session.isConnected) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
            val connectIcon = if (session.isConnected) Icons.Default.LinkOff else Icons.Default.Link

            Button(
                onClick = connectAction,
                enabled = connectEnabled,
                colors = connectColors,
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = connectIcon, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = connectLabel)
            }
        }
    }
}

@Composable
private fun PayloadSection(
    session: SppSessionUiState,
    isCyclesValid: Boolean,
    onDataToSendChange: (String) -> Unit,
    onCyclesChange: (String) -> Unit,
    onSend: () -> Unit
) {
    SectionCard(
        title = stringResource(id = R.string.spp_payload_section_title),
        subtitle = stringResource(id = R.string.spp_payload_section_subtitle)
    ) {
        OutlinedTextField(
            value = session.dataToSend,
            onValueChange = onDataToSendChange,
            label = { Text(text = stringResource(id = R.string.data_to_send)) },
            supportingText = { Text(text = stringResource(id = R.string.data_to_send_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = session.cycles,
                onValueChange = onCyclesChange,
                label = { Text(text = stringResource(id = R.string.cycles)) },
                supportingText = {
                    Text(
                        text = stringResource(
                            id = if (isCyclesValid) R.string.cycles_hint else R.string.cycles_error_invalid
                        )
                    )
                },
                isError = !isCyclesValid && session.cycles.isNotBlank(),
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Button(
                onClick = onSend,
                modifier = Modifier.weight(1f),
                enabled = !session.isSending && session.dataToSend.isNotEmpty() && isCyclesValid
            ) {
                Text(
                    text = stringResource(
                        id = if (session.isSending) R.string.sending else R.string.send
                    )
                )
                if (session.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun LogSection(
    logEntries: List<String>,
    rawLogEnabled: Boolean,
    onRawLogToggle: (Boolean) -> Unit,
    onClearLog: () -> Unit
) {
    SectionCard(
        title = stringResource(id = R.string.log),
        subtitle = stringResource(id = R.string.spp_log_section_subtitle)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = stringResource(id = R.string.log_raw_data))
                Text(
                    text = stringResource(id = R.string.log_raw_data_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = rawLogEnabled,
                onCheckedChange = onRawLogToggle
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClearLog) {
                Text(text = stringResource(id = R.string.clear_log))
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 320.dp),
            tonalElevation = 4.dp
        ) {
            SelectionContainer {
                if (logEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(id = R.string.log_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(logEntries, key = { index, _ -> index }) { _, entry ->
                            Text(
                                text = entry.trimEnd('\n'),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun StatusBadge(text: String, isPositive: Boolean) {
    val (containerColor, contentColor, icon) = if (isPositive) {
        Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            Icons.Default.CheckCircle
        )
    } else {
        Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            Icons.Default.ErrorOutline
        )
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null)
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
