package com.openvela.bluetoothtest.bredr

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openvela.bluetoothtest.R
import com.openvela.bluetoothtest.bredr.BredrL2capViewModel.ChannelUiState
import com.openvela.bluetoothtest.bredr.spp.ui.theme.BluetoothTestSuiteTheme

class BredrL2capActivity : ComponentActivity() {
    private val viewModel: BredrL2capViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothTestSuiteTheme {
                BredrL2capRoute(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BredrL2capRoute(viewModel: BredrL2capViewModel) {
    val channels = viewModel.channels
    val selectedId = viewModel.selectedChannelId
    val currentChannel = channels.firstOrNull { it.id == selectedId }

    var showResetDialog by rememberSaveable { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(text = stringResource(id = R.string.l2cap_reset_channels_title)) },
            text = { Text(text = stringResource(id = R.string.l2cap_reset_channels_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetChannels()
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
        viewModel.selectChannel(null)
    }

    Scaffold(
        topBar = {
            if (selectedId == null) {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.bredr_l2cap)) },
                    actions = {
                        IconButton(
                            onClick = { showResetDialog = true },
                            enabled = channels.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Restore,
                                contentDescription = stringResource(id = R.string.l2cap_reset_channels_content_description)
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text(text = currentChannel?.title ?: stringResource(id = R.string.bredr_l2cap)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.selectChannel(null) }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(id = R.string.back)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedId?.let { id ->
                                    viewModel.removeChannel(id)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(id = R.string.l2cap_delete_channel_content_description)
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (selectedId == null) {
                FloatingActionButton(onClick = { viewModel.addChannel() }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(id = R.string.l2cap_add_channel_content_description)
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
            label = "l2cap_list_detail_transition"
        ) { target ->
            if (target == null) {
                L2capListScreen(
                    channels = channels,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    onOpenChannel = { viewModel.selectChannel(it) },
                    onRemoveChannel = { viewModel.removeChannel(it) }
                )
            } else {
                val channel = channels.firstOrNull { it.id == target }
                if (channel == null) {
                    LaunchedEffect(Unit) {
                        viewModel.selectChannel(null)
                    }
                } else {
                    L2capDetailScreen(
                        channel = channel,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        onServiceChange = viewModel::updateServicePsm,
                        onRemoteAddressChange = viewModel::updateRemoteAddress,
                        onDataChange = viewModel::updateDataToSend,
                        onRegister = viewModel::registerServer,
                        onUnregister = viewModel::unregisterServer,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                        onSend = viewModel::send,
                        onClearLog = viewModel::clearLog
                    )
                }
            }
        }
    }
}

@Composable
private fun L2capListScreen(
    channels: List<ChannelUiState>,
    modifier: Modifier = Modifier,
    onOpenChannel: (Int) -> Unit,
    onRemoveChannel: (Int) -> Unit
) {
    if (channels.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.l2cap_empty_hint),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(id = R.string.l2cap_empty_hint_secondary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { _, channel ->
                L2capChannelCard(
                    channel = channel,
                    onClick = { onOpenChannel(channel.id) },
                    onRemove = { onRemoveChannel(channel.id) }
                )
            }
        }
    }
}

@Composable
private fun L2capChannelCard(
    channel: ChannelUiState,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(id = R.string.delete)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(id = R.string.l2cap_service_value, channel.servicePsm.ifBlank { "-" }),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (channel.remoteAddress.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.remote_address_label, channel.remoteAddress),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusLabel(
                    text = if (channel.isRegistered) stringResource(id = R.string.registered)
                    else stringResource(id = R.string.not_registered)
                )
                StatusLabel(
                    text = if (channel.isConnected) stringResource(id = R.string.connected)
                    else stringResource(id = R.string.disconnected)
                )
            }
            val firstLog = channel.logEntries.firstOrNull()?.trim()
            if (!firstLog.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = firstLog,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusLabel(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun L2capDetailScreen(
    channel: ChannelUiState,
    modifier: Modifier = Modifier,
    onServiceChange: (Int, String) -> Unit,
    onRemoteAddressChange: (Int, String) -> Unit,
    onDataChange: (Int, String) -> Unit,
    onRegister: (Int) -> Unit,
    onUnregister: (Int) -> Unit,
    onConnect: (Int) -> Unit,
    onDisconnect: (Int) -> Unit,
    onSend: (Int) -> Unit,
    onClearLog: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionCard(
            title = stringResource(id = R.string.l2cap_status_section_title),
            subtitle = stringResource(id = R.string.l2cap_status_section_subtitle)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                L2capStatusBadge(
                    text = if (channel.isRegistered) {
                        stringResource(id = R.string.registered)
                    } else {
                        stringResource(id = R.string.not_registered)
                    },
                    icon = if (channel.isRegistered) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    active = channel.isRegistered
                )
                L2capStatusBadge(
                    text = if (channel.isConnected) {
                        stringResource(id = R.string.connected)
                    } else {
                        stringResource(id = R.string.disconnected)
                    },
                    icon = if (channel.isConnected) Icons.Default.Link else Icons.Default.LinkOff,
                    active = channel.isConnected
                )
            }
        }

        SectionCard(
            title = stringResource(id = R.string.l2cap_service_section_title),
            subtitle = stringResource(id = R.string.l2cap_service_section_subtitle)
        ) {
            val isServiceValid = channel.servicePsm.isNotBlank()
            OutlinedTextField(
                value = channel.servicePsm,
                onValueChange = { onServiceChange(channel.id, it) },
                label = { Text(text = stringResource(id = R.string.l2cap_service_label)) },
                placeholder = { Text(text = stringResource(id = R.string.l2cap_service_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth()
            )
            val registerLabel = if (channel.isRegistered) {
                stringResource(id = R.string.unregister)
            } else {
                stringResource(id = R.string.register)
            }
            val registerAction = if (channel.isRegistered) {
                { onUnregister(channel.id) }
            } else {
                { onRegister(channel.id) }
            }
            val registerEnabled = channel.isRegistered || isServiceValid
            val registerColors = if (channel.isRegistered) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ButtonDefaults.buttonColors()
            }
            val registerIcon = if (channel.isRegistered) Icons.Default.Close else Icons.Default.PlayArrow

            OutlinedTextField(
                value = channel.remoteAddress,
                onValueChange = { onRemoteAddressChange(channel.id, it) },
                label = { Text(text = stringResource(id = R.string.remote_address)) },
                placeholder = { Text(text = stringResource(id = R.string.remote_address_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii
                ),
                modifier = Modifier.fillMaxWidth()
            )
            val isAddressValid = channel.remoteAddress.isNotBlank()
            val connectLabel = if (channel.isConnected) {
                stringResource(id = R.string.disconnect)
            } else {
                stringResource(id = R.string.connect)
            }
            val connectAction = if (channel.isConnected) {
                { onDisconnect(channel.id) }
            } else {
                { onConnect(channel.id) }
            }
            val connectEnabled = channel.isConnected || (isServiceValid && isAddressValid)
            val connectColors = if (channel.isConnected) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                ButtonDefaults.buttonColors()
            }
            val connectIcon = if (channel.isConnected) Icons.Default.LinkOff else Icons.Default.Link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = registerAction,
                    enabled = registerEnabled,
                    colors = registerColors,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = registerIcon, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = registerLabel)
                }
                Button(
                    onClick = connectAction,
                    enabled = connectEnabled,
                    colors = connectColors,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = connectIcon, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = connectLabel)
                }
            }
        }

        SectionCard(
            title = stringResource(id = R.string.l2cap_payload_section_title),
            subtitle = stringResource(id = R.string.l2cap_payload_section_subtitle)
        ) {
            OutlinedTextField(
                value = channel.dataToSend,
                onValueChange = { onDataChange(channel.id, it) },
                label = { Text(text = stringResource(id = R.string.data_to_send)) },
                placeholder = { Text(text = stringResource(id = R.string.data_to_send_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
            )
            Button(
                onClick = { onSend(channel.id) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !channel.isSending
            ) {
                if (channel.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                }
                Text(
                    text = if (channel.isSending) {
                        stringResource(id = R.string.sending)
                    } else {
                        stringResource(id = R.string.send)
                    }
                )
            }
        }

        SectionCard(
            title = stringResource(id = R.string.log),
            subtitle = stringResource(id = R.string.l2cap_log_section_subtitle)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { onClearLog(channel.id) },
                    enabled = channel.logEntries.isNotEmpty()
                ) {
                    Text(text = stringResource(id = R.string.clear_log))
                }
            }
            Surface(
                tonalElevation = 2.dp,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 320.dp)
            ) {
                SelectionContainer {
                    if (channel.logEntries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
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
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            state = listState,
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(channel.logEntries, key = { index, _ -> index }) { _, entry ->
                                Text(
                                    text = entry,
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
private fun L2capStatusBadge(
    text: String,
    icon: ImageVector,
    active: Boolean
) {
    Surface(
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
