package com.onemoresecret

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.fragment.app.viewModels
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.openUrl
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.bt.BluetoothController
import com.onemoresecret.bt.KeyboardReport
import com.onemoresecret.bt.KeyboardUsage
import com.onemoresecret.bt.layout.KeyboardLayout
import com.onemoresecret.bt.layout.Stroke
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause
import java.util.Arrays
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import kotlin.enums.EnumEntries

open class OutputFragment : FragmentWithNotificationBeforePause() {
    private var bluetoothController: BluetoothController? = null
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )

    private var bluetoothBroadcastReceiver: BluetoothBroadcastReceiver? = null
    private var preferences: SharedPreferences? = null
    private val refreshingBtControls = AtomicBoolean()
    private var clipboardManager: ClipboardManager? = null
    private val menuProvider = OutputMenuProvider()
    private var copyValue: (() -> Unit)? = null
    private var message: String? = null
    private var shareTitle = ""
    private val typing = AtomicBoolean(false)

    private val viewModel: OutputViewModel by viewModels {
        OutputViewModel.Factory(requireActivity().getPreferences(Context.MODE_PRIVATE))
    }

    fun setMessage(message: String?, shareTitle: String?) {
        this.message = message
        this.shareTitle = Objects.requireNonNullElse(shareTitle, "")
        viewModel.setShareTitle(this.shareTitle)
        refreshBluetoothControls()
        requireActivity().invalidateOptionsMenu()
    }

    override fun setBeforePause(r: Runnable?) {
        super.setBeforePause {
            typing.set(false)
            refreshBluetoothControls()
            r?.run()
        }
    }

    val selectedLayout: KeyboardLayout?
        get() = viewModel.getSelectedKeyboardLayout()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    OutputScreen(
                        state = viewModel.state,
                        onBluetoothTargetSelected = { address ->
                            if (!refreshingBtControls.get()) {
                                viewModel.onBluetoothTargetSelected(address)
                                checkConnectSelectedDevice()
                                refreshBluetoothControls()
                            }
                        },
                        onKeyboardLayoutSelected = { className ->
                            if (!refreshingBtControls.get()) {
                                viewModel.onKeyboardLayoutSelected(className)
                                refreshBluetoothControls()
                            }
                        },
                        onDelayedStrokesChanged = { enabled ->
                            viewModel.onDelayedStrokesChanged(enabled)
                        },
                        onDiscoverableClick = {
                            beforePause?.run()
                            bluetoothController?.requestDiscoverable(discoverableDuration)
                        },
                        onTypeClick = {
                            if (typing.get()) {
                                typing.set(false)
                                refreshBluetoothControls()
                                return@OutputScreen
                            }
                            val selectedLayout = viewModel.getSelectedKeyboardLayout() ?: return@OutputScreen
                            val outputMessage = message ?: return@OutputScreen
                            val strokes = selectedLayout.forString(outputMessage)

                            if (strokes.contains(null)) {
                                requireContext().mainExecutor.execute {
                                    Toast.makeText(
                                        context,
                                        getString(R.string.wrong_keyboard_layout),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                return@OutputScreen
                            }
                            type(strokes)
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(
            menuProvider,
            viewLifecycleOwner,
            androidx.lifecycle.Lifecycle.State.RESUMED
        )

        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        bluetoothController = BluetoothController(
            this,
            { },
            BluetoothHidDeviceCallback()
        )
        clipboardManager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        viewModel.initializeKeyboardLayouts()
        viewModel.setShareTitle(shareTitle)

        if (PermissionsFragment.isAllPermissionsGranted(
                TAG,
                requireContext(),
                *REQUIRED_PERMISSIONS
            )
        ) {
            onAllPermissionsGranted()
        } else {
            refreshBluetoothControls()
        }

        copyValue = {
            val clipData = ClipData.newPlainText("oneMoreSecret", message)

            val isSensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }

            val extras = PersistableBundle().apply {
                putBoolean(isSensitiveKey, true)
            }

            clipData.description.extras = extras
            clipboardManager?.setPrimaryClip(clipData)
        }
    }

    private fun onAllPermissionsGranted() {
        bluetoothBroadcastReceiver = BluetoothBroadcastReceiver()

        val ctx = requireContext()
        ctx.registerReceiver(
            bluetoothBroadcastReceiver,
            IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
        )
        ctx.registerReceiver(
            bluetoothBroadcastReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
        ctx.registerReceiver(
            bluetoothBroadcastReceiver,
            IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        )

        refreshBluetoothControls()
    }

    private fun checkConnectSelectedDevice() {
        if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val device = viewModel.getSelectedBluetoothDevice()?.bluetoothDevice ?: return
        Log.d(TAG, String.format("Selected device: %s", device.name))

        val proxy = bluetoothController!!.bluetoothHidDevice ?: return

        proxy.connectedDevices.stream()
            .filter { d: BluetoothDevice? -> d!!.address != device.address }
            .forEach { connectedDevice: BluetoothDevice? -> proxy.disconnect(connectedDevice) }

        if (bluetoothController!!.bluetoothHidDevice
                .getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED
        ) {
            Log.d(
                TAG, String.format(
                    "Trying to connect %s: %s",
                    device.name,
                    bluetoothController!!.bluetoothHidDevice.connect(device)
                )
            )
        }
    }

    protected fun type(list: MutableList<Stroke?>) {
        if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        Log.d(TAG, "sending message (size: " + list.size + ")")

        typing.set(true)
        refreshBluetoothControls()

        Thread {
            val bluetoothDevice = viewModel.getSelectedBluetoothDevice()?.bluetoothDevice ?: return@Thread
            list.stream()
                .filter { _: Stroke? -> typing.get() }
                .flatMap<KeyboardReport?> { s: Stroke? -> s!!.get().stream() }
                .forEach { r: KeyboardReport? ->
                    try {
                        bluetoothController!!
                            .bluetoothHidDevice
                            .sendReport(
                                bluetoothDevice,
                                0,
                                r!!.report
                            )
                    } catch (ex: Exception) {
                        printStackTrace(ex)
                        typing.set(false)
                        return@forEach
                    }
                    try {
                        Thread.sleep(if (viewModel.state.delayedStrokes) keyStrokeDelayOn else keyStrokeDelayOff)
                    } catch (e: InterruptedException) {
                        printStackTrace(e)
                    }
                }

            typing.set(false)
            refreshBluetoothControls()
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        typing.set(false)

        if (bluetoothBroadcastReceiver != null) requireContext().unregisterReceiver(
            bluetoothBroadcastReceiver
        )

        bluetoothController?.destroy()
        copyValue = null
    }

    protected fun refreshBluetoothControls() {
        if (refreshingBtControls.get()) return

        Thread(Runnable {
            try {
                if (!bluetoothController!!.isBluetoothAvailable ||
                    !PermissionsFragment.isAllPermissionsGranted(
                        TAG,
                        requireContext(),
                        *REQUIRED_PERMISSIONS
                    )
                ) {
                    requireContext().mainExecutor.execute {
                        refreshingBtControls.set(true)
                        try {
                            viewModel.updateUiState(
                                bluetoothAvailable = false,
                                bluetoothTargets = emptyList(),
                                bluetoothStatusText = getString(R.string.bt_not_available),
                                bluetoothStatusIcon = R.drawable.ic_baseline_bluetooth_disabled_24,
                                discoverableEnabled = false,
                                keyboardLayoutEnabled = false,
                                bluetoothTargetEnabled = false,
                                typeButtonEnabled = false,
                                delayedStrokesEnabled = false,
                                typingText = shareTitle,
                                isTyping = false
                            )
                        } catch (_: IllegalStateException) {
                            Log.e(
                                TAG,
                                String.format("%s not attached to a context", this@OutputFragment)
                            )
                        } finally {
                            refreshingBtControls.set(false)
                        }
                    }
                    return@Runnable
                }

                val bluetoothAdapter = bluetoothController!!.adapter
                val bluetoothAdapterEnabled = bluetoothAdapter.isEnabled

                if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return@Runnable
                }

                val discoverable =
                    bluetoothAdapter.scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE

                val connectedDevices =
                    if (bluetoothController!!.bluetoothHidDevice == null) mutableListOf<BluetoothDevice?>() else bluetoothController!!.bluetoothHidDevice
                        .connectedDevices

                val spinnerItems = bluetoothAdapter.bondedDevices
                    .stream()
                    .filter { d: BluetoothDevice? ->
                        d!!.bluetoothClass
                            .majorDeviceClass == BluetoothClass.Device.Major.COMPUTER
                    }
                    .map { bluetoothDevice -> SpinnerItemDevice(bluetoothDevice) }
                    .sorted { s1: SpinnerItemDevice?, s2: SpinnerItemDevice? ->
                        val i1 = if (connectedDevices.contains(s1!!.bluetoothDevice)) 0 else 1
                        val i2 = if (connectedDevices.contains(s2!!.bluetoothDevice)) 0 else 1
                        if (i1 != i2) return@sorted i1.compareTo(i2)
                        s1.toString().compareTo(s2.toString())
                    }
                    .collect(Collectors.toList())

                requireContext().mainExecutor.execute(Runnable {
                    refreshingBtControls.set(true)
                    try {
                        val isTyping = typing.get()
                        var status = getString(R.string.bt_off)
                        var icon = R.drawable.ic_baseline_bluetooth_disabled_24

                        if (bluetoothAdapterEnabled) {
                            status = getString(R.string.bt_disconnected)
                            icon = R.drawable.ic_baseline_bluetooth_24
                        }

                        val restoredAddress = viewModel.state.selectedBluetoothAddress
                            ?: preferences!!.getString(PROP_LAST_SELECTED_BT_TARGET, null)
                        val targetItems = spinnerItems.map {
                            OutputViewModel.BluetoothTargetItem(it.bluetoothDevice.address, it.toString(), it.bluetoothDevice)
                        }
                        if (viewModel.state.selectedBluetoothAddress == null && restoredAddress != null) {
                            viewModel.onBluetoothTargetSelected(restoredAddress)
                        }

                        val selectedBluetoothTarget = spinnerItems.firstOrNull {
                            it.bluetoothDevice.address == viewModel.state.selectedBluetoothAddress
                        }
                        var selectedDeviceConnected = false

                        if (selectedBluetoothTarget != null) {
                            val selectedBtAddress = selectedBluetoothTarget.bluetoothDevice.address
                            selectedDeviceConnected = connectedDevices.stream()
                                .anyMatch { d: BluetoothDevice? -> d!!.address == selectedBtAddress }
                            if (selectedDeviceConnected) {
                                status = getString(R.string.bt_connected)
                                icon = R.drawable.ic_baseline_bluetooth_connected_24
                            }
                        }

                        val selectedLayout = viewModel.getSelectedKeyboardLayout()
                        viewModel.updateUiState(
                            bluetoothAvailable = true,
                            bluetoothTargets = targetItems,
                            bluetoothStatusText = status,
                            bluetoothStatusIcon = icon,
                            discoverableEnabled = bluetoothAdapterEnabled && !discoverable && !isTyping,
                            keyboardLayoutEnabled = !isTyping,
                            bluetoothTargetEnabled = bluetoothAdapterEnabled && !isTyping,
                            typeButtonEnabled = selectedDeviceConnected && selectedLayout != null && message != null,
                            delayedStrokesEnabled = selectedDeviceConnected && !isTyping,
                            typingText = if (isTyping) getString(R.string.typing_please_wait) else shareTitle,
                            isTyping = isTyping
                        )
                    } catch (_: IllegalStateException) {
                        Log.e(
                            TAG,
                            String.format("%s not attached to a context", this@OutputFragment)
                        )
                    } finally {
                        refreshingBtControls.set(false)
                    }
                })
            } catch (_: IllegalStateException) {
                Log.e(TAG, String.format("%s not attached to a context", this@OutputFragment))
            }
        }).start()
    }

    internal inner class BluetoothBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return

            Log.d(
                TAG,
                String.format(
                    "Got intent %s, state %s",
                    intent.action,
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                )
            )

            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val newState =
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (newState == BluetoothAdapter.STATE_ON) {
                    Log.d(TAG, "Bluetooth is now on")
                }
            }

            refreshBluetoothControls()
        }
    }

    inner class SpinnerItemDevice internal constructor(val bluetoothDevice: BluetoothDevice) {
        override fun toString(): String {
            try {
                if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    throw RuntimeException(getString(R.string.insufficient_permissions))
                }
                return bluetoothDevice.alias + " (" + bluetoothDevice.address + ")"
            } catch (_: IllegalStateException) {
                Log.e(TAG, String.format("%s not attached to a context", this@OutputFragment))
            }

            return super.toString()
        }
    }

    inner class BluetoothHidDeviceCallback : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            super.onConnectionStateChanged(device, state)
            Log.i(TAG, "onConnectionStateChanged -  device: $device, state: $state")

            refreshBluetoothControls()
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            super.onSetReport(device, type, id, data)
            Log.i(
                TAG,
                "onSetReport - device: $device, type: $type, id: $id, data: " + byteArrayToHex(
                    data
                )
            )
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            super.onGetReport(device, type, id, bufferSize)
            Log.i(
                TAG,
                "onGetReport - device: $device, type: $type id: $id, bufferSize: $bufferSize"
            )
        }

        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            super.onAppStatusChanged(device, registered)

            try {
                Log.i(
                    TAG,
                    "onAppStatusChanged -  device: $device, registered: $registered"
                )

                if (ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }

                val compatibleDevices = bluetoothController!!.bluetoothHidDevice
                    .getDevicesMatchingConnectionStates(
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
                Log.e(TAG, String.format("%s not attached to a context", this@OutputFragment))
            }
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray) {
            super.onInterruptData(device, reportId, data)
            Log.d(
                TAG,
                "onInterruptData - -  device: $device, reportId: $reportId, data: " + byteArrayToHex(
                    data
                )
            )
        }
    }

    inner class OutputMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_output, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            menu.setGroupVisible(R.id.menuGroupOutputAll, message != null)
            super.onPrepareMenu(menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (!isAdded || context == null) return true

            if (menuItem.itemId == R.id.menuItemOutputCopy) {
                copyValue!!()
            } else if (menuItem.itemId == R.id.menuItemShare) {
                beforePause?.run()

                val sendIntent = Intent()
                sendIntent.action = Intent.ACTION_SEND
                sendIntent.putExtra(Intent.EXTRA_TEXT, message)
                sendIntent.putExtra(Intent.EXTRA_TITLE, shareTitle)
                sendIntent.type = "text/plain"

                val shareIntent = Intent.createChooser(sendIntent, null)
                startActivity(shareIntent)
            } else if (menuItem.itemId == R.id.menuItemOutputHelp) {
                openUrl(R.string.autotype_md_url, requireContext())
            } else if (menuItem.itemId == R.id.menuItemKeyboardTool) {
                showKeyboardTestTool()
            } else {
                return false
            }

            return true
        }
    }

    private val discoverableDuration: Int
        get() = preferences!!.getInt(
            PROP_BT_DISCOVERABLE_DURATION,
            DEF_DISCOVERABLE_DURATION_S
        )

    private val keyStrokeDelayOn: Long
        get() = preferences!!.getLong(
            PROP_KEY_STROKE_DELAY_ON,
            DEF_KEY_STROKE_DELAY_ON
        )

    private val keyStrokeDelayOff: Long
        get() = preferences!!.getLong(
            PROP_KEY_STROKE_DELAY_OFF,
            DEF_KEY_STROKE_DELAY_OFF
        )

    private fun showKeyboardTestTool() {
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.layout_keboard_test, null)
        val listView = dialogView.findViewById<ListView>(R.id.listViewUsbUsage)
        val items: EnumEntries<KeyboardUsage> = KeyboardUsage.entries

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            items
        )

        listView.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val selectedItem = items[position]
                Toast.makeText(requireContext(), selectedItem.toString(), Toast.LENGTH_SHORT).show()

                if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return@OnItemClickListener
                }

                val rArr: Array<KeyboardReport> = arrayOf(
                    KeyboardReport(selectedItem),
                    KeyboardReport(KeyboardUsage.KBD_NONE),
                    KeyboardReport(KeyboardUsage.KBD_SPACE),
                    KeyboardReport(KeyboardUsage.KBD_NONE)
                )

                val bluetoothDevice = viewModel.getSelectedBluetoothDevice()?.bluetoothDevice ?: return@OnItemClickListener
                Arrays.stream(rArr).forEach { r: KeyboardReport? ->
                    try {
                        bluetoothController!!
                            .bluetoothHidDevice
                            .sendReport(
                                bluetoothDevice,
                                0,
                                r!!.report
                            )
                    } catch (ex: Exception) {
                        printStackTrace(ex)
                        typing.set(false)
                    }
                }
            }

        viewModel.getSelectedKeyboardLayout()?.logLayout()
        dialog.show()
    }

    companion object {
        private val TAG: String = OutputFragment::class.java.simpleName
        private const val PROP_BT_DISCOVERABLE_DURATION = "bt_discoverable_duration_s"
        private const val PROP_KEY_STROKE_DELAY_ON = "kbd_stroke_delay_on"
        private const val PROP_KEY_STROKE_DELAY_OFF = "kbd_stroke_delay_off"
        private const val PROP_LAST_SELECTED_BT_TARGET = "last_selected_bt_target"
        private const val DEF_DISCOVERABLE_DURATION_S = 60
        private const val DEF_KEY_STROKE_DELAY_ON: Long = 50
        private const val DEF_KEY_STROKE_DELAY_OFF: Long = 10
    }
}
