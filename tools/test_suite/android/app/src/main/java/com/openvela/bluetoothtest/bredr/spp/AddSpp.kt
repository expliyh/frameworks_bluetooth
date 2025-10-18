package com.openvela.bluetoothtest.bredr.spp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.openvela.bluetoothtest.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSppDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?, String?) -> Unit
) {
    if (!show) return

    var title by rememberSaveable { mutableStateOf("") }
    var uuid by rememberSaveable { mutableStateOf(SppViewModel.DEFAULT_UUID) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.add_spp_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(text = stringResource(id = R.string.add_spp_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = uuid,
                    onValueChange = { uuid = it },
                    label = { Text(text = stringResource(id = R.string.add_spp_uuid)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim().ifEmpty { null }, uuid.trim().ifEmpty { null }) }) {
                Text(text = stringResource(id = R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}