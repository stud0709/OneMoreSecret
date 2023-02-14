package com.onemoresecret;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.Result;
import com.onemoresecret.databinding.FragmentQrBinding;
import com.onemoresecret.qr.MessageParser;
import com.onemoresecret.qr.QRCodeAnalyzer;

import java.util.Arrays;
import java.util.BitSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class QRFragment extends Fragment {
    private static final String TAG = QRFragment.class.getSimpleName();

    private FragmentQrBinding binding;
    private ImageAnalysis imageAnalysis;
    private ProcessCameraProvider cameraProvider;

    private final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA
    };

    private final QrMenuProvider menuProvider = new QrMenuProvider();

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentQrBinding.inflate(inflater, container, false);

        requireActivity().addMenuProvider(menuProvider);

        Intent intent = requireActivity().getIntent();
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null && data.getScheme().equals(MessageComposer.URI_SCHEME)) {
                String message = MessageComposer.decode(data.getSchemeSpecificPart());
                if (message == null) {
                    Toast.makeText(getContext(), "Wrong message format", Toast.LENGTH_LONG).show();
                } else {
                    onMessage(message);
                    return binding.getRoot();
                }
            }
        }

        //enable camera
        if (isAllPermissionsGranted()) {
            onAllPermissionsGranted();
        } else {
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (isAllPermissionsGranted()) {
                    onAllPermissionsGranted();
                } else {
                    Toast.makeText(getContext(), "Insufficient permissions", Toast.LENGTH_LONG).show();
                }
            }).launch(REQUIRED_PERMISSIONS);
        }

        return binding.getRoot();

    }

    @Override
    public void onStart() {
        super.onStart();
        checkBiometrics();
    }

    private void checkBiometrics() {
        BiometricManager biometricManager = (BiometricManager) requireContext().getSystemService(Context.BIOMETRIC_SERVICE);

        switch (biometricManager.
                canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK |
                        BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                new AlertDialog.Builder(getContext())
                        .setTitle("Biometrics unavailable")
                        .setMessage("Looks like your biometric hardware is not available right now. Try again later.")
                        .setIcon(R.drawable.baseline_fingerprint_24)
                        .setNegativeButton("Exit", (dialog, which) -> requireActivity().finish())
                        .show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                new AlertDialog.Builder(getContext())
                        .setTitle("Biometrics not detected")
                        .setMessage("Sorry, we cannot continue without biometric hardware.")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setNegativeButton("Exit", (dialog, which) -> requireActivity().finish())
                        .show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                new AlertDialog.Builder(getContext())
                        .setTitle("Biometrics not enabled")
                        .setMessage("Please set up biometric authentication on your device.")
                        .setIcon(R.drawable.baseline_fingerprint_24)
                        .setPositiveButton("Open Settings", (dialog, which) -> startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)))
                        .setNegativeButton("Exit", (dialog, which) -> requireActivity().finish())
                        .show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
            case BiometricManager.BIOMETRIC_SUCCESS:
                break;
        }
    }

    private void checkClipboard() {
        ClipboardManager clipboardManager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);

        Log.d(TAG, "primaryClip: " + clipboardManager.hasPrimaryClip());

        if (!clipboardManager.hasPrimaryClip()
                || !clipboardManager.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            return;
        }

        ClipData clipData = clipboardManager.getPrimaryClip();

        for (int i = 0; i < clipData.getItemCount(); i++) {
            String txt = clipData.getItemAt(i).getText().toString();
            String message = MessageComposer.decode(txt);
            if (message == null) {
                requireContext().getMainExecutor().execute(() -> Toast.makeText(getContext(), MessageComposer.OMS_PREFIX + "... not found", Toast.LENGTH_LONG).show());
            } else {
                //found text encoded message on the clipboard
                Log.d(TAG, message);
                clipboardManager.clearPrimaryClip();
                onMessage(message);
                return;
            }
        }

    }

    private void onAllPermissionsGranted() {
        startCamera();
    }

    private boolean isAllPermissionsGranted() {
        if (Arrays.stream(REQUIRED_PERMISSIONS).allMatch(p -> ContextCompat.checkSelfPermission(requireContext(), p) == PackageManager.PERMISSION_GRANTED))
            return true;

        Log.d(TAG, "Granted permissions:");
        Arrays.stream(REQUIRED_PERMISSIONS).forEach(p -> {
            int check = ContextCompat.checkSelfPermission(requireContext(), p);
            Log.d(TAG, p + ": " + (check == PackageManager.PERMISSION_GRANTED) + " (" + check + ")");
        });

        return false;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

//                cameraProvider.unbindAll();

                MessageParser parser = new MessageParser() {
                    @Override
                    public void onMessage(String message) {
                        QRFragment.this.onMessage(message);
                    }

                    @Override
                    public void onChunkReceived(BitSet receivedChunks, int cntReceived, int totalChunks) {
                        QRFragment.this.onChunkReceived(receivedChunks, cntReceived, totalChunks);
                    }
                };

                imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setTargetResolution(new Size(binding.cameraPreview.getWidth(), binding.cameraPreview.getHeight()))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(requireContext().getMainExecutor(), new QRCodeAnalyzer() {
                    @Override
                    public void onQRCodeFound(Result result) {
                        try {
                            parser.consume(result.getText());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }

                });

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), String.format(getString(R.string.error_starting_camera), e.getMessage()), Toast.LENGTH_SHORT).show();
            }
        }, requireContext().getMainExecutor());
    }

    private void onMessage(String message) {
        requireContext().getMainExecutor().execute(() -> {
            try {
                String[] sArr = message.split("\t", 2);

                //(1) application ID
                int applicationId = Integer.parseInt(sArr[0]);
                Log.d(TAG, "Application-ID: " + Integer.toHexString(applicationId));

                Bundle bundle = new Bundle();
                bundle.putString("MESSAGE", message);

                switch (applicationId) {
                    case MessageComposer.APPLICATION_AES_ENCRYPTED_KEY_PAIR_TRANSFER:
                        NavHostFragment.findNavController(QRFragment.this)
                                .navigate(R.id.action_QRFragment_to_keyImportFragment, bundle);
                        break;
                    case MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER:
                        Log.d(TAG, "calling " + MessageFragment.class.getSimpleName());
                        NavHostFragment.findNavController(QRFragment.this)
                                .navigate(R.id.action_QRFragment_to_MessageFragment, bundle);
                        break;
                    default:
                        Log.d(TAG,
                                "No processor defined for application ID " +
                                        Integer.toHexString(applicationId)
                        );
                        break;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private BitSet lastReceivedChunks = null;

    private void onChunkReceived(BitSet receivedChunks, int cntReceived, int totalChunks) {
        if (receivedChunks.equals(lastReceivedChunks)) return;
        lastReceivedChunks = receivedChunks;

        String s = IntStream.range(0, totalChunks)
                .filter(i -> !receivedChunks.get(i))
                .mapToObj(i -> Integer.toString(i + 1)).collect(Collectors.joining(", "));
        requireContext().getMainExecutor().execute(() -> {
            if (binding != null) { //prevent post mortem calls
                binding.txtRemainingCodes.setText(s);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        if (cameraProvider != null) cameraProvider.unbindAll();
        binding = null;
    }

    private class QrMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_qr, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemQrPaste) {
                checkClipboard();
            } else if (menuItem.getItemId() == R.id.menuItemQrPrivateKeys) {
                NavHostFragment.findNavController(QRFragment.this)
                        .navigate(R.id.action_QRFragment_to_keyStoreFragment);
            } else {
                return false;
            }

            return true;
        }
    }
}