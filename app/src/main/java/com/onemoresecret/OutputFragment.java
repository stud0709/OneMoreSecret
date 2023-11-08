package com.onemoresecret;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuProvider;

import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.bt.layout.KeyboardLayout;
import com.onemoresecret.bt.layout.Stroke;
import com.onemoresecret.databinding.FragmentOutputBinding;
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class OutputFragment extends FragmentWithNotificationBeforePause {
    private static final String TAG = OutputFragment.class.getSimpleName();
    private static final String PROP_BT_DISCOVERABLE_DURATION = "bt_discoverable_duration_s",
            PROP_KEY_STROKE_DELAY_ON = "kbd_stroke_delay_on",
            PROP_KEY_STROKE_DELAY_OFF = "kbd_stroke_delay_off",
            PROP_LAST_SELECTED_KEYBOARD_LAYOUT = "last_selected_kbd_layout",
            PROP_LAST_SELECTED_BT_TARGET = "last_selected_bt_target";
    private BluetoothController bluetoothController;
    private ArrayAdapter<SpinnerItemDevice> arrayAdapterDevice;
    private final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADVERTISE
    };

    private FragmentOutputBinding binding;
    private BluetoothBroadcastReceiver bluetoothBroadcastReceiver;

    private static final int DEF_DISCOVERABLE_DURATION_S = 60;
    private SharedPreferences preferences;
    private final AtomicBoolean refreshingBtControls = new AtomicBoolean();
    private static final long DEF_KEY_STROKE_DELAY_ON = 50, DEF_KEY_STROKE_DELAY_OFF = 10;
    private ClipboardManager clipboardManager;
    private final OutputMenuProvider menuProvider = new OutputMenuProvider();
    private Runnable copyValue = null;
    private String message = null;
    private String shareTitle = null;


    private final AtomicBoolean typing = new AtomicBoolean(false);

    public void setMessage(@Nullable String message, @Nullable String shareTitle) {
        this.message = message;
        this.shareTitle = shareTitle;
        refreshBluetoothControls();
        requireActivity().invalidateOptionsMenu();
    }

    /**
     * A fragment is paused when the confirmation dialog is raised ("send to" or "BT discovery").
     * This is to notify the parent, that this is about to happen.
     */
    @Override
    public void setBeforePause(Runnable r) {
        this.beforePause = r;
    }

    public KeyboardLayout getSelectedLayout() {
        return (KeyboardLayout) binding.spinnerKeyboardLayout.getSelectedItem();
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentOutputBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        requireActivity().addMenuProvider(menuProvider);

        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        bluetoothController = new BluetoothController(this,
                result -> {
                },
                new BluetoothHidDeviceCallback()
        );

        clipboardManager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);

        if (PermissionsFragment.isAllPermissionsGranted(TAG, requireContext(), REQUIRED_PERMISSIONS)) {
            onAllPermissionsGranted();
        } else {
            refreshBluetoothControls();
        }

        initSpinnerTargetDevice();

        initSpinnerKeyboardLayout();

        binding.btnType.setOnClickListener(v -> {
            if (typing.get()) {
                //cancel typing
                typing.set(false);
                return;
            }

            var selectedLayout = (KeyboardLayout) binding.spinnerKeyboardLayout.getSelectedItem();
            var strokes = selectedLayout.forString(message);

            if (strokes.contains(null)) {
                //not all characters could be converted into key strokes
                requireContext().getMainExecutor().execute(() -> Toast.makeText(getContext(), getString(R.string.wrong_keyboard_layout), Toast.LENGTH_LONG).show());
                return;
            }

            type(strokes);
        });

        binding.textTyping.setVisibility(View.INVISIBLE);

        copyValue = () -> {
            var extra_is_sensitive = "android.content.extra.IS_SENSITIVE"; /* replace with  ClipDescription.EXTRA_IS_SENSITIVE for API Level 33+ */
            var clipData = ClipData.newPlainText("oneMoreSecret", message);
            var persistableBundle = new PersistableBundle();
            persistableBundle.putBoolean(extra_is_sensitive, true);
            clipData.getDescription().setExtras(persistableBundle);
            clipboardManager.setPrimaryClip(clipData);
        };
    }

    private void initSpinnerTargetDevice() {
        arrayAdapterDevice = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item);
        arrayAdapterDevice.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerBluetoothTarget.setAdapter(arrayAdapterDevice);
        binding.spinnerBluetoothTarget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (refreshingBtControls.get()) return;
                var selectedItem = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();
                preferences.edit().putString(PROP_LAST_SELECTED_BT_TARGET, selectedItem.getBluetoothDevice().getAddress()).apply();

                checkConnectSelectedDevice();
                refreshBluetoothControls();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (refreshingBtControls.get()) return;
                refreshBluetoothControls();
            }
        });
    }

    private void initSpinnerKeyboardLayout() {
        var keyboardLayoutAdapter = new ArrayAdapter<KeyboardLayout>(requireContext(), android.R.layout.simple_spinner_item);
        keyboardLayoutAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Arrays.stream(KeyboardLayout.knownSubclasses)
                .map(clazz -> {
                    try {
                        return (KeyboardLayout) clazz.newInstance();
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(Objects::nonNull) /* just in case */
                .sorted(Comparator.comparing(Object::toString))
                .forEach(keyboardLayoutAdapter::add);

        binding.spinnerKeyboardLayout.setAdapter(keyboardLayoutAdapter);

        //select last used keyboard layout
        var lastSelectedKeyboardLayout = preferences.getString(PROP_LAST_SELECTED_KEYBOARD_LAYOUT, null);
        for (var i = 0; i < keyboardLayoutAdapter.getCount(); i++) {
            if (keyboardLayoutAdapter.getItem(i).getClass().getName().equals(lastSelectedKeyboardLayout)) {
                binding.spinnerKeyboardLayout.setSelection(i);
                break;
            }
        }

        binding.spinnerKeyboardLayout.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (refreshingBtControls.get()) return;

                var selectedLayout = (KeyboardLayout) binding.spinnerKeyboardLayout.getSelectedItem();
                preferences.edit().putString(PROP_LAST_SELECTED_KEYBOARD_LAYOUT, selectedLayout.getClass().getName()).apply();

                refreshBluetoothControls();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (refreshingBtControls.get()) return;
                refreshBluetoothControls();
            }
        });
    }

    private void onAllPermissionsGranted() {
        //register broadcast receiver for BT events
        bluetoothBroadcastReceiver = new BluetoothBroadcastReceiver();

        var ctx = requireContext();

        ctx.registerReceiver(
                bluetoothBroadcastReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));

        ctx.registerReceiver(
                bluetoothBroadcastReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        ctx.registerReceiver(
                bluetoothBroadcastReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED));

        //initialize "request discoverable" button
        binding.imgButtonDiscoverable.setOnClickListener(
                e -> {
                    if (beforePause != null) beforePause.run();

                    bluetoothController.requestDiscoverable(getDiscoverableDuration());
                });

        refreshBluetoothControls();

    }

    /**
     * Check if the selected device is connected, try to connect.
     */
    private void checkConnectSelectedDevice() {
        if (binding == null) return;
        if (requireContext().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        var selectedSpinnerItem = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();
        if (selectedSpinnerItem == null) return;
        var device = selectedSpinnerItem.getBluetoothDevice();
        Log.d(TAG, String.format("Selected device: %s", device.getName()));

        //disconnect if anything connected
        var proxy = bluetoothController.getBluetoothHidDevice();
        if (proxy == null) return; //https://github.com/stud0709/OneMoreSecret/issues/11

        proxy.getConnectedDevices().stream().filter(d -> !d.getAddress().equals(device.getAddress())).forEach(d -> proxy.disconnect(d));

        if (bluetoothController.getBluetoothHidDevice().getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED) {
            Log.d(TAG, String.format("Trying to connect %s: %s",
                    device.getName(),
                    bluetoothController.getBluetoothHidDevice().connect(device)));
        }
    }

    protected void type(List<Stroke> list) {
        if (requireContext().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Log.d(TAG, "sending message (size: " + list.size() + ")");

        typing.set(true);
        refreshBluetoothControls();

        new Thread(() -> {
            var bluetoothDevice = ((SpinnerItemDevice) binding
                    .spinnerBluetoothTarget
                    .getSelectedItem())
                    .getBluetoothDevice();

            list.stream()
                    .filter(s -> typing.get())
                    .flatMap(s -> s.get().stream())
                    .forEach(r -> {
                        bluetoothController
                                .getBluetoothHidDevice()
                                .sendReport(
                                        bluetoothDevice,
                                        0,
                                        r.report);

                        try {
                            Thread.sleep(binding.swDelayedStrokes.isChecked() ? getKeyStrokeDelayOn() : getKeyStrokeDelayOff());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    });

            typing.set(false);
            refreshBluetoothControls();
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        typing.set(false); //cancel typing, if any

        if (bluetoothBroadcastReceiver != null)
            requireContext().unregisterReceiver(bluetoothBroadcastReceiver);

        if (bluetoothController != null) bluetoothController.destroy();

        binding.btnType.setOnClickListener(null);
        requireActivity().removeMenuProvider(menuProvider);
        copyValue = null;
        binding = null;
    }

    protected void refreshBluetoothControls() {
        if (binding == null) return;
        if (refreshingBtControls.get()) return; //called in loop

        new Thread(() -> {
            if (binding == null) return; //already being destroyed

            try {
                if (!bluetoothController.isBluetoothAvailable() ||
                        !PermissionsFragment.isAllPermissionsGranted(TAG, requireContext(), REQUIRED_PERMISSIONS)) {

                    //disable all bluetooth functionality
                    requireContext().getMainExecutor().execute(() -> {
                        refreshingBtControls.set(true);

                        try {
                            var drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_bluetooth_disabled_24, getContext().getTheme());
                            binding.chipBtStatus.setChipIcon(drawable);
                            binding.chipBtStatus.setText(getString(R.string.bt_not_available));

                            binding.spinnerKeyboardLayout.setEnabled(false);
                            binding.spinnerBluetoothTarget.setEnabled(false);
                            binding.btnType.setEnabled(false);
                            binding.imgButtonDiscoverable.setEnabled(false);
                            binding.swDelayedStrokes.setEnabled(false);
                            binding.textTyping.setVisibility(View.INVISIBLE);
                        } catch (IllegalStateException ex) {
                            //things are happening outside the context
                            Log.e(TAG, String.format("%s not attached to a context", OutputFragment.this));
                        } finally {
                            refreshingBtControls.set(false);
                        }
                    });
                    return;
                }

                var bluetoothAdapter = bluetoothController.getAdapter();

                var bluetoothAdapterEnabled = bluetoothAdapter.isEnabled();

                if (requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                var discoverable = bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;

                var connectedDevices = bluetoothController.getBluetoothHidDevice() == null ?
                        Collections.<BluetoothDevice>emptyList() :
                        bluetoothController.getBluetoothHidDevice().getConnectedDevices();

                var spinnerItems = bluetoothAdapter.getBondedDevices()
                        .stream()
                        .filter(d -> d.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER)
                        .map(SpinnerItemDevice::new)
                        .sorted((s1, s2) -> {
                            var i1 = connectedDevices.contains(s1.getBluetoothDevice()) ? 0 : 1;
                            var i2 = connectedDevices.contains(s2.getBluetoothDevice()) ? 0 : 1;
                            if (i1 != i2) return Integer.compare(i1, i2);

                            return s1.toString().compareTo(s2.toString());
                        })
                        .collect(Collectors.toList());

                requireContext().getMainExecutor().execute(() -> {
                    if (binding == null) return; //already being destroyed

                    refreshingBtControls.set(true);

                    try {
                        binding.imgButtonDiscoverable.setEnabled(bluetoothAdapterEnabled && !discoverable);

                        var status = getString(R.string.bt_off);
                        var drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_bluetooth_disabled_24, getContext().getTheme());

                        if (bluetoothAdapterEnabled) {
                            status = getString(R.string.bt_disconnected);
                            drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_bluetooth_24, getContext().getTheme());
                        }

                        binding.spinnerBluetoothTarget.setEnabled(bluetoothAdapterEnabled);

                        //remember selection
                        {
                            var selectedBluetoothTarget = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();
                            var selectedBtAddress = selectedBluetoothTarget == null ?
                                    preferences.getString(PROP_LAST_SELECTED_BT_TARGET, null) :
                                    selectedBluetoothTarget.getBluetoothDevice().getAddress();

                            //refreshing the list
                            arrayAdapterDevice.clear();
                            arrayAdapterDevice.addAll(spinnerItems);

                            //restore selection
                            if (selectedBtAddress != null) {
                                for (var i = 0; i < arrayAdapterDevice.getCount(); i++) {
                                    if (arrayAdapterDevice.getItem(i).getBluetoothDevice().getAddress().equals(selectedBtAddress)) {
                                        binding.spinnerBluetoothTarget.setSelection(i);
                                        break;
                                    }
                                }
                            }
                        }

                        //set BT connection state
                        var selectedBluetoothTarget = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();
                        boolean selectedDeviceConnected = false;

                        if (selectedBluetoothTarget != null) {
                            var selectedBtAddress = selectedBluetoothTarget.getBluetoothDevice().getAddress();
                            selectedDeviceConnected = connectedDevices.stream().anyMatch(d -> d.getAddress().equals(selectedBtAddress));
                            if (selectedDeviceConnected) {
                                status = getString(R.string.bt_connected);
                                drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_bluetooth_connected_24, getContext().getTheme());
                            }
                        }

                        binding.chipBtStatus.setChipIcon(drawable);
                        binding.chipBtStatus.setText(status);
                        binding.swDelayedStrokes.setEnabled(selectedDeviceConnected);

                        //set TYPE button state
                        var selectedLayout = (KeyboardLayout) binding.spinnerKeyboardLayout.getSelectedItem();
                        binding.btnType.setEnabled(selectedDeviceConnected &&
                                selectedLayout != null &&
                                message != null);

                        binding.btnType.setText(typing.get() ? getString(R.string.cancel) : getString(R.string.type));

                        binding.textTyping.setVisibility(typing.get() ? View.VISIBLE : View.INVISIBLE);
                    } catch (IllegalStateException ex) {
                        //things are happening outside the context
                        Log.e(TAG, String.format("%s not attached to a context", OutputFragment.this));
                    } finally {
                        refreshingBtControls.set(false);
                    }
                });
            } catch (IllegalStateException ex) {
                //things are happening outside the context
                Log.e(TAG, String.format("%s not attached to a context", OutputFragment.this));
            }
        }).start();
    }

    class BluetoothBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            Log.d(TAG, String.format("Got intent %s, state %s", intent.getAction(), intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)));

            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (newState) {
                    case BluetoothAdapter.STATE_ON:
                        Log.d(TAG, "Bluetooth is now on");
                        break;
                }
            }

            refreshBluetoothControls();
        }
    }

    public class SpinnerItemDevice {
        private final BluetoothDevice bluetoothDevice;

        SpinnerItemDevice(BluetoothDevice bluetoothDevice) {
            this.bluetoothDevice = bluetoothDevice;
        }

        public BluetoothDevice getBluetoothDevice() {
            return bluetoothDevice;
        }

        @NonNull
        @Override
        public String toString() {
            try {
                if (requireContext().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    throw new RuntimeException(getString(R.string.insufficient_permissions));
                }
                return bluetoothDevice.getAlias() + " (" + bluetoothDevice.getAddress() + ")";
            } catch (IllegalStateException ex) {
                //things are happening outside the context
                Log.e(TAG, String.format("%s not attached to a context", OutputFragment.this));
            }

            return super.toString();
        }
    }

    public class BluetoothHidDeviceCallback extends BluetoothHidDevice.Callback {

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            super.onConnectionStateChanged(device, state);
            Log.i(TAG, "onConnectionStateChanged -  device: " + device + ", state: " + state);

            refreshBluetoothControls();
        }

        @Override
        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            super.onSetReport(device, type, id, data);
            Log.i(TAG, "onSetReport - device: " + device.toString() + ", type: " + type + ", id: " + id + ", data: " + Util.byteArrayToHex(data));
        }

        @Override
        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            super.onGetReport(device, type, id, bufferSize);
            Log.i(TAG, "onGetReport - device: " + device + ", type: " + type + " id: " + id + ", bufferSize: " + bufferSize);
        }

        @Override
        public void onAppStatusChanged(BluetoothDevice device, boolean registered) {
            super.onAppStatusChanged(device, registered);

            try {
                Log.i(TAG, "onAppStatusChanged -  device: " + device + ", registered: " + registered);

                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                var compatibleDevices = bluetoothController.getBluetoothHidDevice().getDevicesMatchingConnectionStates(
                        new int[]{BluetoothProfile.STATE_CONNECTING,
                                BluetoothProfile.STATE_CONNECTED,
                                BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_DISCONNECTING});
                Log.d(TAG, "compatible devices: " + compatibleDevices);

                checkConnectSelectedDevice();
                refreshBluetoothControls();
            } catch (IllegalStateException ex) {
                //things are happening outside the context
                Log.e(TAG, String.format("%s not attached to a context", OutputFragment.this));
            }
        }

        @Override
        public void onInterruptData(BluetoothDevice device, byte reportId, byte[] data) {
            super.onInterruptData(device, reportId, data);
            Log.d(TAG, "onInterruptData - -  device: " + device + ", reportId: " + reportId + ", data: " + Util.byteArrayToHex(data));

//            boolean numLockActive = (data[0] & KeyboardReport.NUM_LOCK) == KeyboardReport.NUM_LOCK;
//            boolean capsLockActive = (data[0] & KeyboardReport.CAPS_LOCK) == KeyboardReport.CAPS_LOCK;
        }
    }

    public class OutputMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_output, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            menu.setGroupVisible(R.id.menuGroupOutputAll, message != null);
            MenuProvider.super.onPrepareMenu(menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (!isAdded() || getContext() == null)
                return true;//https://github.com/stud0709/OneMoreSecret/issues/10

            if (menuItem.getItemId() == R.id.menuItemOutputCopy) {
                copyValue.run();
            } else if (menuItem.getItemId() == R.id.menuItemShare) {
                if (beforePause != null) beforePause.run();

                var sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, message);

                sendIntent.putExtra(Intent.EXTRA_TITLE, shareTitle);
                sendIntent.setType("text/plain");

                var shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
            } else if (menuItem.getItemId() == R.id.menuItemOutputHelp) {
                Util.openUrl(R.string.autotype_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }

    }

    private int getDiscoverableDuration() {
        return preferences.getInt(PROP_BT_DISCOVERABLE_DURATION, DEF_DISCOVERABLE_DURATION_S);
    }

    private long getKeyStrokeDelayOn() {
        return preferences.getLong(PROP_KEY_STROKE_DELAY_ON, DEF_KEY_STROKE_DELAY_ON);
    }

    private long getKeyStrokeDelayOff() {
        return preferences.getLong(PROP_KEY_STROKE_DELAY_OFF, DEF_KEY_STROKE_DELAY_OFF);
    }
}