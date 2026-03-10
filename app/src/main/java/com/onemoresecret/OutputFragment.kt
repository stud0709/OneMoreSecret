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
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.openUrl
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.bt.BluetoothController
import com.onemoresecret.bt.KeyboardReport
import com.onemoresecret.bt.KeyboardUsage
import com.onemoresecret.bt.layout.KeyboardLayout
import com.onemoresecret.bt.layout.Stroke
import com.onemoresecret.databinding.FragmentOutputBinding
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause
import java.util.Arrays
import java.util.Objects
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import kotlin.enums.EnumEntries
import androidx.core.content.edit

class OutputFragment : FragmentWithNotificationBeforePause() {
    private var bluetoothController: BluetoothController? = null
    private var arrayAdapterDevice: ArrayAdapter<SpinnerItemDevice?>? = null
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE
    )

    private var binding: FragmentOutputBinding? = null
    private var bluetoothBroadcastReceiver: BluetoothBroadcastReceiver? = null

    private var preferences: SharedPreferences? = null
    private val refreshingBtControls = AtomicBoolean()
    private var clipboardManager: ClipboardManager? = null
    private val menuProvider = OutputMenuProvider()
    private var copyValue: (()->Unit)? = null
    private var message: String? = null
    private var shareTitle = ""


    private val typing = AtomicBoolean(false)

    fun setMessage(message: String?, shareTitle: String?) {
        this.message = message
        this.shareTitle = Objects.requireNonNullElse(shareTitle, "")
        refreshBluetoothControls()
        requireActivity().invalidateOptionsMenu()
    }

    override fun setBeforePause(r: Runnable?) {
        super.setBeforePause {
            typing.set(false)
            r?.run()
        }
    }

    val selectedLayout: KeyboardLayout?
        get() = binding!!.spinnerKeyboardLayout.getSelectedItem() as KeyboardLayout?

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOutputBinding.inflate(inflater, container, false)
        return binding!!.getRoot()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(menuProvider)

        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        bluetoothController = BluetoothController(
            this,
            { },
            BluetoothHidDeviceCallback()
        )

        clipboardManager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

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

        initSpinnerTargetDevice()

        initSpinnerKeyboardLayout()

        binding!!.btnType.setOnClickListener(View.OnClickListener { _: View? ->
            if (typing.get()) {
                //cancel typing
                typing.set(false)
                return@OnClickListener
            }
            val selectedLayout = binding!!.spinnerKeyboardLayout.getSelectedItem() as KeyboardLayout
            val strokes = selectedLayout.forString(message!!)

            if (strokes.contains(null)) {
                //not all characters could be converted into keystrokes
                requireContext().mainExecutor.execute {
                    Toast.makeText(
                        context,
                        getString(R.string.wrong_keyboard_layout),
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@OnClickListener
            }
            type(strokes)
        })

        binding!!.textTyping.text = shareTitle

        copyValue = {
            val clipData = ClipData.newPlainText("oneMoreSecret", message)

            val isSensitiveKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClipDescription.EXTRA_IS_SENSITIVE
            } else {
                "android.content.extra.IS_SENSITIVE"
            }

            // Initialize as PersistableBundle to match the expected type exactly
            val extras = PersistableBundle().apply {
                putBoolean(isSensitiveKey, true)
            }

            clipData.description.extras = extras
            clipboardManager?.setPrimaryClip(clipData)
        }
    }

    private fun initSpinnerTargetDevice() {
        arrayAdapterDevice =
            ArrayAdapter<SpinnerItemDevice?>(requireContext(), android.R.layout.simple_spinner_item)
        arrayAdapterDevice!!.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding!!.spinnerBluetoothTarget.setAdapter(arrayAdapterDevice)
        binding!!.spinnerBluetoothTarget.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (refreshingBtControls.get()) return
                val selectedItem =
                    binding!!.spinnerBluetoothTarget.getSelectedItem() as SpinnerItemDevice
                preferences!!.edit {
                    putString(
                        PROP_LAST_SELECTED_BT_TARGET,
                        selectedItem.bluetoothDevice.getAddress()
                    )
                }

                checkConnectSelectedDevice()
                refreshBluetoothControls()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (refreshingBtControls.get()) return
                refreshBluetoothControls()
            }
        }
    }

    private fun initSpinnerKeyboardLayout() {
        val keyboardLayoutAdapter =
            ArrayAdapter<KeyboardLayout?>(requireContext(), android.R.layout.simple_spinner_item)
        keyboardLayoutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        Arrays.stream(KeyboardLayout.knownSubclasses)
            .map<KeyboardLayout?> { clazz: Class<*>? ->
                try {
                    return@map clazz!!.getDeclaredConstructor().newInstance() as KeyboardLayout?
                } catch (e: Exception) {
                    printStackTrace(e)
                    return@map null
                }
            }
            .filter { obj -> Objects.nonNull(obj) }  /* just in case */
            .sorted(Comparator.comparing { obj -> obj.toString() })
            .forEach { `object`: KeyboardLayout? -> keyboardLayoutAdapter.add(`object`) }

        binding!!.spinnerKeyboardLayout.setAdapter(keyboardLayoutAdapter)

        //select last used keyboard layout
        val lastSelectedKeyboardLayout =
            preferences!!.getString(PROP_LAST_SELECTED_KEYBOARD_LAYOUT, null)
        for (i in 0..<keyboardLayoutAdapter.count) {
            if (Objects.requireNonNull<KeyboardLayout?>(keyboardLayoutAdapter.getItem(i)).javaClass.getName() == lastSelectedKeyboardLayout) {
                binding!!.spinnerKeyboardLayout.setSelection(i)
                break
            }
        }

        binding!!.spinnerKeyboardLayout.onItemSelectedListener = object :
            AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (refreshingBtControls.get()) return

                val selectedLayout =
                    binding!!.spinnerKeyboardLayout.getSelectedItem() as KeyboardLayout
                preferences!!.edit {
                    putString(
                        PROP_LAST_SELECTED_KEYBOARD_LAYOUT,
                        selectedLayout.javaClass.getName()
                    )
                }

                refreshBluetoothControls()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                if (refreshingBtControls.get()) return
                refreshBluetoothControls()
            }
        }
    }

    private fun onAllPermissionsGranted() {
        //register broadcast receiver for BT events
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

        //initialize "request discoverable" button
        binding!!.imgButtonDiscoverable.setOnClickListener { _: View? ->
            if (beforePause != null) beforePause.run()
            bluetoothController!!.requestDiscoverable(this.discoverableDuration)
        }

        refreshBluetoothControls()
    }

    /**
     * Check if the selected device is connected, try to connect.
     */
    private fun checkConnectSelectedDevice() {
        if (binding == null) return
        if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val selectedSpinnerItem =
            binding!!.spinnerBluetoothTarget.getSelectedItem() as? SpinnerItemDevice? ?: return
        val device = selectedSpinnerItem.bluetoothDevice
        Log.d(TAG, String.format("Selected device: %s", device.getName()))

        //disconnect if anything connected
        val proxy = bluetoothController!!.bluetoothHidDevice ?: return
        //https://github.com/stud0709/OneMoreSecret/issues/11


        proxy.getConnectedDevices().stream()
            .filter { d: BluetoothDevice? -> d!!.getAddress() != device.getAddress() }
            .forEach { device: BluetoothDevice? -> proxy.disconnect(device) }

        if (bluetoothController!!.bluetoothHidDevice
                .getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED
        ) {
            Log.d(
                TAG, String.format(
                    "Trying to connect %s: %s",
                    device.getName(),
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
            val bluetoothDevice = (binding!!
                .spinnerBluetoothTarget
                .getSelectedItem() as SpinnerItemDevice).bluetoothDevice
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
                        Thread.sleep(if (binding!!.swDelayedStrokes.isChecked) this.keyStrokeDelayOn else this.keyStrokeDelayOff)
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

        typing.set(false) //cancel typing, if any

        if (bluetoothBroadcastReceiver != null) requireContext().unregisterReceiver(
            bluetoothBroadcastReceiver
        )

        if (bluetoothController != null) bluetoothController!!.destroy()

        binding!!.btnType.setOnClickListener(null)
        requireActivity().removeMenuProvider(menuProvider)
        copyValue = null
        binding = null
    }

    protected fun refreshBluetoothControls() {
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

                            binding!!.spinnerKeyboardLayout.setEnabled(false)
                            binding!!.spinnerBluetoothTarget.setEnabled(false)
                            binding!!.btnType.setEnabled(false)
                            binding!!.imgButtonDiscoverable.setEnabled(false)
                            binding!!.swDelayedStrokes.setEnabled(false)
                            binding!!.textTyping.text = shareTitle
                        } catch (_: IllegalStateException) {
                            //things are happening outside the context
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
                    bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE

                val connectedDevices =
                    if (bluetoothController!!.bluetoothHidDevice == null) mutableListOf<BluetoothDevice?>() else bluetoothController!!.bluetoothHidDevice
                        .getConnectedDevices()

                val spinnerItems = bluetoothAdapter.getBondedDevices()
                    .stream()
                    .filter { d: BluetoothDevice? ->
                        d!!.getBluetoothClass()
                            .majorDeviceClass == BluetoothClass.Device.Major.COMPUTER
                    }
                    .map { bluetoothDevice ->
                        SpinnerItemDevice(
                            bluetoothDevice
                        )
                    }
                    .sorted { s1: SpinnerItemDevice?, s2: SpinnerItemDevice? ->
                        val i1 = if (connectedDevices.contains(
                                s1!!.bluetoothDevice
                            )
                        ) 0 else 1
                        val i2 = if (connectedDevices.contains(s2!!.bluetoothDevice)) 0 else 1
                        if (i1 != i2) return@sorted i1.compareTo(i2)
                        s1.toString().compareTo(s2.toString())
                    }
                    .collect(Collectors.toList())

                requireContext().mainExecutor.execute(Runnable {
                    if (binding == null) return@Runnable  //already being destroyed


                    refreshingBtControls.set(true)
                    try {
                        val isTyping = typing.get()

                        binding!!.imgButtonDiscoverable.setEnabled(
                            bluetoothAdapterEnabled
                                    && !discoverable && !isTyping
                        )

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

                        binding!!.spinnerBluetoothTarget.setEnabled(bluetoothAdapterEnabled && !isTyping)

                        //remember selection
                        run {
                            val selectedBluetoothTarget =
                                binding!!.spinnerBluetoothTarget.getSelectedItem() as SpinnerItemDevice?
                            val selectedBtAddress =
                                if (selectedBluetoothTarget == null) preferences!!.getString(
                                    PROP_LAST_SELECTED_BT_TARGET, null
                                ) else selectedBluetoothTarget.bluetoothDevice.getAddress()

                            //refreshing the list
                            arrayAdapterDevice!!.clear()
                            arrayAdapterDevice!!.addAll(spinnerItems)

                            //restore selection
                            if (selectedBtAddress != null) {
                                for (i in 0..<arrayAdapterDevice!!.count) {
                                    if (arrayAdapterDevice!!.getItem(i)?.bluetoothDevice?.address == selectedBtAddress) {
                                        binding!!.spinnerBluetoothTarget.setSelection(i)
                                        break
                                    }
                                }
                            }
                        }

                        //set BT connection state
                        val selectedBluetoothTarget =
                            binding!!.spinnerBluetoothTarget.getSelectedItem() as SpinnerItemDevice?
                        var selectedDeviceConnected = false

                        if (selectedBluetoothTarget != null) {
                            val selectedBtAddress =
                                selectedBluetoothTarget.bluetoothDevice.getAddress()
                            selectedDeviceConnected = connectedDevices.stream()
                                .anyMatch { d: BluetoothDevice? -> d!!.getAddress() == selectedBtAddress }
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
                        binding!!.swDelayedStrokes.setEnabled(selectedDeviceConnected && !isTyping)
                        binding!!.spinnerKeyboardLayout.setEnabled(!isTyping)

                        //set TYPE button state
                        val selectedLayout =
                            binding!!.spinnerKeyboardLayout.getSelectedItem() as KeyboardLayout?
                        binding!!.btnType.setEnabled(selectedDeviceConnected && selectedLayout != null && message != null)

                        binding!!.btnType.text = if (isTyping) getString(R.string.cancel) else getString(
                            R.string.type
                        )

                        binding!!.textTyping.text = if (isTyping) getText(R.string.typing_please_wait) else shareTitle
                    } catch (_: IllegalStateException) {
                        //things are happening outside the context
                        Log.e(
                            TAG,
                            String.format("%s not attached to a context", this@OutputFragment)
                        )
                    } finally {
                        refreshingBtControls.set(false)
                    }
                })
            } catch (_: IllegalStateException) {
                //things are happening outside the context
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
                return bluetoothDevice.getAlias() + " (" + bluetoothDevice.getAddress() + ")"
            } catch (_: IllegalStateException) {
                //things are happening outside the context
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
                //things are happening outside the context
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
                copyValue!!()
            } else if (menuItem.itemId == R.id.menuItemShare) {
                if (beforePause != null) beforePause.run()

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
        // Inflate the custom layout
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.layout_keboard_test, null)

        // Initialize the ListView and Button
        val listView = dialogView.findViewById<ListView>(R.id.listViewUsbUsage)

        val items: EnumEntries<KeyboardUsage> = KeyboardUsage.entries

        // Set up the ListView with an ArrayAdapter
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            items
        )

        listView.setAdapter(adapter)

        // Set up the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        // Set item click listener for the ListView
        listView.onItemClickListener =
            OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
                val selectedItem = items[position]
                Toast.makeText(requireContext(), selectedItem.toString(), Toast.LENGTH_SHORT).show()

                if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return@OnItemClickListener
                }

                val rArr: Array<KeyboardReport> = arrayOf(
                    KeyboardReport(selectedItem),  //usage without any modifiers
                    KeyboardReport(KeyboardUsage.KBD_NONE),  //release
                    KeyboardReport(KeyboardUsage.KBD_SPACE),  //trigger things like ¨ or ^
                    KeyboardReport(KeyboardUsage.KBD_NONE)
                )

                val bluetoothDevice = (binding!!
                    .spinnerBluetoothTarget
                    .getSelectedItem() as SpinnerItemDevice).bluetoothDevice
                Arrays.stream(rArr).forEach { r: KeyboardReport? ->
                    try {
                        bluetoothController!!
                            .bluetoothHidDevice
                            .sendReport(
                                bluetoothDevice,
                                0,
                                r!!.report
                            )
                        //Log.d(TAG, "sent: $r")
                    } catch (ex: Exception) {
                        printStackTrace(ex)
                        typing.set(false)
                    }
                }
            }

        //log the current layout
        val selectedLayout = binding!!.spinnerKeyboardLayout.getSelectedItem() as KeyboardLayout
        selectedLayout.logLayout()

        // Show the dialog
        dialog.show()
    }

    companion object {
        private val TAG: String = OutputFragment::class.java.getSimpleName()
        private const val PROP_BT_DISCOVERABLE_DURATION = "bt_discoverable_duration_s"
        private const val PROP_KEY_STROKE_DELAY_ON = "kbd_stroke_delay_on"
        private const val PROP_KEY_STROKE_DELAY_OFF = "kbd_stroke_delay_off"
        private const val PROP_LAST_SELECTED_KEYBOARD_LAYOUT = "last_selected_kbd_layout"
        private const val PROP_LAST_SELECTED_BT_TARGET = "last_selected_bt_target"
        private const val DEF_DISCOVERABLE_DURATION_S = 60
        private const val DEF_KEY_STROKE_DELAY_ON: Long = 50
        private const val DEF_KEY_STROKE_DELAY_OFF: Long = 10
    }
}