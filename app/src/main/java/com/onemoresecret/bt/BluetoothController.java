package com.onemoresecret.bt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.app.ActivityCompat;

import java.util.List;

//https://developer.android.com/guide/topics/connectivity/bluetooth/setup

public class BluetoothController extends BluetoothHidDevice.Callback implements BluetoothProfile.ServiceListener {
    private static final String TAG = BluetoothController.class.getSimpleName();

    private final BluetoothHidDeviceAppSdpSettings sdpRecord = new BluetoothHidDeviceAppSdpSettings(
            "OneMoreSecret",
            "OneMoreSecret HID Device",
            "OneMoreSecret",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            REPORT_MAP_KEYBOARD
    );

    private final Context ctx;
    private BluetoothHidDevice btHid;
    private BluetoothManager bluetoothManager;

    public BluetoothController(Context ctx) {
        this.ctx = ctx;
        this.bluetoothManager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        boolean b = bluetoothManager.getAdapter().getProfileProxy(ctx, this, BluetoothProfile.HID_DEVICE);
        Log.i(TAG, "getProfileProxy: " + b);
    }

    public void requestDiscoverable(ActivityResultCaller activityResultCaller, int duration_s) {
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration_s);
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        activityResultCaller.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            // don't care if discovery was started or not
        }).launch(discoverableIntent);
    }

    public BluetoothHidDevice getBtHid() {
        return btHid;
    }

//////////////////////////////////////
// BluetoothProfile.ServiceListener //
//////////////////////////////////////

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        Log.i(TAG, "Service connected (" + profile + ")");
        if (profile != BluetoothProfile.HID_DEVICE) {
            Log.wtf(TAG, "profile: " + profile + " is not HID_DEVICE");
            return;
        }

        this.btHid = (BluetoothHidDevice) proxy;

        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        btHid.registerApp(sdpRecord, null, null, ctx.getMainExecutor(), this);
    }

    @Override
    public void onServiceDisconnected(int profile) {
        Log.i(TAG, "Service disconnected: " + profile);
        if (profile == BluetoothProfile.HID_DEVICE)
            btHid = null;
    }

/////////////////////////////////
// BluetoothHidDevice.Callback //
/////////////////////////////////


    @Override
    public void onConnectionStateChanged(BluetoothDevice device, int state) {
        super.onConnectionStateChanged(device, state);
    }

    @Override
    public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
        super.onSetReport(device, type, id, data);
        Log.i(TAG, "onSetReport - device: " + device.toString() + ", type: " + type + ", id: " + id + ", data: " + byteArrayToHex(data));
    }

    @Override
    public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
        super.onGetReport(device, type, id, bufferSize);
        Log.i(TAG, "onGetReport - device: " + device + ", type: " + type + " id: " + id + ", bufferSize: " + bufferSize);
/*
        if (type == BluetoothHidDevice.REPORT_TYPE_FEATURE) {
            featureReport.wheelResolutionMultiplier = true
            featureReport.acPanResolutionMultiplier = true
            Log.i("getbthid","$btHid")

            var wasrs=btHid?.replyReport(device, type, FeatureReport.ID, featureReport.bytes)
            Log.i("replysuccess flag ",wasrs.toString())
        }
*/
    }

    @Override
    public void onAppStatusChanged(BluetoothDevice device, boolean registered) {
        super.onAppStatusChanged(device, registered);
        Log.i(TAG, "onAppStatusChanged -  device: " + device + ", registered: " + registered);

        if (registered) {
            if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            List<BluetoothDevice> pairedDevices = btHid.getDevicesMatchingConnectionStates(
                    new int[]{BluetoothProfile.STATE_CONNECTING,
                            BluetoothProfile.STATE_CONNECTED,
                            BluetoothProfile.STATE_DISCONNECTED,
                            BluetoothProfile.STATE_DISCONNECTING});
            Log.d(TAG, "paired devices: " + pairedDevices);

            //TODO: test
            if (btHid.getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED)
                btHid.connect(device);
        }
    }

    @Override
    public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {
        super.onInterruptData(device, reportId, data);

        Log.d(TAG, "data: " + byteArrayToHex(data));
        //TODO: LED status has changed
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b)).append(" ");
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
