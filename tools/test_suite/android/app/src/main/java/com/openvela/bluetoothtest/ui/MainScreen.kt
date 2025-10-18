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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) }
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
            MainMenuSection.entries.forEach { section ->
                val sectionItems = destinations.filter { it.section == section }
                if (sectionItems.isEmpty()) return@forEach

                item(key = "${section.name}_header") {
                    SectionHeader(section = section)
                }
                items(
                    items = sectionItems,
                    key = { destination -> destination.id }
                ) { destination ->
                    MainMenuCard(
                        destination = destination,
                        onClick = { onOpenDestination(destination) }
                    )
                }
                item(key = "${section.name}_footer_spacer") {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: MainMenuSection) {
    Text(
        text = stringResource(id = section.titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun MainMenuCard(
    destination: MainDestination,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        RowContent(destination = destination)
    }
}

@Composable
private fun RowContent(destination: MainDestination) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(id = destination.titleRes),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(id = destination.descriptionRes),
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
