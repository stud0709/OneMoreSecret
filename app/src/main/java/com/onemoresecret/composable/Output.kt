package com.onemoresecret.composable

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.onemoresecret.LocalOmsState
import com.onemoresecret.R
import com.onemoresecret.bt.BluetoothController
import com.onemoresecret.bt.KeyboardUsage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputScreen(
    outputViewModel: OutputViewModel
) {
    val state = outputViewModel.state
    val context = LocalContext.current
    val omsState = LocalOmsState.current
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showKeyboardTestTool by remember { mutableStateOf(false) }
    val copiedToClipboard = stringResource(R.string.copied_to_clipboard)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        outputViewModel.refreshBluetoothControls()
    }

    DisposableEffect(outputViewModel) {
        val applicationContext = context.applicationContext
        outputViewModel.context = applicationContext
        val controller = BluetoothController(applicationContext, launcher, outputViewModel.hidDeviceCallback)
        outputViewModel.bluetoothController = controller

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                outputViewModel.refreshBluetoothControls()
            }
        }
        applicationContext.registerReceiver(receiver, filter)
        outputViewModel.refreshBluetoothControls()

        onDispose {
            applicationContext.unregisterReceiver(receiver)
            controller.destroy()
            outputViewModel.bluetoothController = null
            outputViewModel.context = null
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, outputViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                outputViewModel.bluetoothController?.registerApp()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Center,
            verticalAlignment = CenterVertically
        ) {
            OutlinedButton(onClick = { showBluetoothDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = stringResource(R.string.bluetooth_target)
                )
            }

            Spacer(modifier = Modifier.padding(horizontal = 8.dp))

            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            Surface(
                shape = androidx.compose.material3.ButtonDefaults.shape,
                color = if (state.typeButtonEnabled && state.typingText.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (state.typeButtonEnabled && state.typingText.isNotEmpty()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                modifier = Modifier.clip(androidx.compose.material3.ButtonDefaults.shape)
            ) {
                Row(
                    verticalAlignment = CenterVertically,
                    horizontalArrangement = Center,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                if (!(state.typeButtonEnabled && state.typingText.isNotEmpty())) return@combinedClickable
                                val messageToType = outputViewModel.state.message ?: return@combinedClickable
                                val selectedLayout = outputViewModel.getSelectedKeyboardLayout() ?: return@combinedClickable
                                val strokes = selectedLayout.forString(messageToType)
                                if (strokes.contains(null)) {
                                    val unmappedIndices = strokes.indices.filter { strokes[it] == null }
                                    val unmappedChars = unmappedIndices.map { messageToType[it] }.distinct().joinToString()
                                    Toast.makeText(
                                        context,
                                        "Cannot type: unsupported characters: $unmappedChars",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@combinedClickable
                                }
                                outputViewModel.type(strokes)
                            },
                            onLongClick = {
                                showKeyboardTestTool = true
                            }
                        )
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                    Text(if (state.isTyping) stringResource(R.string.cancel) else stringResource(R.string.type))
                }
            }

            Spacer(modifier = Modifier.padding(horizontal = 8.dp))

            KeyboardLayoutSelector(
                layouts = state.keyboardLayouts,
                selectedClassName = state.selectedKeyboardLayoutClassName,
                enabled = state.keyboardLayoutEnabled,
                onSelected = outputViewModel::onKeyboardLayoutSelected
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = CenterVertically
        ) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val helpUrl = stringResource(R.string.autotype_md_url)
            IconButton(onClick = { uriHandler.openUri(helpUrl) }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = stringResource(R.string.help)
                )
            }
            Text(
                text = state.typingText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            val context = LocalContext.current
            val message = state.message
            val shareTitle = state.typingText
            if (message != null) {
                Row {
                    IconButton(onClick = {
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = ClipData.newPlainText("oneMoreSecret", message)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val persistableBundle = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                            clipData.description.extras = persistableBundle
                        }
                        clipboardManager.setPrimaryClip(clipData)
                        
                        // Verify in logcat that the flag is correctly set on the system clipboard
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val activeClip = clipboardManager.primaryClip
                            val hasSensitiveFlag = activeClip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) ?: false
                            android.util.Log.d("OmsClipboard", "Clipboard updated. Extras: ${activeClip?.description?.extras}, Sensitive Flag: $hasSensitiveFlag")
                        }
                        
                        Toast.makeText(context, copiedToClipboard, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(android.R.string.copy)
                        )
                    }
                    IconButton(onClick = {
                        omsState.isAutoLockDisarmed = true
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, message)
                            putExtra(Intent.EXTRA_TITLE, shareTitle)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }) {
                        Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share))
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(96.dp))
            }
        }
    }

    if (showBluetoothDialog) {
        Dialog(onDismissRequest = { showBluetoothDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DropdownField(
                        items = state.bluetoothTargets.map { it.address to it.label },
                        selectedKey = state.selectedBluetoothAddress,
                        enabled = state.bluetoothTargetEnabled,
                        contentDescription = stringResource(R.string.bluetooth_targets),
                        onSelected = {
                            outputViewModel.onBluetoothTargetSelected(it)
                        },
                        label = stringResource(R.string.bluetooth_target)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                val discoverableDuration = 60 // defaults to 60 as per old fragment
                                omsState.isAutoLockDisarmed = true
                                outputViewModel.bluetoothController?.requestDiscoverable(discoverableDuration)
                            },
                            enabled = state.discoverableEnabled
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                                contentDescription = stringResource(R.string.make_this_device_discoverable)
                            )
                        }

                        Row(verticalAlignment = CenterVertically) {
                            Switch(
                                checked = state.delayedStrokes,
                                onCheckedChange = outputViewModel::onDelayedStrokesChanged,
                                enabled = state.delayedStrokesEnabled
                            )
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text(text = stringResource(R.string.delay))
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Center
                    ) {
                        StatusChip(
                            iconVector = state.bluetoothStatusIcon,
                            text = state.bluetoothStatusText
                        )
                    }
                }
            }
        }
    }

    if (showKeyboardTestTool) {
        Dialog(onDismissRequest = { showKeyboardTestTool = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Keyboard Test Tool", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.foundation.lazy.LazyColumn {
                        items(KeyboardUsage.entries.size) { index ->
                            val usage = KeyboardUsage.entries[index]
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    Toast.makeText(context, usage.name, Toast.LENGTH_SHORT).show()
                                    outputViewModel.sendTestKeystroke(usage)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(usage.name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyboardLayoutSelector(
    layouts: List<OutputViewModel.KeyboardLayoutItem>,
    selectedClassName: String?,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLayout = layouts.firstOrNull { it.className == selectedClassName }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedButton(
            onClick = {}, // handled by ExposedDropdownMenuBox
            enabled = enabled,
            modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
        ) {
            Text(selectedLayout?.shortName ?: "")
            Spacer(modifier = Modifier.padding(horizontal = 2.dp))
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            layouts.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(layout.label) },
                    onClick = {
                        expanded = false
                        onSelected(layout.className)
                    }
                )
            }
        }
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
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = enabled)
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
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        leadingIcon = {
            Icon(
                imageVector = iconVector,
                contentDescription = null
            )
        }
    )
}
