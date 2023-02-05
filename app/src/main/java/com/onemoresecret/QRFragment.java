package com.onemoresecret;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.Result;
import com.onemoresecret.databinding.FragmentQrBinding;
import com.onemoresecret.qr.MessageParser;
import com.onemoresecret.qr.QRCodeAnalyzer;

import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.ExecutionException;
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

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentQrBinding.inflate(inflater, container, false);

        int canAuthenticate = ((BiometricManager) getContext().getSystemService(Context.BIOMETRIC_SERVICE))
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.BIOMETRIC_STRONG);

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            //TODO: biometric auth not supported - check this on startup
        }

        //enable camera
        if (isAllPermissionsGranted()) {
            onAllPermissionsGranted();
        } else {
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (isAllPermissionsGranted()) {
                    onAllPermissionsGranted();
                } else {
                    Toast.makeText(getContext(), "Insufficient permissions", Toast.LENGTH_SHORT).show();
                }
            }).launch(REQUIRED_PERMISSIONS);
        }

        binding.btnPaste.setOnClickListener(e -> checkClipboard());


        return binding.getRoot();

    }

    private boolean checkClipboard() {
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);

        Log.d(TAG, "primaryClip: " + clipboardManager.hasPrimaryClip());

        if (!clipboardManager.hasPrimaryClip()
                || !clipboardManager.getPrimaryClipDescription().hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            return false;
        }

        ClipData clipData = clipboardManager.getPrimaryClip();
        for (int i = 0; i < clipData.getItemCount(); i++) {
            String txt = String.valueOf(clipData.getItemAt(i).getText());
            if (txt != null) {
                String message = MessageComposer.decode(txt);
                if (message != null) {
                    //found text encoded message on the clipboard
                    Log.d(TAG, message);
                    clipboardManager.clearPrimaryClip();
                    onMessage(message);
                    return true;
                }
            }
        }

        return false;
    }

    private void onAllPermissionsGranted() {
        startCamera();
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

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());

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

                imageAnalysis.setAnalyzer(getContext().getMainExecutor(), new QRCodeAnalyzer() {
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
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(getContext(), "Error starting camera " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, getContext().getMainExecutor());
    }

    private void onMessage(String message) {
        try {
            getContext().getMainExecutor().execute(() -> {
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
                    default:
                        Log.d(TAG, "No processor defined for application ID " + Integer.toHexString(applicationId));
                        break;
                }
            });
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private BitSet lastReceivedChunks = null;

    private void onChunkReceived(BitSet receivedChunks, int cntReceived, int totalChunks) {
        if (receivedChunks.equals(lastReceivedChunks)) return;
        lastReceivedChunks = receivedChunks;

        String s = IntStream.range(0, totalChunks)
                .filter(i -> !receivedChunks.get(i))
                .mapToObj(i -> Integer.toString(i + 1)).collect(Collectors.joining(", "));
        getContext().getMainExecutor().execute(() -> binding.txtRemainingCodes.setText(s));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (cameraProvider != null) cameraProvider.unbindAll();
        binding = null;
    }

}