package com.onemoresecret;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import java.util.BitSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class QRFragment extends Fragment {
    private static final String TAG = QRFragment.class.getSimpleName();

    private FragmentQrBinding binding;
    private ImageAnalysis imageAnalysis;
    private ProcessCameraProvider cameraProvider;

    private final QrMenuProvider menuProvider = new QrMenuProvider();

    private ClipboardManager clipboardManager;

    private SharedPreferences preferences;


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

        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        if (!preferences.getBoolean(PermissionsFragment.PROP_PERMISSIONS_REQUESTED, false)) {
            NavHostFragment.findNavController(QRFragment.this)
                    .navigate(R.id.action_QRFragment_to_permissionsFragment);
            return;
        }

        clipboardManager = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);

        requireActivity().addMenuProvider(menuProvider);

        binding.txtAppVersion.setText(BuildConfig.VERSION_NAME);

        Intent intent = requireActivity().getIntent();
        if (intent != null) {
            requireActivity().setIntent(null);
            if (processIntent(intent)) return;
        }

        //enable camera
        if (getContext().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), result -> {
                if (getContext().checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    startCamera();
                } else {
                    Toast.makeText(getContext(), R.string.insufficient_permissions, Toast.LENGTH_LONG).show();
                }
            }).launch(Manifest.permission.CAMERA);
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
    }

    private boolean processIntent(Intent intent) {

        Log.d(TAG, "found intent: " + intent);

        if (intent != null) {
            String action = intent.getAction();
            String type = intent.getType();
            Log.d(TAG, "Action: " + action + ", type: " + type);

            switch (intent.getAction()) {
                case Intent.ACTION_VIEW:
                    Uri data = intent.getData();
                    if (data != null) {
                        onMessage(data.getPath().substring(1));
                        return true;
                    } else {
                        Toast.makeText(requireContext(), R.string.malformed_intent, Toast.LENGTH_LONG).show();
                    }
                    break;
                case Intent.ACTION_SEND:
                    //a piece of text has been sent to the app using Android "send to" functionality
                    String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                    if (MessageComposer.decode(text) == null) {
                        //this is not an OMS message, forward it to the text encryption fragment
                        Bundle bundle = new Bundle();
                        bundle.putString("TEXT", text);

                        NavHostFragment.findNavController(QRFragment.this)
                                .navigate(R.id.action_QRFragment_to_encryptTextFragment, bundle);
                    } else {
                        onMessage(text);
                    }
                    return true;
            }
        }
        return false;
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
     *
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
            } else if (menuItem.getItemId() == R.id.menuItemFeedback) {
                CrashReportData crashReportData = new CrashReportData(null);

                try {
                    Intent intentSendTo = new Intent(Intent.ACTION_SENDTO);
                    intentSendTo.setData(Uri.parse("mailto:"));
                    intentSendTo.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret feedback");
                    intentSendTo.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.contact_email)});
                    intentSendTo.putExtra(Intent.EXTRA_TEXT, crashReportData.toString(false) + "\n" + getString(R.string.feedback_prompt));
                    startActivity(intentSendTo);
                } catch (ActivityNotFoundException ex) {
                    requireContext().getMainExecutor().execute(
                            () -> Toast.makeText(getContext(), "Could not send email", Toast.LENGTH_LONG).show());
                }
            } else if (menuItem.getItemId() == R.id.menuItemEncryptText) {
                NavHostFragment.findNavController(QRFragment.this)
                        .navigate(R.id.action_QRFragment_to_encryptTextFragment);
            } else {
                return false;
            }

            return true;
        }
    }
}