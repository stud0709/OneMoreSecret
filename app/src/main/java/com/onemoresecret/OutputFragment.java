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
import android.graphics.drawable.Drawable;
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
import androidx.fragment.app.Fragment;

import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.bt.layout.KeyboardLayout;
import com.onemoresecret.bt.layout.Stroke;
import com.onemoresecret.databinding.FragmentOutputBinding;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class OutputFragment extends Fragment {
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
    private final Map<String, List<Stroke>> keyboardQueue = new ConcurrentHashMap<>();

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

    private Runnable beforePause = null;

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
    public void setBeforePause(Runnable onBtDiscover) {
        this.beforePause = onBtDiscover;
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
            KeyboardLayout selectedLayout = (KeyboardLayout) binding.spinnerKeyboardLayout.getSelectedItem();
            List<Stroke> strokes = selectedLayout.forString(message);

            if (strokes.contains(null)) {
                //not all characters could be converted into key strokes
                requireContext().getMainExecutor().execute(() -> Toast.makeText(getContext(), getString(R.string.wrong_keyboard_layout), Toast.LENGTH_LONG).show());
                return;
            }

            type(strokes);
        });

        copyValue = () -> {
            String extra_is_sensitive = "android.content.extra.IS_SENSITIVE"; /* replace with  ClipDescription.EXTRA_IS_SENSITIVE for API Level 33+ */
            ClipData clipData = ClipData.newPlainText("oneMoreSecret", message);
            PersistableBundle persistableBundle = new PersistableBundle();
            persistableBundle.putBoolean(extra_is_sensitive, true);
            clipData.getDescription().setExtras(persistableBundle);
            clipboardManager.setPrimaryClip(clipData);
        };
    }

    private void initSpinnerTargetDevice() {
        arrayAdapterDevice = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
        arrayAdapterDevice.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerBluetoothTarget.setAdapter(arrayAdapterDevice);
        binding.spinnerBluetoothTarget.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (refreshingBtControls.get()) return;

                SpinnerItemDevice selectedItem = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();
                preferences.edit().putString(PROP_LAST_SELECTED_BT_TARGET, selectedItem.getBluetoothDevice().getAddress()).apply();

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
        ArrayAdapter<KeyboardLayout> keyboardLayoutAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
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
        String lastSelectedKeyboardLayout = preferences.getString(PROP_LAST_SELECTED_KEYBOARD_LAYOUT, null);
        for (int i = 0; i < keyboardLayoutAdapter.getCount(); i++) {
            if (keyboardLayoutAdapter.getItem(i).getClass().getName().equals(lastSelectedKeyboardLayout)) {
                binding.spinnerKeyboardLayout.setSelection(i);
                break;
            }
        }

        binding.spinnerKeyboardLayout.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (refreshingBtControls.get()) return;

                KeyboardLayout selectedLayout = (KeyboardLayout) binding.spinnerKeyboardLayout.getSelectedItem();
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

        Context ctx = requireContext();

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

    protected void type(List<Stroke> list) {
        SpinnerItemDevice selectedSpinnerItem = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();

        if (selectedSpinnerItem == null) {
            requireContext().getMainExecutor().execute(() -> Toast.makeText(getContext(), getString(R.string.select_bt_target), Toast.LENGTH_LONG));
            return;
        }

        if (requireContext().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (bluetoothController.getBluetoothHidDevice().getConnectedDevices()
                .stream().noneMatch(d -> d.getAddress()
                        .equals(selectedSpinnerItem.getBluetoothDevice().getAddress()))) {
            Log.d(TAG, "queueing message for " + selectedSpinnerItem.getBluetoothDevice().getAddress() + " (size: " + list.size() + ")");
            keyboardQueue.put(selectedSpinnerItem.getBluetoothDevice().getAddress(), list);

            if (!bluetoothController.registerApp()) //if app is not registered, connect will be executed upon register event
                //app is registered
                bluetoothController.getBluetoothHidDevice().connect(selectedSpinnerItem.getBluetoothDevice());

            return;
        }

        Log.d(TAG, "sending message (size: " + list.size() + ")");

        if (requireContext().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        typing.set(true);
        refreshBluetoothControls();

        new Thread(() -> {
            BluetoothDevice bluetoothDevice = ((SpinnerItemDevice) binding
                    .spinnerBluetoothTarget
                    .getSelectedItem())
                    .getBluetoothDevice();

            list.stream().flatMap(s -> s.get().stream()).forEach(r -> {
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
        if (bluetoothBroadcastReceiver != null)
            requireContext().unregisterReceiver(bluetoothBroadcastReceiver);
        if (requireContext().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        if (bluetoothController != null && bluetoothController.getBluetoothHidDevice() != null) {
            bluetoothController.getBluetoothHidDevice().getConnectedDevices().forEach(d -> bluetoothController.getBluetoothHidDevice().disconnect(d));
            bluetoothController.getBluetoothHidDevice().unregisterApp();
        }

        binding.btnType.setOnClickListener(null);
        requireActivity().removeMenuProvider(menuProvider);
        copyValue = null;
        binding = null;
    }

    protected void refreshBluetoothControls() {
        if (getContext() == null) return; //post mortem call
        if (binding == null) return;
        if (refreshingBtControls.get()) return; //called in loop

        new Thread(() -> {
            if (!bluetoothController.isBluetoothAvailable() ||
                    !PermissionsFragment.isAllPermissionsGranted(TAG, requireContext(), REQUIRED_PERMISSIONS)) {

                //disable all bluetooth functionality
                requireContext().getMainExecutor().execute(() -> {
                    Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_bluetooth_disabled_24, getContext().getTheme());
                    binding.chipBtStatus.setChipIcon(drawable);
                    binding.chipBtStatus.setText(getString(R.string.bt_not_available));

                    binding.spinnerKeyboardLayout.setEnabled(false);
                    binding.spinnerBluetoothTarget.setEnabled(false);
                    binding.btnType.setEnabled(false);
                    binding.imgButtonDiscoverable.setEnabled(false);
                    binding.swDelayedStrokes.setEnabled(false);
                });
                return;
            }

            BluetoothAdapter bluetoothAdapter = bluetoothController.getAdapter();

            boolean bluetoothAdapterEnabled = bluetoothAdapter.isEnabled();

            if (requireContext().checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            boolean discoverable = bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;

            List<BluetoothDevice> connectedDevices = bluetoothController.getBluetoothHidDevice() == null ?
                    Collections.emptyList() :
                    bluetoothController.getBluetoothHidDevice().getConnectedDevices();

            SpinnerItemDevice[] bondedDevices = bluetoothAdapter.getBondedDevices().stream().filter(
                            d -> d.getBluetoothClass()
                                    .getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER)
                    .map(SpinnerItemDevice::new)
                    .sorted((s1, s2) -> {
                        int i1 = connectedDevices.contains(s1.getBluetoothDevice()) ? 0 : 1;
                        int i2 = connectedDevices.contains(s2.getBluetoothDevice()) ? 0 : 1;
                        if (i1 != i2) return Integer.compare(i1, i2);

                        return s1.toString().compareTo(s2.toString());
                    })
                    .collect(Collectors.toList()).toArray(new SpinnerItemDevice[]{});

            requireContext().getMainExecutor().execute(() -> {
                Log.d(TAG, "Refreshing controls");
                refreshingBtControls.set(true);

                try {
                    binding.imgButtonDiscoverable.setEnabled(bluetoothAdapterEnabled && !discoverable);

                    String status = getString(R.string.bt_off);
                    Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_bluetooth_disabled_24, getContext().getTheme());

                    if (bluetoothAdapterEnabled) {
                        status = getString(R.string.bt_disconnected);
                        drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_bluetooth_24, getContext().getTheme());
                    }

                    if (discoverable) {
                        status = getString(R.string.bt_discoverable);
                        drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_bluetooth_discovering_24, getContext().getTheme());
                    }

                    binding.spinnerBluetoothTarget.setEnabled(bluetoothAdapterEnabled);

                    //remember selection
                    {
                        SpinnerItemDevice selectedBluetoothTarget = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();
                        String selectedBtAddress = selectedBluetoothTarget == null ?
                                preferences.getString(PROP_LAST_SELECTED_BT_TARGET, null) :
                                selectedBluetoothTarget.getBluetoothDevice().getAddress();

                        //refreshing the list
                        arrayAdapterDevice.clear();
                        arrayAdapterDevice.addAll(bondedDevices);

                        //restore selection
                        if (selectedBtAddress != null) {
                            for (int i = 0; i < arrayAdapterDevice.getCount(); i++) {
                                if (arrayAdapterDevice.getItem(i).getBluetoothDevice().getAddress().equals(selectedBtAddress)) {
                                    binding.spinnerBluetoothTarget.setSelection(i);
                                    break;
                                }
                            }
                        }
                    }

                    //set BT connection state
                    SpinnerItemDevice selectedBluetoothTarget = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();
                    boolean selectedDeviceConnected;

                    if (selectedBluetoothTarget != null) {
                        String selectedBtAddress = selectedBluetoothTarget.getBluetoothDevice().getAddress();
                        selectedDeviceConnected = connectedDevices.stream().anyMatch(d -> d.getAddress().equals(selectedBtAddress));
                        if (selectedDeviceConnected) {
                            status = getString(R.string.bt_connected);
                            drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_baseline_bluetooth_connected_24, getContext().getTheme());
                        }
                    }

                    binding.chipBtStatus.setChipIcon(drawable);
                    binding.chipBtStatus.setText(status);

                    //set TYPE button state
                    KeyboardLayout selectedLayout = (KeyboardLayout) binding.spinnerKeyboardLayout.getSelectedItem();
                    binding.btnType.setEnabled(bluetoothAdapterEnabled &&
                            selectedBluetoothTarget != null &&
//                            selectedDeviceConnected &&
                            selectedLayout != null &&
                            message != null &&
                            !typing.get());

                    binding.textTyping.setVisibility(typing.get() ? View.VISIBLE : View.INVISIBLE);

                } finally {
                    refreshingBtControls.set(false);
                }
            });
        }).start();
    }

    class BluetoothBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null)
                Log.d(TAG, intent.getAction());

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
            if (requireContext().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                throw new RuntimeException(getString(R.string.insufficient_permissions));
            }
            return bluetoothDevice.getAlias() + " (" + bluetoothDevice.getAddress() + ")";
        }
    }

    public class BluetoothHidDeviceCallback extends BluetoothHidDevice.Callback {

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            super.onConnectionStateChanged(device, state);
            Log.i(TAG, "onConnectionStateChanged -  device: " + device + ", state: " + state);

            refreshBluetoothControls();

            List<Stroke> queuedList = keyboardQueue.remove(device.getAddress());
            if (queuedList != null) {
                Log.d(TAG, "found queued message, size " + queuedList.size());
                type(queuedList);
            }
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
            Log.i(TAG, "onAppStatusChanged -  device: " + device + ", registered: " + registered);

            if (device == null) return;

            super.onAppStatusChanged(device, registered);

            if (registered) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                List<BluetoothDevice> pairedDevices = bluetoothController.getBluetoothHidDevice().getDevicesMatchingConnectionStates(
                        new int[]{BluetoothProfile.STATE_CONNECTING,
                                BluetoothProfile.STATE_CONNECTED,
                                BluetoothProfile.STATE_DISCONNECTED,
                                BluetoothProfile.STATE_DISCONNECTING});
                Log.d(TAG, "paired devices: " + pairedDevices);

                if (bluetoothController.getBluetoothHidDevice().getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED)
                    bluetoothController.getBluetoothHidDevice().connect(device);
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
            if (menuItem.getItemId() == R.id.menuItemOutputCopy) {
                copyValue.run();
            } else if (menuItem.getItemId() == R.id.menuItemShare) {
                if (beforePause != null) beforePause.run();

                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, message);

                sendIntent.putExtra(Intent.EXTRA_TITLE, shareTitle);
                sendIntent.setType("text/plain");

                Intent shareIntent = Intent.createChooser(sendIntent, null);
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