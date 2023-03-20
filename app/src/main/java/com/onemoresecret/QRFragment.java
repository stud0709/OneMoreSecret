package com.onemoresecret;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ClipData;
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
import androidx.annotation.Nullable;
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
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.databinding.FragmentQrBinding;
import com.onemoresecret.qr.MessageParser;
import com.onemoresecret.qr.QRCodeAnalyzer;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class QRFragment extends Fragment {
    private static final String TAG = QRFragment.class.getSimpleName();

    private FragmentQrBinding binding;
    private ImageAnalysis imageAnalysis;
    private ProcessCameraProvider cameraProvider;

    private boolean consumeIntentIfAny = true;

    private final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.CAMERA
    };

    private final QrMenuProvider menuProvider = new QrMenuProvider();

    private ClipboardManager clipboardManager;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentQrBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        clipboardManager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);

        requireActivity().addMenuProvider(menuProvider);

        binding.txtAppVersion.setText(BuildConfig.VERSION_NAME);

        Intent intent = requireActivity().getIntent();

        if (intent != null && consumeIntentIfAny) {
            String action = intent.getAction();
            String type = intent.getType();
            Log.d(TAG, "Action: " + action + ", type: " + type);

            switch (intent.getAction()) {
                case Intent.ACTION_VIEW:
                    Uri data = intent.getData();
                    if (data != null && data.getQuery().startsWith(MessageComposer.OMS_PREFIX)) {
                        onMessage(data.getPath().substring(1));
                    }
                    break;
                case Intent.ACTION_SEND:
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    onMessage(text);
                    break;
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
                    Toast.makeText(getContext(), R.string.insufficient_permissions, Toast.LENGTH_LONG).show();
                }
            }).launch(REQUIRED_PERMISSIONS);
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        checkBiometrics();
    }

    @Override
    public void onResume() {
        super.onResume();
        consumeIntentIfAny = false;
    }

    private void checkBiometrics() {
        BiometricManager biometricManager = (BiometricManager) requireContext().getSystemService(Context.BIOMETRIC_SERVICE);

        switch (biometricManager.
                canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK |
                        BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.biometrics_unavailable)
                        .setMessage(R.string.biometrics_unavailable_long_text)
                        .setIcon(R.drawable.baseline_fingerprint_24)
                        .setNegativeButton(R.string.exit, (dialog, which) -> requireActivity().finish())
                        .show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.biometrics_not_detected)
                        .setMessage(R.string.biometrics_not_detected_long_text)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setNegativeButton(R.string.exit, (dialog, which) -> requireActivity().finish())
                        .show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                new AlertDialog.Builder(getContext())
                        .setTitle(R.string.biometrics_not_enabled)
                        .setMessage(R.string.biometrics_not_enabled_long_text)
                        .setIcon(R.drawable.baseline_fingerprint_24)
                        .setPositiveButton(R.string.open_settings, (dialog, which) -> startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)))
                        .setNegativeButton(R.string.exit, (dialog, which) -> requireActivity().finish())
                        .show();
                break;
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED:
            case BiometricManager.BIOMETRIC_SUCCESS:
                break;
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

    /**
     * Try to process inbound message
     * @param message Message in OMS format
     * @see MessageComposer
     */
    private void onMessage(String message) {
        requireContext().getMainExecutor().execute(() -> {
            byte[] bArr = MessageComposer.decode(message);
            if (bArr == null) {
                Toast.makeText(getContext(), R.string.wrong_message_format, Toast.LENGTH_LONG).show();
                return;
            }

            try (ByteArrayInputStream bais = new ByteArrayInputStream(bArr);
                 OmsDataInputStream dataInputStream = new OmsDataInputStream(bais)) {

                //(1) application ID
                int applicationId = dataInputStream.readUnsignedShort();
                Log.d(TAG, "Application-ID: " + Integer.toHexString(applicationId));

                Bundle bundle = new Bundle();
                bundle.putByteArray("MESSAGE", bArr);

                switch (applicationId) {
                    case MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER:
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
            if (menuItem.getItemId() == R.id.menuItemQrPrivateKeys) {
                NavHostFragment.findNavController(QRFragment.this)
                        .navigate(R.id.action_QRFragment_to_keyManagementFragment);
            } else if (menuItem.getItemId() == R.id.menuItemHelp) {
                Util.openUrl(R.string.qr_scanner_md_url, requireContext());
            } else if (menuItem.getItemId() == R.id.menuItemPwdGenerator) {
                NavHostFragment.findNavController(QRFragment.this)
                        .navigate(R.id.action_QRFragment_to_passwordGeneratorFragment);
            } else if (menuItem.getItemId() == R.id.menuItemHomePage) {
                Util.openUrl(R.string.readme_url, requireContext());
            } else if (menuItem.getItemId() == R.id.menuItemPaste) {
                ClipData.Item item = clipboardManager.getPrimaryClip().getItemAt(0);
                String text = (String) item.getText();
                if (text != null) {
                    onMessage(text);
                }
            } else {
                return false;
            }

            return true;
        }
    }
}