package com.onemoresecret.composable

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onemoresecret.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputScreen(
    state: OutputViewModel.State,
    onBluetoothTargetSelected: (String) -> Unit,
    onKeyboardLayoutSelected: (String) -> Unit,
    onDelayedStrokesChanged: (Boolean) -> Unit,
    onDiscoverableClick: () -> Unit,
    onTypeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DropdownField(
            items = state.bluetoothTargets.map { it.address to it.label },
            selectedKey = state.selectedBluetoothAddress,
            enabled = state.bluetoothTargetEnabled,
            contentDescription = stringResource(R.string.bluetooth_targets),
            onSelected = onBluetoothTargetSelected,
            label = stringResource(R.string.bluetooth_target)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDiscoverableClick,
                enabled = state.discoverableEnabled
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_bluetooth_discovering_24),
                    contentDescription = stringResource(R.string.make_this_device_discoverable)
                )
            }

            StatusChip(
                iconRes = state.bluetoothStatusIcon,
                text = state.bluetoothStatusText
            )

            Switch(
                checked = state.delayedStrokes,
                onCheckedChange = onDelayedStrokesChanged,
                enabled = state.delayedStrokesEnabled
            )
            Text(text = stringResource(R.string.delay))
        }

        DropdownField(
            items = state.keyboardLayouts.map { it.className to it.label },
            selectedKey = state.selectedKeyboardLayoutClassName,
            enabled = state.keyboardLayoutEnabled,
            contentDescription = stringResource(R.string.keyboard_layouts),
            onSelected = onKeyboardLayoutSelected,
            label = stringResource(R.string.keyboard_layout)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onTypeClick,
                enabled = state.typeButtonEnabled
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(if (state.isTyping) stringResource(R.string.cancel) else stringResource(R.string.type))
            }
        }

        Text(
            text = state.typingText,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownField(
    items: List<Pair<String, String>>,
    selectedKey: String?,
    enabled: Boolean,
    contentDescription: String,
    onSelected: (String) -> Unit,
    label: String
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = items.firstOrNull { it.first == selectedKey }?.second ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            label = { Text(label) },
            value = selectedLabel,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .semantics { this.contentDescription = contentDescription },
            readOnly = true,
            enabled = enabled,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { (key, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        expanded = false
                        onSelected(key)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    @DrawableRes iconRes: Int,
    text: String
) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        leadingIcon = {
            if (iconRes != 0) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null
                )
            }
        }
    )
}
