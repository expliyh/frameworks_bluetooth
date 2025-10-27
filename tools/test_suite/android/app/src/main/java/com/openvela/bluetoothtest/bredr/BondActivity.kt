package com.openvela.bluetoothtest.bredr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openvela.bluetoothtest.R
import com.openvela.bluetoothtest.bredr.BondViewModel.BondedDeviceEntry
import com.openvela.bluetoothtest.bredr.BondViewModel.CycleInfo
import com.openvela.bluetoothtest.bredr.BondViewModel.CycleMode
import com.openvela.bluetoothtest.bredr.spp.ui.theme.BluetoothTestSuiteTheme

class BondActivity : ComponentActivity() {
    private val viewModel: BondViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothTestSuiteTheme {
                val uiState by viewModel.uiState.collectAsState()
                BondRoute(
                    uiState = uiState,
                    onBack = { finish() },
                    onAddressChange = viewModel::onAddressInputChange,
                    onCyclesChange = viewModel::onCyclesInputChange,
                    onCreateBond = viewModel::onCreateBondRequested,
                    onRemoveBond = viewModel::onRemoveBondRequested,
                    onRefreshDevices = viewModel::refreshBondedDevices,
                    onUseDevice = viewModel::onUseDevice,
                    onClearLog = viewModel::onClearLog
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshBondedDevices()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BondRoute(
    uiState: BondViewModel.UiState,
    onBack: () -> Unit,
    onAddressChange: (String) -> Unit,
    onCyclesChange: (String) -> Unit,
    onCreateBond: () -> Unit,
    onRemoveBond: () -> Unit,
    onRefreshDevices: () -> Unit,
    onUseDevice: (String) -> Unit,
    onClearLog: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.bond)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!uiState.bluetoothSupported) {
                BondSupportWarning()
            }

            BondControlsSection(
                address = uiState.addressInput,
                addressError = uiState.addressError,
                cycles = uiState.cyclesInput,
                cyclesError = uiState.cyclesError,
                onAddressChange = onAddressChange,
                onCyclesChange = onCyclesChange,
                onCreateBond = onCreateBond,
                onRemoveBond = onRemoveBond
            )

            uiState.activeCycle?.let { cycle ->
                BondCycleStatus(cycle)
            }

            BondedDevicesSection(
                devices = uiState.pairedDevices,
                onRefresh = onRefreshDevices,
                onUseDevice = onUseDevice
            )

            BondLogSection(
                log = uiState.log,
                onClearLog = onClearLog
            )
        }
    }
}

@Composable
private fun BondSupportWarning() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = 6.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null
            )
            Text(
                text = stringResource(id = R.string.bond_bluetooth_not_supported),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BondControlsSection(
    address: String,
    addressError: String?,
    cycles: String,
    cyclesError: String?,
    onAddressChange: (String) -> Unit,
    onCyclesChange: (String) -> Unit,
    onCreateBond: () -> Unit,
    onRemoveBond: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(id = R.string.bond_controls_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(id = R.string.bond_controls_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                label = { Text(text = stringResource(id = R.string.remote_address)) },
                placeholder = { Text(text = stringResource(id = R.string.remote_address_hint)) },
                singleLine = true,
                isError = addressError != null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (addressError != null) {
                Text(
                    text = addressError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = cycles,
                onValueChange = onCyclesChange,
                label = { Text(text = stringResource(id = R.string.cycles)) },
                placeholder = { Text(text = stringResource(id = R.string.cycles_hint)) },
                singleLine = true,
                isError = cyclesError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (cyclesError != null) {
                Text(
                    text = cyclesError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onCreateBond,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.bond_create_action))
                }
                FilledTonalButton(
                    onClick = onRemoveBond,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.bond_remove_action))
                }
            }
        }
    }
}

@Composable
private fun BondCycleStatus(info: CycleInfo) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = when (info.mode) {
                    CycleMode.Create -> stringResource(
                        id = R.string.bond_cycle_status_create,
                        info.remaining
                    )
                    CycleMode.Remove -> stringResource(
                        id = R.string.bond_cycle_status_remove,
                        info.remaining
                    )
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun BondedDevicesSection(
    devices: List<BondedDeviceEntry>,
    onRefresh: () -> Unit,
    onUseDevice: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(id = R.string.bond_devices_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.bond_devices_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(id = R.string.bond_refresh)
                    )
                }
            }

            if (devices.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.no_bonded_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    devices.forEach { device ->
                        BondedDeviceRow(
                            device = device,
                            onUseDevice = onUseDevice
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BondedDeviceRow(
    device: BondedDeviceEntry,
    onUseDevice: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(
                        id = R.string.remote_address_label,
                        device.address
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (device.isConnected) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(id = R.string.connected_device_suffix),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(id = R.string.connected_device_suffix),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            TextButton(onClick = { onUseDevice(device.address) }) {
                Text(text = stringResource(id = R.string.bond_use_address))
            }
        }
    }
}

@Composable
private fun BondLogSection(
    log: String,
    onClearLog: () -> Unit
) {
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(text = stringResource(id = R.string.clear_log)) },
            text = { Text(text = stringResource(id = R.string.bond_clear_log_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearLog()
                    }
                ) {
                    Text(text = stringResource(id = R.string.clear_log))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(id = R.string.log),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(id = R.string.bond_log_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = { showClearDialog = true },
                    enabled = log.isNotEmpty()
                ) {
                    Text(text = stringResource(id = R.string.clear_log))
                }
            }

            val scrollState = rememberScrollState()
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = if (log.isEmpty()) {
                                stringResource(id = R.string.log_empty_hint)
                            } else {
                                log
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
