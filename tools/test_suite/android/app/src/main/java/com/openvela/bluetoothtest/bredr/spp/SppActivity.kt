package com.openvela.bluetoothtest.bredr.spp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.openvela.bluetoothtest.R
import com.openvela.bluetoothtest.bredr.spp.SppViewModel.BondedDeviceOption
import com.openvela.bluetoothtest.bredr.spp.SppViewModel.SppSessionUiState
import com.openvela.bluetoothtest.bredr.spp.ui.theme.BluetoothTestSuiteTheme

class SppActivity : ComponentActivity() {
    private val viewModel: SppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothTestSuiteTheme {
                SppRoute(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SppRoute(viewModel: SppViewModel) {
    val sessions = viewModel.sessions
    val selectedId = viewModel.selectedSessionId
    val currentSession = sessions.firstOrNull { it.id == selectedId }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    AddSppDialog(
        show = showAddDialog,
        onDismiss = { showAddDialog = false },
        onConfirm = { title, uuid ->
            viewModel.addSession(title = title, serviceUuid = uuid)
            showAddDialog = false
        }
    )

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = stringResource(id = R.string.reset_spp_title)) },
            text = { Text(text = stringResource(id = R.string.reset_spp_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSessions()
                    showResetDialog = false
                }) {
                    Text(text = stringResource(id = R.string.reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = stringResource(id = R.string.cancel))
                }
            }
        )
    }

    BackHandler(enabled = selectedId != null) {
        viewModel.selectSession(null)
    }

    Scaffold(
        topBar = {
            if (selectedId == null) {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.spp)) },
                    actions = {
                        IconButton(
                            onClick = { showResetDialog = true },
                            enabled = sessions.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = stringResource(id = R.string.reset_spp_content_description)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(text = currentSession?.title ?: stringResource(id = R.string.spp)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.selectSession(null) }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = stringResource(id = R.string.reset_spp_content_description)
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedId?.let { id ->
                                    viewModel.removeSession(id)
                                    viewModel.selectSession(null)
                                }
                            },
                            enabled = currentSession != null
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(id = R.string.delete)
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selectedId == null) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(id = R.string.add)
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedId,
            transitionSpec = {
                if (targetState != null && initialState == null) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }.using(SizeTransform(clip = false))
            },
            label = "spp_list_detail_transition"
        ) { targetSessionId ->
            if (targetSessionId == null) {
                SppListScreen(
                    sessions = sessions,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    onOpenSession = { viewModel.selectSession(it) },
                    onRemoveSession = { viewModel.removeSession(it) }
                )
            } else {
                val session = sessions.firstOrNull { it.id == targetSessionId }
                if (session == null) {
                    LaunchedEffect(Unit) {
                        viewModel.selectSession(null)
                    }
                } else {
                    var bondedDevices by remember(session.id) { mutableStateOf(emptyList<BondedDeviceOption>()) }
                    var deviceMenuExpanded by remember(session.id) { mutableStateOf(false) }

                    SppDetailScreen(
                        session = session,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        onServiceUuidChange = { viewModel.updateServiceUuid(session.id, it) },
                        onRemoteAddressChange = { viewModel.updateRemoteAddress(session.id, it) },
                        onDataToSendChange = { viewModel.updateDataToSend(session.id, it) },
                        onCyclesChange = { viewModel.updateCycles(session.id, it) },
                        onRegister = { viewModel.registerServer(session.id) },
                        onUnregister = { viewModel.unregisterServer(session.id) },
                        onConnect = { viewModel.connect(session.id) },
                        onDisconnect = { viewModel.disconnect(session.id) },
                        onSend = { viewModel.send(session.id) },
                        onClearLog = { viewModel.clearLog(session.id) },
                        rawLogEnabled = session.logRawDataEnabled,
                        onRawLogToggle = { enabled -> viewModel.setRawLogEnabled(session.id, enabled) },
                        bondedDevices = bondedDevices,
                        deviceMenuExpanded = deviceMenuExpanded,
                        onOpenDevicePicker = {
                            bondedDevices = viewModel.loadBondedDevices()
                            deviceMenuExpanded = true
                        },
                        onDismissDeviceMenu = { deviceMenuExpanded = false },
                        onSelectDevice = { option ->
                            viewModel.updateRemoteAddress(session.id, option.address)
                            deviceMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SppListScreen(
    sessions: List<SppSessionUiState>,
    modifier: Modifier = Modifier,
    onOpenSession: (Int) -> Unit,
    onRemoveSession: (Int) -> Unit
) {
    if (sessions.isEmpty()) {
        EmptyState(
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sessions, key = { it.id }) { session ->
                SppCard(
                    session = session,
                    onClick = { onOpenSession(session.id) },
                    onRemove = { onRemoveSession(session.id) }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(id = R.string.spp_empty_hint),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(id = R.string.spp_empty_hint_secondary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
