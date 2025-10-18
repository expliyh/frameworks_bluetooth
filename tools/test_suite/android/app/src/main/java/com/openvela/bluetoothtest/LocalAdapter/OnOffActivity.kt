package com.openvela.bluetoothtest.LocalAdapter

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openvela.bluetoothtest.R
import com.openvela.bluetoothtest.bredr.spp.ui.theme.BluetoothTestSuiteTheme
import kotlinx.coroutines.flow.collectLatest

class OnOffActivity : ComponentActivity() {
    private val viewModel: AdapterOnOffViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothTestSuiteTheme {
                val uiState by viewModel.uiState.collectAsState()
                val enableIntent = rememberEnableIntent()
                val enableLauncher = rememberEnableLauncher {
                    viewModel.onEnableIntentResult(it == Activity.RESULT_OK)
                }

                LaunchedEffect(Unit) {
                    viewModel.events.collectLatest { event ->
                        when (event) {
                            is AdapterOnOffViewModel.Event.RequestEnable -> {
                                if (viewModel.isCurrentGeneration(event.generation)) {
                                    enableLauncher.launch(enableIntent)
                                }
                            }

                            is AdapterOnOffViewModel.Event.ShowMessage -> {
                                Toast
                                    .makeText(this@OnOffActivity, event.message, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        }
                    }
                }

                AdapterOnOffRoute(
                    uiState = uiState,
                    onBack = { finish() },
                    onCyclesChange = viewModel::onCyclesInputChange,
                    onEnable = viewModel::onEnableRequested,
                    onDisable = viewModel::onDisableRequested,
                    onClearLog = viewModel::clearLog,
                    onCancelCycle = viewModel::cancelCycle
                )
            }
        }
    }
}

@Composable
private fun rememberEnableIntent(): Intent {
    return remember { Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE) }
}

@Composable
private fun rememberEnableLauncher(onResult: (Int) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result -> onResult(result.resultCode) }
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdapterOnOffRoute(
    uiState: AdapterOnOffViewModel.UiState,
    onBack: () -> Unit,
    onCyclesChange: (String) -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onClearLog: () -> Unit,
    onCancelCycle: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.bredr_on_off)) },
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
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            AdapterStateCard(
                uiState = uiState,
                onCancelCycle = onCancelCycle
            )

            OutlinedTextField(
                value = uiState.cyclesInput,
                onValueChange = onCyclesChange,
                label = { Text(text = stringResource(id = R.string.cycles)) },
                supportingText = {
                    val error = uiState.cyclesInputError
                    if (error != null) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(text = stringResource(id = R.string.cycle_test))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.cyclesInputError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            ActionButtonsRow(
                uiState = uiState,
                onEnable = onEnable,
                onDisable = onDisable
            )

            AdapterLogSection(log = uiState.log, onClearLog = onClearLog)
        }
    }
}

@Composable
private fun ActionButtonsRow(
    uiState: AdapterOnOffViewModel.UiState,
    onEnable: () -> Unit,
    onDisable: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onEnable,
            modifier = Modifier.weight(1f),
            enabled = uiState.bluetoothSupported && !uiState.isCycling
        ) {
            Text(text = stringResource(id = R.string.enable))
        }
        FilledTonalButton(
            onClick = onDisable,
            modifier = Modifier.weight(1f),
            enabled = uiState.bluetoothSupported && !uiState.isCycling
        ) {
            Text(text = stringResource(id = R.string.disable))
        }
    }
}

@Composable
private fun AdapterStateCard(
    uiState: AdapterOnOffViewModel.UiState,
    onCancelCycle: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = stringResource(id = R.string.adapter_status_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val statusText = when {
                        !uiState.bluetoothSupported -> stringResource(id = R.string.adapter_not_supported)
                        uiState.isBluetoothOn -> stringResource(id = R.string.adapter_state_on)
                        else -> stringResource(id = R.string.adapter_state_off)
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.isCycling && uiState.bluetoothSupported,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(
                            id = when (uiState.cycleMode) {
                                AdapterOnOffViewModel.CycleMode.StartEnable ->
                                    R.string.cycle_mode_enable

                                AdapterOnOffViewModel.CycleMode.StartDisable ->
                                    R.string.cycle_mode_disable

                                null -> R.string.cycle_mode_enable
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            id = R.string.cycle_remaining,
                            uiState.remainingCycles
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    TextButton(
                        onClick = onCancelCycle,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(text = stringResource(id = R.string.stop))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdapterLogSection(
    log: String,
    onClearLog: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.log),
                style = MaterialTheme.typography.titleSmall
            )
            TextButton(onClick = onClearLog) {
                Text(text = stringResource(id = R.string.clear_log))
            }
        }
        val scrollState = rememberScrollState()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 320.dp),
            tonalElevation = 4.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(12.dp)
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
