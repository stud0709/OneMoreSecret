package com.onemoresecret.compose

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.IntentFilter
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onemoresecret.PermissionsFragment
import com.onemoresecret.R
import com.onemoresecret.compose.OutputViewModel.Companion.REQUIRED_PERMISSIONS
import com.onemoresecret.compose.OutputViewModel.Companion.TAG

@Composable
fun OutputScreen() {
    val viewModel =
        OutputViewModel(
            LocalContext.current.applicationContext as Application,
            ResourceProvider(LocalContext.current),
            //prepare intent "request discoverable"
            rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { }
        )

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(
            text = stringResource(id = R.string.bluetooth_target),
            modifier = Modifier.padding(top = 8.dp)
        )

        ExposedDropdownMenuBox(
            options = viewModel.bluetoothTargets,
            selectedOption = viewModel.selectedBluetoothTarget,
            onSelectionChanged = viewModel::onKeyboardLayoutSelected
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = {viewModel.bluetoothController.requestDiscoverable(viewModel.discoverableDuration)},
                enabled = false) {
                Icon(
                    painterResource(id = R.drawable.ic_baseline_bluetooth_discovering_24),
                    contentDescription = stringResource(R.string.make_this_device_discoverable)
                )
            }

            AssistChip(
                onClick = {},
                label = { Text(text = stringResource(R.string.unsupported)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_bluetooth_24),
                        contentDescription = null
                    )
                },
                enabled = false
            )

            var isDelayedStrokes by remember { mutableStateOf(false) }
            Switch(
                checked = isDelayedStrokes,
                onCheckedChange = { isDelayedStrokes = it },
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Text(
            text = stringResource(id = R.string.keyboard_layout),
            modifier = Modifier.padding(top = 8.dp)
        )

        ExposedDropdownMenuBox(
            options = viewModel.keyboardLayouts,
            selectedOption = viewModel.selectedKeyboardLayout,
            onSelectionChanged =  viewModel::onBluetoothTargetSelected
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(onClick = {}, enabled = false) {
                Icon(
                    painterResource(id = R.drawable.ic_baseline_keyboard_24),
                    contentDescription = null
                )
                Text(text = stringResource(R.string.type))
            }
        }

        Text(
            text = stringResource(id = R.string.typing_please_wait),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    LaunchedEffect(Unit) {
        if (PermissionsFragment.isAllPermissionsGranted(
                TAG,
                context,
                *REQUIRED_PERMISSIONS
            )
        ) {
            viewModel.allPermissionsGranted.value = true
        } else {
            viewModel.refreshBluetoothControls()
        }
    }

    LaunchedEffect(viewModel.allPermissionsGranted) {
        if(!viewModel.allPermissionsGranted.value) return@LaunchedEffect

        context.registerReceiver(
            viewModel.bluetoothBroadcastReceiver,
            IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        )

        context.registerReceiver(
            viewModel.bluetoothBroadcastReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )

        context.registerReceiver(
            viewModel.bluetoothBroadcastReceiver,
            IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        )

        viewModel.refreshBluetoothControls()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBox(
    options: SnapshotStateList<String>,
    selectedOption: MutableState<String>,
    onSelectionChanged: (String) -> Unit
) {

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        TextField(
            value = selectedOption.value,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        selectedOption.value = option
                        onSelectionChanged(option)
                        expanded = false
                    }
                )
            }
        }
    }
}