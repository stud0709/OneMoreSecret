package com.onemoresecret.composable

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.onemoresecret.R
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.bt.BluetoothController
import com.onemoresecret.bt.layout.KeyboardLayout
import com.onemoresecret.bt.layout.Stroke
import java.util.Arrays
import java.util.Comparator
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import kotlin.time.Duration.Companion.milliseconds

class OutputViewModel(private val prefs: SharedPreferences) : ViewModel() {
    var state by mutableStateOf(State())
        private set

    private var keyboardLayouts: List<KeyboardLayout> = emptyList()
    var bluetoothController: BluetoothController? = null
    @SuppressLint("StaticFieldLeak")
    var context: Context? = null

    val typing = AtomicBoolean(false)
    val refreshingBtControls = AtomicBoolean(false)

    init {
        initializeKeyboardLayouts()
    }

    fun initializeKeyboardLayouts() {
        if (keyboardLayouts.isNotEmpty()) return

        keyboardLayouts = Arrays.stream(KeyboardLayout.knownSubclasses)
            .map { clazz -> clazz!!.getDeclaredConstructor().newInstance() as KeyboardLayout }
            .filter { Objects.nonNull(it) }
            .sorted(Comparator.comparing { obj -> obj.toString() })
            .collect(Collectors.toList())

        val selectedClassName = prefs.getString(PROP_LAST_SELECTED_KEYBOARD_LAYOUT, null)
        val selectedLayout = keyboardLayouts.firstOrNull { it.javaClass.name == selectedClassName }
            ?: keyboardLayouts.firstOrNull()

        state = state.copy(
            keyboardLayouts = keyboardLayouts.map {
                KeyboardLayoutItem(it.javaClass.name, it.toString(), it.shortName)
            },
            selectedKeyboardLayoutClassName = selectedLayout?.javaClass?.name
        )
    }

    fun setMessage(message: String?, shareTitle: String) {
        state = state.copy(message = message, typingText = if (typing.get()) state.typingText else shareTitle)
        refreshBluetoothControls()
    }

    fun onBluetoothTargetSelected(address: String) {
        prefs.edit { putString(PROP_LAST_SELECTED_BT_TARGET, address) }
        state = state.copy(selectedBluetoothAddress = address)
        checkConnectSelectedDevice()
        refreshBluetoothControls()
    }

    fun onKeyboardLayoutSelected(className: String) {
        prefs.edit { putString(PROP_LAST_SELECTED_KEYBOARD_LAYOUT, className) }
        state = state.copy(selectedKeyboardLayoutClassName = className)
        refreshBluetoothControls()
    }

    fun onDelayedStrokesChanged(enabled: Boolean) {
        state = state.copy(delayedStrokes = enabled)
    }

    fun getSelectedKeyboardLayout(): KeyboardLayout? {
        return keyboardLayouts.firstOrNull { it.javaClass.name == state.selectedKeyboardLayoutClassName }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getSelectedBluetoothDevice(): BluetoothTargetItem? {
        val target = state.bluetoothTargets.firstOrNull { it.address == state.selectedBluetoothAddress }
        if (target != null) return target
        
        val address = state.selectedBluetoothAddress ?: prefs.getString(PROP_LAST_SELECTED_BT_TARGET, null)
        val adapter = bluetoothController?.adapter ?: return null
        val device = adapter.bondedDevices.firstOrNull { it.address == address } ?: adapter.bondedDevices.firstOrNull()
        if (device != null) {
            return try {
                BluetoothTargetItem(device.address, device.name ?: device.address, device)
            } catch (_: SecurityException) {
                BluetoothTargetItem(device.address, device.address, device)
            }
        }
        return null
    }

    fun updateUiState(
        bluetoothAvailable: Boolean,
        bluetoothTargets: List<BluetoothTargetItem>,
        bluetoothStatusText: String,
        bluetoothStatusIcon: androidx.compose.ui.graphics.vector.ImageVector,
        discoverableEnabled: Boolean,
        keyboardLayoutEnabled: Boolean,
        bluetoothTargetEnabled: Boolean,
        typeButtonEnabled: Boolean,
        delayedStrokesEnabled: Boolean,
        typingText: String,
        isTyping: Boolean
    ) {
        val selectedBluetoothAddress = when {
            state.selectedBluetoothAddress in bluetoothTargets.map { it.address } -> state.selectedBluetoothAddress
            bluetoothTargets.isNotEmpty() -> bluetoothTargets.first().address
            else -> null
        }

        state = state.copy(
            bluetoothAvailable = bluetoothAvailable,
            bluetoothTargets = bluetoothTargets,
            selectedBluetoothAddress = selectedBluetoothAddress,
            bluetoothStatusText = bluetoothStatusText,
            bluetoothStatusIcon = bluetoothStatusIcon,
            discoverableEnabled = discoverableEnabled,
            keyboardLayoutEnabled = keyboardLayoutEnabled,
            bluetoothTargetEnabled = bluetoothTargetEnabled,
            typeButtonEnabled = typeButtonEnabled,
            delayedStrokesEnabled = delayedStrokesEnabled,
            typingText = typingText,
            isTyping = isTyping
        )
    }

    fun refreshBluetoothControls() {
        if (refreshingBtControls.get()) return
        val ctx = context ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (bluetoothController == null || !bluetoothController!!.isBluetoothAvailable ||
                    ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED
                ) {
                    ctx.mainExecutor.execute {
                        refreshingBtControls.set(true)
                        try {
                            updateUiState(
                                bluetoothAvailable = false,
                                bluetoothTargets = emptyList(),
                                bluetoothStatusText = ctx.getString(R.string.bt_not_available),
                                bluetoothStatusIcon = androidx.compose.material.icons.Icons.Default.BluetoothDisabled,
                                discoverableEnabled = false,
                                keyboardLayoutEnabled = false,
                                bluetoothTargetEnabled = false,
                                typeButtonEnabled = false,
                                delayedStrokesEnabled = false,
                                typingText = state.typingText,
                                isTyping = false
                            )
                        } catch (ex: IllegalStateException) {
                            Log.e(TAG, "Context invalid", ex)
                        } finally {
                            refreshingBtControls.set(false)
                        }
                    }
                    return@launch
                }

                val bluetoothAdapter = bluetoothController!!.adapter ?: return@launch
                val bluetoothAdapterEnabled = bluetoothAdapter.isEnabled

                val discoverable =
                    if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                        bluetoothAdapter.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
                    } else {
                        false
                    }

                val hidDevice = bluetoothController!!.bluetoothHidDevice
                val connectedDevices =
                    hidDevice?.connectedDevices ?: mutableListOf<BluetoothDevice?>()

                val compatibleDevices = hidDevice?.getDevicesMatchingConnectionStates(
                    intArrayOf(
                        BluetoothProfile.STATE_CONNECTING,
                        BluetoothProfile.STATE_CONNECTED,
                        BluetoothProfile.STATE_DISCONNECTED,
                        BluetoothProfile.STATE_DISCONNECTING
                    )
                ) ?: emptyList<BluetoothDevice>()

                val spinnerItems = bluetoothAdapter.bondedDevices
                    .stream()
                    .filter { d: BluetoothDevice? ->
                        compatibleDevices.contains(d)
                    }
                    .map { bluetoothDevice -> SpinnerItemDevice(ctx, bluetoothDevice) }
                    .sorted { s1: SpinnerItemDevice, s2: SpinnerItemDevice ->
                        val i1 = if (connectedDevices.contains(s1.bluetoothDevice)) 0 else 1
                        val i2 = if (connectedDevices.contains(s2.bluetoothDevice)) 0 else 1
                        if (i1 != i2) return@sorted i1.compareTo(i2)
                        s1.toString().compareTo(s2.toString())
                    }
                    .collect(Collectors.toList())

                ctx.mainExecutor.execute {
                    refreshingBtControls.set(true)
                    try {
                        val isTyping = typing.get()
                        var status = ctx.getString(R.string.bt_off)
                        var icon = androidx.compose.material.icons.Icons.Default.BluetoothDisabled

                        if (bluetoothAdapterEnabled) {
                            status = ctx.getString(R.string.bt_disconnected)
                            icon = androidx.compose.material.icons.Icons.Default.Bluetooth
                        }

                        val restoredAddress = state.selectedBluetoothAddress
                            ?: prefs.getString(PROP_LAST_SELECTED_BT_TARGET, null)
                        val targetItems = spinnerItems.map {
                            BluetoothTargetItem(
                                it.bluetoothDevice.address,
                                it.toString(),
                                it.bluetoothDevice
                            )
                        }
                        if (state.selectedBluetoothAddress == null && restoredAddress != null) {
                            onBluetoothTargetSelected(restoredAddress)
                        }

                        var isConnected = false
                        if (hidDevice != null && spinnerItems.isNotEmpty()) {
                            val selectedAddress = state.selectedBluetoothAddress ?: restoredAddress
                            val device = spinnerItems.firstOrNull { it.bluetoothDevice.address == selectedAddress }?.bluetoothDevice
                                ?: spinnerItems[0].bluetoothDevice

                            if (connectedDevices.contains(device)) {
                                status = ctx.getString(R.string.bt_connected)
                                icon = androidx.compose.material.icons.Icons.Default.BluetoothConnected
                                isConnected = true
                            }
                        }

                        updateUiState(
                            bluetoothAvailable = true,
                            bluetoothTargets = targetItems,
                            bluetoothStatusText = status,
                            bluetoothStatusIcon = icon,
                            discoverableEnabled = !discoverable && !isTyping,
                            keyboardLayoutEnabled = !isTyping,
                            bluetoothTargetEnabled = !isTyping,
                            typeButtonEnabled = isConnected && state.selectedKeyboardLayoutClassName != null && !state.message.isNullOrEmpty(),
                            delayedStrokesEnabled = !isTyping,
                            typingText = state.typingText,
                            isTyping = isTyping
                        )
                    } catch (ex: IllegalStateException) {
                        Log.e(TAG, "Context invalid", ex)
                    } finally {
                        refreshingBtControls.set(false)
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    private fun checkConnectSelectedDevice() {
        val ctx = context ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val device = getSelectedBluetoothDevice()?.bluetoothDevice ?: return
        Log.d(TAG, String.format("Selected device: %s", device.name))

        val proxy = bluetoothController?.bluetoothHidDevice ?: return

        proxy.connectedDevices.stream()
            .filter { d: BluetoothDevice? -> d?.address != device.address }
            .forEach { connectedDevice: BluetoothDevice? -> proxy.disconnect(connectedDevice) }

        val hidDevice = bluetoothController?.bluetoothHidDevice ?: return

        if (hidDevice.getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED) {
            Log.d(
                TAG, String.format(
                    "Trying to connect %s: %s",
                    device.name,
                    hidDevice.connect(device)
                )
            )
        }
    }

    fun type(strokes: MutableList<Stroke?>) {
        val ctx = context ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (typing.get()) {
            typing.set(false)
            refreshBluetoothControls()
            return
        }

        Log.d(TAG, "sending message (size: " + strokes.size + ")")

        typing.set(true)
        refreshBluetoothControls()

        viewModelScope.launch(Dispatchers.IO) {
            val bluetoothDevice = getSelectedBluetoothDevice()?.bluetoothDevice ?: return@launch
            for (s in strokes) {
                if (!typing.get()) break
                if (s == null) continue
                for (r in s.get()) {
                    if (!typing.get()) break
                    if (r == null) continue
                    try {
                        bluetoothController!!
                            .bluetoothHidDevice
                            ?.sendReport(
                                bluetoothDevice,
                                0,
                                r.report
                            )
                    } catch (ex: Exception) {
                        printStackTrace(ex)
                        typing.set(false)
                        continue
                    }
                    try {
                        val delayOn = prefs.getLong("kbd_stroke_delay_on", 50)
                        val delayOff = prefs.getLong("kbd_stroke_delay_off", 10)
                        delay((if (state.delayedStrokes) delayOn else delayOff).milliseconds)
                    } catch (e: Exception) {
                        printStackTrace(e)
                    }
                }
            }

            typing.set(false)
            refreshBluetoothControls()
        }
    }

    fun sendTestKeystroke(usage: com.onemoresecret.bt.KeyboardUsage) {
        val ctx = context ?: return
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val bluetoothDevice = getSelectedBluetoothDevice()?.bluetoothDevice ?: return@launch
            try {
                val stroke = Stroke().type(usage)
                for (r in stroke.get()) {
                    if (r == null) continue
                    bluetoothController?.bluetoothHidDevice?.sendReport(
                        bluetoothDevice,
                        0,
                        r.report
                    )
                    // Add a tiny delay between press and release
                    delay(10)
                }
            } catch (ex: Exception) {
                printStackTrace(ex)
            }
        }
    }

    inner class SpinnerItemDevice internal constructor(val ctx: Context, val bluetoothDevice: BluetoothDevice) {
        override fun toString(): String {
            try {
                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    throw RuntimeException(ctx.getString(R.string.insufficient_permissions))
                }
                return bluetoothDevice.alias ?: bluetoothDevice.name ?: bluetoothDevice.address
            } catch (_: IllegalStateException) {
                Log.e(TAG, "Context invalid")
            }
            return super.toString()
        }
    }

    val hidDeviceCallback = object : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice?, btState: Int) {
            super.onConnectionStateChanged(device, btState)
            Log.i(TAG, "onConnectionStateChanged -  device: $device, state: $btState")
            refreshBluetoothControls()
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            super.onSetReport(device, type, id, data)
            Log.i(TAG, "onSetReport - device: $device, type: $type, id: $id, data: " + byteArrayToHex(data))
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            super.onGetReport(device, type, id, bufferSize)
            Log.i(TAG, "onGetReport - device: $device, type: $type id: $id, bufferSize: $bufferSize")
        }

        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(device, registered)
            try {
                Log.i(TAG, "onAppStatusChanged -  device: $device, registered: $registered")
                val proxy = bluetoothController?.bluetoothHidDevice ?: return
                
                val ctx = context ?: return
                if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
                
                val compatibleDevices = proxy.getDevicesMatchingConnectionStates(
                    intArrayOf(
                        BluetoothProfile.STATE_CONNECTING,
                        BluetoothProfile.STATE_CONNECTED,
                        BluetoothProfile.STATE_DISCONNECTED,
                        BluetoothProfile.STATE_DISCONNECTING
                    )
                )
                Log.d(TAG, "compatible devices: $compatibleDevices")

                checkConnectSelectedDevice()
                refreshBluetoothControls()
            } catch (_: IllegalStateException) {
                Log.e(TAG, "Context invalid")
            }
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray) {
            super.onInterruptData(device, reportId, data)
            Log.d(TAG, "onInterruptData -  device: $device, reportId: $reportId, data: " + byteArrayToHex(data))
        }
    }

    class Factory(private val prefs: SharedPreferences) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OutputViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return OutputViewModel(prefs) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    data class State(
        val message: String? = null,
        val bluetoothAvailable: Boolean = false,
        val bluetoothTargets: List<BluetoothTargetItem> = emptyList(),
        val selectedBluetoothAddress: String? = null,
        val keyboardLayouts: List<KeyboardLayoutItem> = emptyList(),
        val selectedKeyboardLayoutClassName: String? = null,
        val bluetoothStatusText: String = "",
        val bluetoothStatusIcon: androidx.compose.ui.graphics.vector.ImageVector = androidx.compose.material.icons.Icons.Default.BluetoothDisabled,
        val discoverableEnabled: Boolean = false,
        val keyboardLayoutEnabled: Boolean = false,
        val bluetoothTargetEnabled: Boolean = false,
        val typeButtonEnabled: Boolean = false,
        val delayedStrokes: Boolean = false,
        val delayedStrokesEnabled: Boolean = false,
        val typingText: String = "",
        val isTyping: Boolean = false
    )

    data class BluetoothTargetItem(val address: String, val label: String, val bluetoothDevice: android.bluetooth.BluetoothDevice? = null)
    data class KeyboardLayoutItem(val className: String, val label: String, val shortName: String)

    companion object {
        private const val PROP_LAST_SELECTED_KEYBOARD_LAYOUT = "last_selected_kbd_layout"
        private const val PROP_LAST_SELECTED_BT_TARGET = "last_selected_bt_target"
        private val TAG = OutputViewModel::class.java.simpleName
    }
}
