package com.onemoresecret.bt

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment

class BluetoothController(
    private val fragment: Fragment,
    onActivityResult: ActivityResultCallback<ActivityResult>,
    private val callback: BluetoothHidDevice.Callback
) : BluetoothProfile.ServiceListener {

    private val sdpRecord = BluetoothHidDeviceAppSdpSettings(
        "OneMoreSecret",
        "OneMoreSecret HID Device",
        "OneMoreSecret",
        BluetoothHidDevice.SUBCLASS1_KEYBOARD,
        REPORT_MAP_KEYBOARD
    )

    private val bluetoothManager: BluetoothManager? =
        fragment.requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?

    private val activityResultLauncher: ActivityResultLauncher<Intent>?

    var bluetoothHidDevice: BluetoothHidDevice? = null
        private set

    init {
        if (bluetoothManager == null) {
            Log.i(TAG, "No bluetooth Manager found")
            activityResultLauncher = null
        } else {
            val b = adapter?.getProfileProxy(fragment.context, this, BluetoothProfile.HID_DEVICE)
            Log.i(TAG, "getProfileProxy: $b")

            //prepare intent "request discoverable"
            activityResultLauncher = fragment.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
                onActivityResult
            )
        }
    }

    val isBluetoothAvailable: Boolean
        get() = bluetoothManager != null

    fun requestDiscoverable(discoverableDurationS: Int) {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverableDurationS)
        }
        activityResultLauncher?.launch(discoverableIntent)
    }

    val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    fun destroy() {
        try {
            if (ActivityCompat.checkSelfPermission(
                    fragment.requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            if (bluetoothManager != null && bluetoothHidDevice != null) {
                bluetoothHidDevice?.connectedDevices?.forEach { d ->
                    bluetoothHidDevice?.disconnect(d)
                }
                bluetoothHidDevice?.unregisterApp()
                adapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, bluetoothHidDevice)
            }
        } catch (_: IllegalStateException) {
            //things are happening outside the context
            Log.e(TAG, "$fragment not attached to a context")
        }
    }

    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
        Log.i(TAG, "Service connected")
        if (profile != BluetoothProfile.HID_DEVICE) {
            Log.wtf(TAG, "profile: $profile is not HID_DEVICE")
            return
        }

        try {
            bluetoothHidDevice = proxy as BluetoothHidDevice
            if (ActivityCompat.checkSelfPermission(
                    fragment.requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val hidDevice = bluetoothHidDevice ?: return

            Log.i(
                TAG, String.format(
                    "registering HID app: %s",
                    hidDevice.registerApp(
                        sdpRecord,
                        null,
                        null,
                        fragment.requireContext().mainExecutor,
                        callback
                    )
                )
            )
        } catch (_: IllegalStateException) {
            //things are happening outside the context
            Log.e(TAG, "$fragment not attached to a context")
        }
    }

    override fun onServiceDisconnected(profile: Int) {
        Log.i(TAG, "Service disconnected: $profile")
        bluetoothHidDevice = null
    }

    companion object {
        private val TAG = BluetoothController::class.java.simpleName

        private val REPORT_MAP_KEYBOARD = byteArrayOf(
            0x05.toByte(), 0x01.toByte(),                         // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(),                         // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(),                         // Collection (Application)
            0x05.toByte(), 0x07.toByte(),                         //     Usage Page (Key Codes)
            0x19.toByte(), 0xe0.toByte(),                         //     Usage Minimum (224)
            0x29.toByte(), 0xe7.toByte(),                         //     Usage Maximum (231)
            0x15.toByte(), 0x00.toByte(),                         //     Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(),                         //     Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(),                         //     Report Size (1)
            0x95.toByte(), 0x08.toByte(),                         //     Report Count (8)
            0x81.toByte(), 0x02.toByte(),                         //     Input (Data, Variable, Absolute)
            0x95.toByte(), 0x01.toByte(),                         //     Report Count (1)
            0x75.toByte(), 0x08.toByte(),                         //     Report Size (8)
            0x81.toByte(), 0x01.toByte(),                         //     Input (Constant) reserved byte(1)
            0x95.toByte(), 0x05.toByte(),                         //     Report Count (5)
            0x75.toByte(), 0x01.toByte(),                         //     Report Size (1)
            0x05.toByte(), 0x08.toByte(),                         //     Usage Page (Page# for LEDs)
            0x19.toByte(), 0x01.toByte(),                         //     Usage Minimum (1)
            0x29.toByte(), 0x05.toByte(),                         //     Usage Maximum (5)
            0x91.toByte(), 0x02.toByte(),                         //     Output (Data, Variable, Absolute), Led report
            0x95.toByte(), 0x01.toByte(),                         //     Report Count (1)
            0x75.toByte(), 0x03.toByte(),                         //     Report Size (3)
            0x91.toByte(), 0x01.toByte(),                         //     Output (Data, Variable, Absolute), Led report padding
            0x95.toByte(), 0x06.toByte(),                         //     Report Count (6)
            0x75.toByte(), 0x08.toByte(),                         //     Report Size (8)
            0x15.toByte(), 0x00.toByte(),                         //     Logical Minimum (0)
            0x25.toByte(), 0x65.toByte(),                         //     Logical Maximum (101)
            0x05.toByte(), 0x07.toByte(),                         //     Usage Page (Key codes)
            0x19.toByte(), 0x00.toByte(),                         //     Usage Minimum (0)
            0x29.toByte(), 0x65.toByte(),                         //     Usage Maximum (101)
            0x81.toByte(), 0x00.toByte(),                         //     Input (Data, Array) Key array(6 bytes)
            0xC0.toByte()                                         // End Collection (Application)
        )
    }
}
