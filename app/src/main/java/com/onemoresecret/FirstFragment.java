package com.onemoresecret;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.fragment.NavHostFragment;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.zxing.Result;
import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.bt.KeyboardReport;
import com.onemoresecret.bt.layout.GermanLayout;
import com.onemoresecret.databinding.FragmentFirstBinding;
import com.onemoresecret.qr.MessageParser;
import com.onemoresecret.qr.MessageProcessor;
import com.onemoresecret.qr.QRCodeAnalyzer;

import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class FirstFragment extends Fragment {
    private static final String TAG = FirstFragment.class.getSimpleName();

    private FragmentFirstBinding binding;

    private final String REQUIRED_PERMISSIONS[] = {
            Manifest.permission.CAMERA,
            //TODO: remove after bluetooth test
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
    };

    private BluetoothController bluetoothController;
    private final MessageProcessor messageProcessor = new MessageProcessor((callback) -> new BiometricPrompt(this, callback));

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);

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

        binding.switch90.setOnCheckedChangeListener((compoundButton, b) -> startCamera());

        binding.buttonHid.setOnClickListener(view -> {
            Log.d(TAG, "HID has been clicked");
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            List<KeyboardReport[]> list = new GermanLayout().forString("Hello, World!");

            Log.d(TAG, "got " + list.size() + " report arrays");

            getContext().getMainExecutor().execute(() -> {
                list.stream().flatMap(a -> Arrays.stream(a)).forEach(r ->
                        bluetoothController.getBtHid().sendReport(
                                bluetoothController.getBtHid().getConnectedDevices().get(0),
                                0,
                                r.report));
            });
        });


        return binding.getRoot();

    }

    private void onAllPermissionsGranted() {
        //TODO: remove after bluetooth test >>>
        bluetoothController = new BluetoothController(getContext());
        bluetoothController.requestDiscoverable(this, 60);
        //TODO: <<<
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
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();

                MessageParser parser = new MessageParser() {
                    @Override
                    public void onMessage(String message) {
                        Log.d("QR", "message: " + message);
                        try {
                            getContext().getMainExecutor().execute(() -> {
                                cameraProvider.unbindAll();
                            });

                            messageProcessor.onMessage(message, getContext());
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    @Override
                    public void onChunkReceived(BitSet receivedChunks, int cntReceived, int totalChunks) {
                        Log.d("MessageParser", cntReceived + " of " + totalChunks + " received");
                    }
                };

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setTargetResolution(new Size(binding.cameraPreview.getWidth(), binding.cameraPreview.getHeight()))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setTargetRotation(binding.switch90.isChecked() ? Surface.ROTATION_90 : Surface.ROTATION_0)
                                .build();

                imageAnalysis.setAnalyzer(getContext().getMainExecutor(), new QRCodeAnalyzer() {
                    @Override
                    public void qrCodeNotFound() {
                        return;
                    }

                    @Override
                    public void onQRCodeFound(Result result) {
                        //Log.d("QR", result.getText());
                        try {
                            parser.consume(result.getText());
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

                cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(getContext(), "Error starting camera " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }, getContext().getMainExecutor());
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}