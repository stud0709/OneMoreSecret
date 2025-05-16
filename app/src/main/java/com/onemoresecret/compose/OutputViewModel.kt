package com.onemoresecret.compose

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.AndroidViewModel
import com.onemoresecret.MainActivity
import com.onemoresecret.OutputFragment
import com.onemoresecret.PermissionsFragment
import com.onemoresecret.R
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.openUrl
import com.onemoresecret.bt.BluetoothController
import com.onemoresecret.bt.KeyboardReport
import com.onemoresecret.bt.KeyboardUsage
import com.onemoresecret.bt.layout.KeyboardLayout
import com.onemoresecret.bt.layout.Stroke
import java.util.Arrays
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import androidx.core.content.edit

class OutputViewModel(
    application: Application,
    val resourceProvider: ResourceProvider,
    activityResultLauncher: ActivityResultLauncher<Intent>
) :
    AndroidViewModel(application) {

    private val preferences: SharedPreferences =
        application.applicationContext.getSharedPreferences(
            MainActivity.SPARED_PREF_NAME,
            Context.MODE_PRIVATE
        )

    internal val bluetoothController = BluetoothController(
        application.applicationContext as MainActivity,
        activityResultLauncher,
        BluetoothHidDeviceCallback()
    )
    private var arrayAdapterDevice: ArrayAdapter<SpinnerItemDevice>? = null

    private val refreshingBtControls = AtomicBoolean()
    private val clipboardManager =
        resourceProvider.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val menuProvider = OutputMenuProvider()
    private var copyValue: Runnable? = null
    private var message: String? = null
    private var shareTitle = ""

    internal val keyboardLayouts = KeyboardLayout.knownSubclasses.map{ subclass ->
        subclass.getDeclaredConstructor().newInstance() as KeyboardLayout
    }.map { it.toString() }.sorted().toMutableStateList()

    internal val selectedKeyboardLayout = mutableStateOf("")
    internal var keyboardLayoutInstance: KeyboardLayout? = null;

    internal val bluetoothTargets = mutableStateListOf<String>()
    internal val selectedBluetoothTarget = mutableStateOf("")
    internal val bluetoothBroadcastReceiver = BluetoothBroadcastReceiver()

    private val typing = AtomicBoolean(false)

    internal val allPermissionsGranted = mutableStateOf<Boolean>(false)

    fun setMessage(message: String?, shareTitle: String?) {
        this.message = message
        this.shareTitle = Objects.requireNonNullElse(shareTitle, "")
        refreshBluetoothControls()
        //requireActivity().invalidateOptionsMenu()
    }

/*
    override fun setBeforePause(r: Runnable?) {
        super.setBeforePause {
            typing.set(false)
            r?.run()
        }
    }
*/
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
       // super.onViewCreated(view, savedInstanceState)

        //requireActivity().addMenuProvider(menuProvider)


        initSpinnerTargetDevice()

        initSpinnerKeyboardLayout()

        binding!!.btnType.setOnClickListener { v: View? ->
            if (typing.get()) {
                //cancel typing
                typing.set(false)
                return@setOnClickListener
            }
            val selectedLayout = binding!!.spinnerKeyboardLayout.selectedItem as KeyboardLayout
            val strokes = selectedLayout.forString(
                message!!
            )

            if (strokes.contains(null)) {
                //not all characters could be converted into key strokes
                requireContext().mainExecutor.execute {
                    Toast.makeText(
                        context,
                        getString(R.string.wrong_keyboard_layout),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@setOnClickListener
            }
            type(strokes)
        }

        binding!!.textTyping.text = shareTitle

        copyValue = Runnable {
            val extra_is_sensitive =
                "android.content.extra.IS_SENSITIVE" /* replace with  ClipDescription.EXTRA_IS_SENSITIVE for API Level 33+ */
            val clipData = ClipData.newPlainText("oneMoreSecret", message)
            val persistableBundle = PersistableBundle()
            persistableBundle.putBoolean(extra_is_sensitive, true)
            clipData.description.extras = persistableBundle
            clipboardManager!!.setPrimaryClip(clipData)
        }
    }

    internal fun onBluetoothTargetSelected(value: String) {
        selectedKeyboardLayout.value = value
        preferences.edit() {
            putString(
                PROP_LAST_SELECTED_BT_TARGET,
                selectedBluetoothTarget.value
            )
        }

        checkConnectSelectedDevice()
        refreshBluetoothControls()
    }

    internal fun onKeyboardLayoutSelected(value: String) {
        selectedBluetoothTarget.value = value

        preferences.edit() {
            putString(
                PROP_LAST_SELECTED_KEYBOARD_LAYOUT,
                selectedKeyboardLayout.value
            )
        }

        //initialize the selected layout
        keyboardLayoutInstance = KeyboardLayout.knownSubclasses.map { it -> it.getDeclaredConstructor().newInstance() as KeyboardLayout }
            .find { it.toString() == selectedKeyboardLayout.value }

        refreshBluetoothControls()
    }

    /**
     * Check if the selected device is connected, try to connect.
     */
    private fun checkConnectSelectedDevice() {
        //if (binding == null) return
        if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val selectedSpinnerItem = binding!!.spinnerBluetoothTarget.selectedItem as SpinnerItemDevice
            ?: return
        val device = selectedSpinnerItem.bluetoothDevice
        Log.d(TAG, String.format("Selected device: %s", device.name))

        //disconnect if anything connected
        val proxy = bluetoothController!!.bluetoothHidDevice ?: return
        //https://github.com/stud0709/OneMoreSecret/issues/11


        proxy.connectedDevices.stream().filter { d: BluetoothDevice -> d.address != device.address }
            .forEach { d: BluetoothDevice? -> proxy.disconnect(d) }

        if (bluetoothController!!.bluetoothHidDevice!!.getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED) {
            Log.d(
                TAG, String.format(
                    "Trying to connect %s: %s",
                    device.name,
                    bluetoothController!!.bluetoothHidDevice!!.connect(device)
                )
            )
        }
    }

    protected fun type(list: List<Stroke?>) {
        if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        Log.d(TAG, "sending message (size: " + list.size + ")")

        typing.set(true)
        refreshBluetoothControls()

        Thread {
            val bluetoothDevice = (binding!!
                .spinnerBluetoothTarget
                .selectedItem as SpinnerItemDevice).bluetoothDevice
            list.stream()
                .filter { s: Stroke? -> typing.get() }
                .flatMap<KeyboardReport> { s: Stroke? ->
                    s!!.get().stream()
                }
                .forEach { r: KeyboardReport ->
                    try {
                        bluetoothController!!
                            .bluetoothHidDevice!!
                            .sendReport(
                                bluetoothDevice,
                                0,
                                r.report
                            )
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        typing.set(false)
                        return@forEach
                    }
                    try {
                        Thread.sleep(if (binding!!.swDelayedStrokes.isChecked) keyStrokeDelayOn else keyStrokeDelayOff)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }

            typing.set(false)
            refreshBluetoothControls()
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        typing.set(false) //cancel typing, if any

        if (bluetoothBroadcastReceiver != null) requireContext().unregisterReceiver(
            bluetoothBroadcastReceiver
        )

        if (bluetoothController != null) bluetoothController!!.destroy()

        binding!!.btnType.setOnClickListener(null)
        requireActivity().removeMenuProvider(menuProvider)
        copyValue = null
        //binding = null
    }

    internal fun refreshBluetoothControls() {
        if (binding == null) return
        if (refreshingBtControls.get()) return  //called in loop


        Thread(Runnable {
            if (binding == null) return@Runnable  //already being destroyed
            try {
                if (!bluetoothController!!.isBluetoothAvailable ||
                    !PermissionsFragment.isAllPermissionsGranted(
                        TAG,
                        requireContext(),
                        *REQUIRED_PERMISSIONS
                    )
                ) {
                    //disable all bluetooth functionality

                    requireContext().mainExecutor.execute {
                        refreshingBtControls.set(true)
                        try {
                            val drawable = ResourcesCompat.getDrawable(
                                resources,
                                R.drawable.ic_baseline_bluetooth_disabled_24,
                                requireContext().theme
                            )
                            binding!!.chipBtStatus.chipIcon = drawable
                            binding!!.chipBtStatus.text = getString(R.string.bt_not_available)

                            binding!!.spinnerKeyboardLayout.isEnabled = false
                            binding!!.spinnerBluetoothTarget.isEnabled = false
                            binding!!.btnType.isEnabled = false
                            binding!!.imgButtonDiscoverable.isEnabled = false
                            binding!!.swDelayedStrokes.isEnabled = false
                            binding!!.textTyping.text = shareTitle
                        } catch (ex: IllegalStateException) {
                            //things are happening outside the context
                            Log.e(
                                TAG,
                                String.format(
                                    "%s not attached to a context",
                                    this@OutputFragment
                                )
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
                    if (bluetoothController!!.bluetoothHidDevice == null) emptyList() else bluetoothController!!.bluetoothHidDevice!!.connectedDevices

                val spinnerItems = bluetoothAdapter.bondedDevices
                    .stream()
                    .filter { d: BluetoothDevice -> d.bluetoothClass.majorDeviceClass == BluetoothClass.Device.Major.COMPUTER }
                    .map<SpinnerItemDevice> { bluetoothDevice: BluetoothDevice ->
                        SpinnerItemDevice(
                            bluetoothDevice
                        )
                    }
                    .sorted { s1: SpinnerItemDevice, s2: SpinnerItemDevice ->
                        val i1 = if (connectedDevices.contains(
                                s1.bluetoothDevice
                            )
                        ) 0 else 1
                        val i2 = if (connectedDevices.contains(s2.bluetoothDevice)) 0 else 1
                        if (i1 != i2) return@sorted Integer.compare(i1, i2)
                        s1.toString().compareTo(s2.toString())
                    }
                    .collect(Collectors.toList<SpinnerItemDevice>())

                requireContext().mainExecutor.execute {
                    if (binding == null) return@execute  //already being destroyed


                    refreshingBtControls.set(true)
                    try {
                        binding!!.imgButtonDiscoverable.isEnabled =
                            bluetoothAdapterEnabled && !discoverable

                        var status = getString(R.string.bt_off)
                        var drawable = ResourcesCompat.getDrawable(
                            resources,
                            R.drawable.ic_baseline_bluetooth_disabled_24,
                            requireContext().theme
                        )

                        if (bluetoothAdapterEnabled) {
                            status = getString(R.string.bt_disconnected)
                            drawable = ResourcesCompat.getDrawable(
                                resources,
                                R.drawable.ic_baseline_bluetooth_24,
                                requireContext().theme
                            )
                        }

                        binding!!.spinnerBluetoothTarget.isEnabled = bluetoothAdapterEnabled

                        //remember selection
                        run {
                            val selectedBluetoothTarget =
                                binding!!.spinnerBluetoothTarget.selectedItem as SpinnerItemDevice
                            val selectedBtAddress =
                                if (selectedBluetoothTarget == null) preferences!!.getString(
                                    PROP_LAST_SELECTED_BT_TARGET,
                                    null
                                ) else selectedBluetoothTarget.bluetoothDevice.address

                            //refreshing the list
                            arrayAdapterDevice!!.clear()
                            arrayAdapterDevice!!.addAll(spinnerItems)

                            //restore selection
                            if (selectedBtAddress != null) {
                                for (i in 0..<arrayAdapterDevice!!.count) {
                                    if (arrayAdapterDevice!!.getItem(i)!!.bluetoothDevice.address == selectedBtAddress) {
                                        binding!!.spinnerBluetoothTarget.setSelection(i)
                                        break
                                    }
                                }
                            }
                        }

                        //set BT connection state
                        val selectedBluetoothTarget =
                            binding!!.spinnerBluetoothTarget.selectedItem as SpinnerItemDevice
                        var selectedDeviceConnected = false

                        if (selectedBluetoothTarget != null) {
                            val selectedBtAddress = selectedBluetoothTarget.bluetoothDevice.address
                            selectedDeviceConnected = connectedDevices.stream()
                                .anyMatch { d: BluetoothDevice -> d.address == selectedBtAddress }
                            if (selectedDeviceConnected) {
                                status = getString(R.string.bt_connected)
                                drawable = ResourcesCompat.getDrawable(
                                    resources,
                                    R.drawable.ic_baseline_bluetooth_connected_24,
                                    requireContext().theme
                                )
                            }
                        }

                        binding!!.chipBtStatus.chipIcon = drawable
                        binding!!.chipBtStatus.text = status
                        binding!!.swDelayedStrokes.isEnabled = selectedDeviceConnected

                        //set TYPE button state
                        val selectedLayout =
                            binding!!.spinnerKeyboardLayout.selectedItem as KeyboardLayout
                        binding!!.btnType.isEnabled =
                            selectedDeviceConnected && selectedLayout != null && message != null

                        binding!!.btnType.text =
                            if (typing.get()) getString(R.string.cancel) else getString(R.string.type)

                        binding!!.textTyping.text =
                            if (typing.get()) getText(R.string.typing_please_wait) else shareTitle
                    } catch (ex: IllegalStateException) {
                        //things are happening outside the context
                        Log.e(
                            TAG,
                            String.format(
                                "%s not attached to a context",
                                this@OutputFragment
                            )
                        )
                    } finally {
                        refreshingBtControls.set(false)
                    }
                }
            } catch (ex: IllegalStateException) {
                //things are happening outside the context
                Log.e(
                    TAG, String.format(
                        "%s not attached to a context",
                        this@OutputFragment
                    )
                )
            }
        }).start()
    }

    internal inner class BluetoothBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
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
            } catch (ex: IllegalStateException) {
                //things are happening outside the context
                Log.e(
                    TAG, String.format(
                        "%s not attached to a context",
                        this@OutputFragment
                    )
                )
            }

            return super.toString()
        }
    }

    inner class BluetoothHidDeviceCallback : BluetoothHidDevice.Callback() {
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            super.onConnectionStateChanged(device, state)
            Log.i(
                TAG,
                "onConnectionStateChanged -  device: $device, state: $state"
            )

            refreshBluetoothControls()
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            super.onSetReport(device, type, id, data)
            Log.i(
                TAG,
                "onSetReport - device: " + device.toString() + ", type: " + type + ", id: " + id + ", data: " + byteArrayToHex(
                    data
                )
            )
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            super.onGetReport(device, type, id, bufferSize)
            Log.i(
                TAG,
                "onGetReport - device: $device, type: $type id: $id, bufferSize: $bufferSize"
            )
        }

        override fun onAppStatusChanged(device: BluetoothDevice, registered: Boolean) {
            super.onAppStatusChanged(device, registered)

            if (binding == null) return

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

                val compatibleDevices =
                    bluetoothController!!.bluetoothHidDevice!!.getDevicesMatchingConnectionStates(
                        intArrayOf(
                            BluetoothProfile.STATE_CONNECTING,
                            BluetoothProfile.STATE_CONNECTED,
                            BluetoothProfile.STATE_DISCONNECTED,
                            BluetoothProfile.STATE_DISCONNECTING
                        )
                    )
                Log.d(
                    TAG,
                    "compatible devices: $compatibleDevices"
                )

                checkConnectSelectedDevice()
                refreshBluetoothControls()
            } catch (ex: IllegalStateException) {
                //things are happening outside the context
                Log.e(
                    TAG, String.format(
                        "%s not attached to a context",
                        this@OutputFragment
                    )
                )
            }
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {
            super.onInterruptData(device, reportId, data)
            Log.d(
                TAG,
                "onInterruptData - -  device: " + device + ", reportId: " + reportId + ", data: " + byteArrayToHex(
                    data
                )
            )

            //            boolean numLockActive = (data[0] & KeyboardReport.NUM_LOCK) == KeyboardReport.NUM_LOCK;
//            boolean capsLockActive = (data[0] & KeyboardReport.CAPS_LOCK) == KeyboardReport.CAPS_LOCK;
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
            if (!isAdded || context == null) return true //https://github.com/stud0709/OneMoreSecret/issues/10


            if (menuItem.itemId == R.id.menuItemOutputCopy) {
                copyValue!!.run()
            } else if (menuItem.itemId == R.id.menuItemShare) {
                if (beforePause != null) beforePause!!.run()

                val sendIntent = Intent()
                sendIntent.setAction(Intent.ACTION_SEND)
                sendIntent.putExtra(Intent.EXTRA_TEXT, message)

                sendIntent.putExtra(Intent.EXTRA_TITLE, shareTitle)
                sendIntent.setType("text/plain")

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

    internal val discoverableDuration: Int
        get() = preferences.getInt(
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
        // Inflate the custom layout
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.layout_keboard_test, null)

        // Initialize the ListView and Button
        val listView = dialogView.findViewById<ListView>(R.id.listViewUsbUsage)

        val items = KeyboardUsage.entries.toTypedArray()

        // Set up the ListView with an ArrayAdapter
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            items
        )

        listView.adapter = adapter

        // Set up the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Set item click listener for the ListView
        listView.onItemClickListener =
            OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                val selectedItem =
                    items[position]
                Toast.makeText(requireContext(), selectedItem.toString(), Toast.LENGTH_SHORT).show()

                if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return@OnItemClickListener
                }

                val rArr = arrayOf(
                    KeyboardReport(selectedItem),  //usage without any modifiers
                    KeyboardReport(KeyboardUsage.KBD_NONE),  //release
                    KeyboardReport(KeyboardUsage.KBD_SPACE),  //trigger things like ¨ or ^
                    KeyboardReport(KeyboardUsage.KBD_NONE)
                )

                val bluetoothDevice = (binding!!
                    .spinnerBluetoothTarget
                    .selectedItem as SpinnerItemDevice).bluetoothDevice
                Arrays.stream<KeyboardReport>(rArr).forEach { r: KeyboardReport ->
                    try {
                        bluetoothController!!
                            .bluetoothHidDevice!!
                            .sendReport(
                                bluetoothDevice,
                                0,
                                r.report
                            )
                        Log.d(TAG, "sent: $r")
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                        typing.set(false)
                        return@forEach
                    }
                }
            }

        //log the current layout
        val selectedLayout = binding!!.spinnerKeyboardLayout.selectedItem as KeyboardLayout
        selectedLayout.logLayout()

        // Show the dialog
        dialog.show()
    }

    companion object {
        internal val TAG: String = OutputFragment::class.java.simpleName
        private const val PROP_BT_DISCOVERABLE_DURATION = "bt_discoverable_duration_s"
        private const val PROP_KEY_STROKE_DELAY_ON = "kbd_stroke_delay_on"
        private const val PROP_KEY_STROKE_DELAY_OFF = "kbd_stroke_delay_off"
        private const val PROP_LAST_SELECTED_KEYBOARD_LAYOUT = "last_selected_kbd_layout"
        private const val PROP_LAST_SELECTED_BT_TARGET = "last_selected_bt_target"
        private const val DEF_DISCOVERABLE_DURATION_S = 60
        private const val DEF_KEY_STROKE_DELAY_ON: Long = 50
        private const val DEF_KEY_STROKE_DELAY_OFF: Long = 10
        internal val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    }
}