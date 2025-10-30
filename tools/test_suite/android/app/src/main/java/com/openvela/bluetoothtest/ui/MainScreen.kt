package com.openvela.bluetoothtest.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.openvela.bluetoothtest.R

enum class MainMenuSection(@StringRes val titleRes: Int) {
    Adapter(R.string.main_section_adapter),
    Classic(R.string.main_section_classic),
    Ble(R.string.main_section_ble)
}

enum class MainDestination(
    val id: String,
    val section: MainMenuSection,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector
) {
    AdapterOnOff(
        id = "adapter_on_off",
        section = MainMenuSection.Adapter,
        titleRes = R.string.bredr_on_off,
        descriptionRes = R.string.main_menu_on_off_desc,
        icon = Icons.Default.Settings
    ),
    BredrInquiry(
        id = "bredr_inquiry",
        section = MainMenuSection.Classic,
        titleRes = R.string.bredr_inquiry,
        descriptionRes = R.string.main_menu_bredr_inquiry_desc,
        icon = Icons.Default.BluetoothSearching
    ),
    BondManagement(
        id = "bond_management",
        section = MainMenuSection.Classic,
        titleRes = R.string.bond,
        descriptionRes = R.string.main_menu_bond_desc,
        icon = Icons.Default.Link
    ),
    SppSessions(
        id = "spp_sessions",
        section = MainMenuSection.Classic,
        titleRes = R.string.spp,
        descriptionRes = R.string.main_menu_spp_desc,
        icon = Icons.Default.Bluetooth
    ),
    ClassicL2cap(
        id = "classic_l2cap",
        section = MainMenuSection.Classic,
        titleRes = R.string.bredr_l2cap,
        descriptionRes = R.string.main_menu_bredr_l2cap_desc,
        icon = Icons.Default.SettingsEthernet
    ),
    BlePeripheral(
        id = "ble_peripheral",
        section = MainMenuSection.Ble,
        titleRes = R.string.ble_peripheral,
        descriptionRes = R.string.main_menu_ble_peripheral_desc,
        icon = Icons.Default.WifiTethering
    ),
    BleCentral(
        id = "ble_central",
        section = MainMenuSection.Ble,
        titleRes = R.string.ble_central,
        descriptionRes = R.string.main_menu_ble_central_desc,
        icon = Icons.Default.DeveloperBoard
    ),
    BleL2cap(
        id = "ble_l2cap",
        section = MainMenuSection.Ble,
        titleRes = R.string.ble_l2cap,
        descriptionRes = R.string.main_menu_ble_l2cap_desc,
        icon = Icons.Default.SettingsInputComponent
    );
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainRoute(
    destinations: List<MainDestination>,
    onOpenDestination: (MainDestination) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val sectionUiModels = remember(destinations, context, configuration) {
        MainMenuSection.entries.mapNotNull { section ->
            val sectionItems = destinations.filter { it.section == section }
            if (sectionItems.isEmpty()) {
                null
            } else {
                val items = sectionItems.map { destination ->
                    MainMenuItemUiModel(
                        destination = destination,
                        title = context.getString(destination.titleRes),
                        description = context.getString(destination.descriptionRes)
                    )
                }
                SectionUiModel(
                    section = section,
                    title = context.getString(section.titleRes),
                    items = items
                )
            }
        }
    }
    val appTitle = remember(context, configuration) { context.getString(R.string.app_name) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = appTitle) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            sectionUiModels.forEach { section ->

                item(key = "${section.section.name}_header") {
                    SectionHeader(title = section.title)
                }
                items(
                    items = section.items,
                    key = { item -> item.destination.id }
                ) { item ->
                    MainMenuCard(
                        item = item,
                        onClick = { onOpenDestination(item.destination) }
                    )
                }
                item(key = "${section.section.name}_footer_spacer") {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun MainMenuCard(
    item: MainMenuItemUiModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        RowContent(item = item)
    }
}

@Composable
private fun RowContent(item: MainMenuItemUiModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = item.destination.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class MainMenuItemUiModel(
    val destination: MainDestination,
    val title: String,
    val description: String
)

private data class SectionUiModel(
    val section: MainMenuSection,
    val title: String,
    val items: List<MainMenuItemUiModel>
)
