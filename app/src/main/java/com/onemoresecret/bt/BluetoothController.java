package com.onemoresecret.bt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

//https://developer.android.com/guide/topics/connectivity/bluetooth/setup

public class BluetoothController implements BluetoothProfile.ServiceListener {
    private static final String TAG = BluetoothController.class.getSimpleName();

    private final BluetoothHidDeviceAppSdpSettings sdpRecord = new BluetoothHidDeviceAppSdpSettings(
            "OneMoreSecret",
            "OneMoreSecret HID Device",
            "OneMoreSecret",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            REPORT_MAP_KEYBOARD
    );

    private final Fragment fragment;
    private final BluetoothManager bluetoothManager;
    private final ActivityResultLauncher<Intent> activityResultLauncher;
    private final BluetoothHidDevice.Callback callback;
    private BluetoothHidDevice bluetoothHidDevice = null;

    public BluetoothController(Fragment fragment, ActivityResultCallback<ActivityResult> onActivityResult, BluetoothHidDevice.Callback callback) {
        this.fragment = fragment;
        this.callback = callback;
        this.bluetoothManager = (BluetoothManager) fragment.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        if (this.bluetoothManager == null) {
            Log.i(TAG, "No bluetooth Manager found");
            activityResultLauncher = null;
            return;
        }
        boolean b = this.getAdapter().getProfileProxy(fragment.getContext(), this, BluetoothProfile.HID_DEVICE);
        Log.i(TAG, "getProfileProxy: " + b);

        //prepare intent "request discoverable"
        activityResultLauncher = fragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), onActivityResult);
    }

    public boolean isBluetoothAvailable() {
        return bluetoothManager != null;
    }

    public BluetoothHidDevice getBluetoothHidDevice() {
        return bluetoothHidDevice;
    }

    public void requestDiscoverable(int discoverable_duration_s) {
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoverable_duration_s);
        activityResultLauncher.launch(discoverableIntent);
    }

    public BluetoothAdapter getAdapter() {
        return bluetoothManager.getAdapter();
    }

// BluetoothProfile.ServiceListener

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        Log.i(TAG, "Service connected (" + profile + ")");
        if (profile != BluetoothProfile.HID_DEVICE) {
            Log.wtf(TAG, "profile: " + profile + " is not HID_DEVICE");
            return;
        }

        if (ActivityCompat.checkSelfPermission(fragment.getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        bluetoothHidDevice = (BluetoothHidDevice) proxy;
        bluetoothHidDevice.registerApp(sdpRecord, null, null, fragment.getContext().getMainExecutor(), callback);
    }

    @Override
    public void onServiceDisconnected(int profile) {
        Log.i(TAG, "Service disconnected: " + profile);
        bluetoothHidDevice = null;
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (byte b : a)
            sb.append(String.format("%02x", b)).append(i++ % 2 == 1 ? " " : "");
        return sb.toString();
    }

    private static final byte[] REPORT_MAP_KEYBOARD = {

            (byte) 0x05, (byte) 0x01,                         // Usage Page (Generic Desktop)
            (byte) 0x09, (byte) 0x06,                         // Usage (Keyboard)
            (byte) 0xA1, (byte) 0x01,                         // Collection (Application)
            (byte) 0x05, (byte) 0x07,                         //     Usage Page (Key Codes)
            (byte) 0x19, (byte) 0xe0,                         //     Usage Minimum (224)
            (byte) 0x29, (byte) 0xe7,                         //     Usage Maximum (231)
            (byte) 0x15, (byte) 0x00,                         //     Logical Minimum (0)
            (byte) 0x25, (byte) 0x01,                         //     Logical Maximum (1)
            (byte) 0x75, (byte) 0x01,                         //     Report Size (1)
            (byte) 0x95, (byte) 0x08,                         //     Report Count (8)
            (byte) 0x81, (byte) 0x02,                         //     Input (Data, Variable, Absolute)

            (byte) 0x95, (byte) 0x05,                         //     Report Count (5)
            (byte) 0x75, (byte) 0x01,                         //     Report Size (1)
            (byte) 0x05, (byte) 0x08,                         //     Usage Page (Page# for LEDs)
            (byte) 0x19, (byte) 0x01,                         //     Usage Minimum (1)
            (byte) 0x29, (byte) 0x05,                         //     Usage Maximum (5)
            (byte) 0x91, (byte) 0x02,                         //     Output (Data, Variable, Absolute), Led report
            (byte) 0x95, (byte) 0x01,                         //     Report Count (1)
            (byte) 0x75, (byte) 0x03,                         //     Report Size (3)
            (byte) 0x91, (byte) 0x01,                         //     Output (Data, Variable, Absolute), Led report padding

            (byte) 0x95, (byte) 0x01,                         //     Report Count (1)
            (byte) 0x75, (byte) 0x08,                         //     Report Size (8)
            (byte) 0x15, (byte) 0x00,                         //     Logical Minimum (0)
            (byte) 0x25, (byte) 0x65,                         //     Logical Maximum (101)
            (byte) 0x05, (byte) 0x07,                         //     Usage Page (Key codes)
            (byte) 0x19, (byte) 0x00,                         //     Usage Minimum (0)
            (byte) 0x29, (byte) 0x65,                         //     Usage Maximum (101)
            (byte) 0x81, (byte) 0x00,                         //     Input (Data, Array) Key array(6 bytes)
            (byte) 0xC0                                       // End Collection (Application)
    };

}
