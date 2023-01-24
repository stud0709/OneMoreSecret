package com.onemoresecret;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.bt.KeyboardReport;
import com.onemoresecret.bt.layout.GermanLayout;
import com.onemoresecret.bt.layout.KeyboardLayout;
import com.onemoresecret.bt.layout.KeyboardUsage;
import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentMessageBinding;
import com.onemoresecret.qr.MessageProcessorApplication;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MessageFragment extends Fragment {
    private static final String TAG = MessageFragment.class.getSimpleName();
    private byte[] cipherText, encryptedAesSecretKey, iv;
    private String rsaTransformation, aesTransformation;
    private BluetoothController bluetoothController;
    private ArrayAdapter<SpinnerItemDevice> arrayAdapterDevice;
    private final String REQUIRED_PERMISSIONS[] = {
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_SCAN
    };
    private Map<String, List<KeyboardReport[]>> keyboardQueue = new ConcurrentHashMap<>();

    private FragmentMessageBinding binding;
    private BluetoothBroadcastReceiver bluetoothBroadcastReceiver;

    private static final int DISCOVERABLE_DURATION_S = 60;
    private SharedPreferences preferences;
    private AtomicBoolean refreshingBtControls = new AtomicBoolean();
    private boolean paused = false;

    protected static final String LAST_SELECTED_KEYBOARD_LAYOUT = "last_selected_kbd_layout", LAST_SELECTED_BT_TARGET = "last_selected_bt_target";

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentMessageBinding.inflate(inflater, container, false);
        preferences = getActivity().getPreferences(Context.MODE_PRIVATE);

        bluetoothController = new BluetoothController(this,
                result -> {
                },
                new BluetoothHidDeviceCallback()
        );

        if (isAllPermissionsGranted()) {
            onAllPermissionsGranted();
        } else {
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (isAllPermissionsGranted()) {
                    onAllPermissionsGranted();
                } else {
                    Toast.makeText(getContext(), "Insufficient permissions", Toast.LENGTH_SHORT).show();
                    //todo
                }
            }).launch(REQUIRED_PERMISSIONS);
        }

        initSpinnerTargetDevice();

        initSpinnerKeyboardLayout();

        return binding.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        paused = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (paused)
            NavHostFragment.findNavController(MessageFragment.this).popBackStack();
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
                preferences.edit().putString(LAST_SELECTED_BT_TARGET, selectedItem.getBluetoothDevice().getAddress()).apply();

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
                .filter(l -> l != null) /* just in case */
                .sorted(Comparator.comparing(Object::toString))
                .forEach(l -> keyboardLayoutAdapter.add(l));

        binding.spinnerKeyboardLayout.setAdapter(keyboardLayoutAdapter);

        //select last used keyboard layout
        String lastSelectedKeyboardLayout = preferences.getString(LAST_SELECTED_KEYBOARD_LAYOUT, null);
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
                preferences.edit().putString(LAST_SELECTED_KEYBOARD_LAYOUT, selectedLayout.getClass().getName()).apply();

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

        getContext().registerReceiver(
                bluetoothBroadcastReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED),
                Context.RECEIVER_EXPORTED);

        getContext().registerReceiver(
                bluetoothBroadcastReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                Context.RECEIVER_EXPORTED);

        getContext().registerReceiver(
                bluetoothBroadcastReceiver,
                new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED),
                Context.RECEIVER_EXPORTED);

        //initialize "request discoverable" button
        binding.imgButtonDiscoverable.setOnClickListener(
                e -> bluetoothController.requestDiscoverable(DISCOVERABLE_DURATION_S));

        refreshBluetoothControls();

    }

    private boolean isAllPermissionsGranted() {
        if (Arrays.stream(REQUIRED_PERMISSIONS).allMatch(p -> ContextCompat.checkSelfPermission(getContext(), p) == PackageManager.PERMISSION_GRANTED))
            return true;

        Log.d(TAG, "Granted permissions:");
        Arrays.stream(REQUIRED_PERMISSIONS).forEach(p -> {
            int check = ContextCompat.checkSelfPermission(getContext(), p);
            Log.d(TAG, p + ": " + (check == PackageManager.PERMISSION_GRANTED) + " (" + check + ")");
        });

        return false;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String message = getArguments().getString("MESSAGE");

        try {
            String sArr[] = message.split("\t");

            //(1) Application ID
            int applicationId = Integer.parseInt(sArr[0]);
            if (applicationId != MessageProcessorApplication.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER)
                throw new IllegalArgumentException("wrong applicationId: " + applicationId);

            //(2) RSA transformation
            rsaTransformation = sArr[1];

            //(3) RSA fingerprint
            byte[] fingerprint = Base64.getDecoder().decode(sArr[2]);

            // (4) AES transformation
            aesTransformation = sArr[3];

            //(5) IV
            iv = Base64.getDecoder().decode(sArr[4]);

            //(6) RSA-encrypted AES secret key
            encryptedAesSecretKey = Base64.getDecoder().decode(sArr[5]);

            //(7) AES-encrypted message
            cipherText = Base64.getDecoder().decode(sArr[6]);

            //******* decrypting ********

            CryptographyManager cryptographyManager = new CryptographyManager();
            List<String> aliases = cryptographyManager.getByFingerprint(fingerprint);

            if (aliases.isEmpty()) throw new NoSuchElementException("No key found");

            showBiometricPromptForDecryption(aliases.get(0));
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(this.getContext(), ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showBiometricPromptForDecryption(String alias) throws
            InvalidAlgorithmParameterException,
            NoSuchAlgorithmException,
            KeyStoreException,
            NoSuchProviderException {

        BiometricPrompt.AuthenticationCallback authenticationCallback = new BiometricPrompt.AuthenticationCallback() {

            @Override
            public void onAuthenticationError(int errCode, CharSequence errString) {
                super.onAuthenticationError(errCode, errString);
                MessageFragment.this.onAuthenticationError(errCode, errString);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                MessageFragment.this.onAuthenticationFailed();
            }

            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                MessageFragment.this.onAuthenticationSucceeded(result);
            }
        };

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, authenticationCallback);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.prompt_info_title))
                .setSubtitle(getString(R.string.prompt_info_subtitle))
                .setDescription(getString(R.string.prompt_info_description))
                .setNegativeButtonText(getString(R.string.prompt_info_negative_text))
                .setConfirmationRequired(false)
                .build();

        Cipher cipher = new CryptographyManager().getInitializedCipherForDecryption(
                alias, rsaTransformation);

        biometricPrompt.authenticate(
                promptInfo,
                new BiometricPrompt.CryptoObject(cipher));
    }

    public void onAuthenticationError(int errCode, CharSequence errString) {
        Log.d(TAG,
                "Authentication failed: " + errString + " (" + errCode + ")");
        getContext().getMainExecutor().execute(() -> {
            Toast.makeText(getContext(), errString + " (" + errCode + ")", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(MessageFragment.this).popBackStack();
        });
    }

    public void onAuthenticationFailed() {
        Log.d(TAG,
                "User biometric rejected");
        getContext().getMainExecutor().execute(() -> {
            Toast.makeText(getContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(MessageFragment.this).popBackStack();
        });
    }

    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        Log.d(TAG,
                "Authentication was successful");

        Cipher cipher = result.getCryptoObject().getCipher();
        try {
            byte[] aesSecretKeyData = cipher.doFinal(encryptedAesSecretKey);
            SecretKey aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");
            byte[] bArr = AESUtil.decrypt(cipherText, aesSecretKey, new IvParameterSpec(iv), aesTransformation);

            onDecryptedData(bArr);
        } catch (Exception e) {
            e.printStackTrace();
            getContext().getMainExecutor().execute(() -> {
                Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(MessageFragment.this).popBackStack();
            });
        }
    }

    private void onDecryptedData(byte[] bArr) throws NoSuchAlgorithmException {
        String[] sArr = new String(bArr).split("\t");
        // (1) message
        byte[] messageBytes = Base64.getDecoder().decode(sArr[0]);
        String message = new String(messageBytes);

        // (2) hash
        byte[] hash = Base64.getDecoder().decode(sArr[1]);

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] _hash = sha256.digest(messageBytes);

        if (!Arrays.equals(hash, _hash)) {
            throw new IllegalArgumentException("Could not confirm message integrity");
        }

        binding.swRevealMessage.setOnCheckedChangeListener((compoundButton, b) -> {
            binding.textViewMessage.setText(b ? message : getString(R.string.hidden_text_slide_to_reveal));
        });

        binding.btnType.setOnClickListener(view -> {
            KeyboardLayout selectedLayout = (KeyboardLayout) binding.spinnerKeyboardLayout.getSelectedItem();
            List<KeyboardReport[]> reports = selectedLayout.forString(message);

            type(reports);
        });
    }

    protected void type(List<KeyboardReport[]> list) {
        SpinnerItemDevice selectedSpinnerItem = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();

        if (selectedSpinnerItem == null) {
            getContext().getMainExecutor().execute(() -> Toast.makeText(getContext(), "Select BT target", Toast.LENGTH_LONG));
            return;
        }

        if (getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (!bluetoothController.getBluetoothHidDevice().getConnectedDevices()
                .stream().filter(d -> d.getAddress().equals(selectedSpinnerItem.getBluetoothDevice().getAddress()))
                .findAny().isPresent()) {
            bluetoothController.getBluetoothHidDevice().connect(selectedSpinnerItem.getBluetoothDevice());
            Log.d(TAG, "queueing message for " + selectedSpinnerItem.getBluetoothDevice().getAddress() + " (size: " + list.size() + ")");
            keyboardQueue.put(selectedSpinnerItem.getBluetoothDevice().getAddress(), list);
            return;
        }

        Log.d(TAG, "sending message (size: " + list.size() + ")");

        if (getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        new Thread(() -> {
            list.stream().flatMap(a -> Arrays.stream(a)).forEach(r -> {
                bluetoothController.getBluetoothHidDevice().sendReport(
                        ((SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem()).getBluetoothDevice(),
                        0,
                        r.report);
            });
        }).start();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (bluetoothBroadcastReceiver != null)
            getContext().unregisterReceiver(bluetoothBroadcastReceiver);
        if (getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        bluetoothController.getBluetoothHidDevice().getConnectedDevices().stream().forEach(d -> bluetoothController.getBluetoothHidDevice().disconnect(d));
        bluetoothController.getBluetoothHidDevice().unregisterApp();
        binding.textViewMessage.setText(getString(R.string.hidden_text_slide_to_reveal));
        binding.swRevealMessage.setOnCheckedChangeListener(null);
        binding.btnType.setOnClickListener(null);
        binding = null;
    }

    protected void refreshBluetoothControls() {
        if (getContext() == null) return; //post mortem call
        if (refreshingBtControls.get()) return; //called in loop

        new Thread(() -> {
            BluetoothAdapter bluetoothAdapter = bluetoothController.getAdapter();

            boolean bluetoothAdapterEnabled = bluetoothAdapter.isEnabled();

            if (getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            boolean discoverable = bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;

            List<BluetoothDevice> connectedDevices = bluetoothController.getBluetoothHidDevice() == null ?
                    Collections.emptyList() :
                    bluetoothController.getBluetoothHidDevice().getConnectedDevices();

            SpinnerItemDevice bondedDevices[] = bluetoothAdapter.getBondedDevices().stream().filter(
                            d -> d.getBluetoothClass()
                                    .getMajorDeviceClass() == BluetoothClass.Device.Major.COMPUTER)
                    .map(d -> new SpinnerItemDevice(d))
                    .sorted((s1, s2) -> {
                        int i1 = connectedDevices.contains(s1.getBluetoothDevice()) ? 0 : 1;
                        int i2 = connectedDevices.contains(s2.getBluetoothDevice()) ? 0 : 1;
                        if (i1 != i2) return Integer.compare(i1, i2);

                        return s1.toString().compareTo(s2.toString());
                    })
                    .collect(Collectors.toList()).toArray(new SpinnerItemDevice[]{});

            getContext().getMainExecutor().execute(() -> {
                Log.d(TAG, "Refreshing controls");
                refreshingBtControls.set(true);

                try {
                    binding.imgButtonDiscoverable.setEnabled(bluetoothAdapterEnabled && !discoverable);

                    String status = "off";
                    Drawable drawable = getResources().getDrawable(R.drawable.ic_baseline_bluetooth_disabled_24, getContext().getTheme());

                    if (bluetoothAdapterEnabled) {
                        status = "disconnected";
                        drawable = getResources().getDrawable(R.drawable.ic_baseline_bluetooth_24, getContext().getTheme());
                    }

                    if (discoverable) {
                        status = "discoverable";
                        drawable = getResources().getDrawable(R.drawable.ic_baseline_bluetooth_discovering_24, getContext().getTheme());
                    }

                    binding.spinnerBluetoothTarget.setEnabled(bluetoothAdapterEnabled);

                    //remember selection
                    {
                        SpinnerItemDevice selectedBluetoothTarget = (SpinnerItemDevice) binding.spinnerBluetoothTarget.getSelectedItem();
                        String selectedBtAddress = selectedBluetoothTarget == null ?
                                preferences.getString(LAST_SELECTED_BT_TARGET, null) :
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

                    if (selectedBluetoothTarget != null) {
                        String selectedBtAddress = selectedBluetoothTarget.getBluetoothDevice().getAddress();
                        if (connectedDevices.stream().filter(d -> d.getAddress().equals(selectedBtAddress)).findAny().isPresent()) {
                            status = "connected";
                            drawable = getResources().getDrawable(R.drawable.ic_baseline_bluetooth_connected_24, getContext().getTheme());
                        }
                    }

                    binding.chipBtStatus.setChipIcon(drawable);
                    binding.chipBtStatus.setText(status);

                    //set TYPE button state
                    KeyboardLayout selectedLayout = (KeyboardLayout) binding.spinnerKeyboardLayout.getSelectedItem();
                    binding.btnType.setEnabled(bluetoothAdapterEnabled && selectedBluetoothTarget != null && selectedLayout != null);
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

        @Override
        public String toString() {
            if (getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return null;
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

            List<KeyboardReport[]> queuedList = keyboardQueue.remove(device.getAddress());
            if (queuedList != null) {
                Log.d(TAG, "found queued message, size " + queuedList.size());
                type(queuedList);
            }
        }

        @Override
        public void onSetReport(BluetoothDevice device, byte type, byte id, byte[] data) {
            super.onSetReport(device, type, id, data);
            Log.i(TAG, "onSetReport - device: " + device.toString() + ", type: " + type + ", id: " + id + ", data: " + BluetoothController.byteArrayToHex(data));
        }

        @Override
        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            super.onGetReport(device, type, id, bufferSize);
            Log.i(TAG, "onGetReport - device: " + device + ", type: " + type + " id: " + id + ", bufferSize: " + bufferSize);
        }

        @Override
        public void onAppStatusChanged(BluetoothDevice device, boolean registered) {
            if (device == null) return;

            super.onAppStatusChanged(device, registered);
            Log.i(TAG, "onAppStatusChanged -  device: " + device + ", registered: " + registered);

            if (registered) {
                if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
            Log.d(TAG, "onInterruptData - -  device: " + device + ", reportId: " + reportId + ", data: " + BluetoothController.byteArrayToHex(data));

//            boolean numLockActive = (data[0] & KeyboardReport.NUM_LOCK) == KeyboardReport.NUM_LOCK;
//            boolean capsLockActive = (data[0] & KeyboardReport.CAPS_LOCK) == KeyboardReport.CAPS_LOCK;
        }
    }
}