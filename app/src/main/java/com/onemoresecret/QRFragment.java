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
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
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

import com.google.zxing.Result;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.OneTimePassword;
import com.onemoresecret.databinding.FragmentQrBinding;
import com.onemoresecret.qr.MessageParser;
import com.onemoresecret.qr.QRCodeAnalyzer;

import java.io.ByteArrayInputStream;
import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
    private MessageParser parser;
    private final AtomicBoolean messageReceived = new AtomicBoolean(false);
    private long nextPinRequestTimestamp = 0;
    private static String PROP_USE_ZXING = "use_zxing";

    public static final String ARG_FILENAME = "FILENAME",
            ARG_FILESIZE = "FILESIZE",
            ARG_URI = "URI",
            ARG_MESSAGE = "MESSAGE",
            ARG_TEXT = "TEXT";


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

        parser = new MessageParser() {
            @Override
            public void onMessage(String message) {
                if (messageReceived.get()) return;
                messageReceived.set(true);
                QRFragment.this.onMessage(message);
            }

            @Override
            public void onChunkReceived(BitSet receivedChunks, int cntReceived, int totalChunks) {
                QRFragment.this.onChunkReceived(receivedChunks, cntReceived, totalChunks);
            }
        };

        binding.swZxing.setChecked(preferences.getBoolean(PROP_USE_ZXING, false));

        binding.swZxing.setOnCheckedChangeListener((compoundButton, b) -> preferences.edit().putBoolean(PROP_USE_ZXING, b).commit());
    }

    @Override
    public void onStart() {
        super.onStart();
        checkBiometrics();
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "resuming...");
        //get ready to receive new messages
        messageReceived.set(false);
    }

    private boolean processIntent(Intent intent) {
        try {
            if (intent != null) {
                var action = intent.getAction();
                var type = intent.getType();
                Log.d(TAG, "Intent action: " + action + ", type: " + type);

                switch (intent.getAction()) {
                    case Intent.ACTION_VIEW -> {
                        Uri data = intent.getData();
                        if (data != null) {
                            onMessage(data.getPath().substring(1));
                            return true;
                        } else {
                            Toast.makeText(requireContext(), R.string.malformed_intent, Toast.LENGTH_LONG).show();
                        }
                    }
                    case Intent.ACTION_SEND -> {
                        //a piece of text has been sent to the app using Android "send to" functionality
                        var text = intent.getStringExtra(Intent.EXTRA_TEXT);

                        if (text == null) {
                            var uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
                            Log.d(TAG, "URI: " + uri);

                            var bundle = new Bundle();
                            bundle.putParcelable(ARG_URI, uri);

                            var fileInfo = Util.getFileInfo(requireContext(), uri);

                            bundle.putString(ARG_FILENAME, fileInfo.filename());
                            bundle.putInt(ARG_FILESIZE, fileInfo.fileSize());

                            if (fileInfo.filename().endsWith("." + MessageComposer.OMS_FILE_TYPE)) {
                                Log.d(TAG, "calling " + MessageFragment.class.getSimpleName());
                                NavHostFragment.findNavController(QRFragment.this)
                                        .navigate(R.id.action_QRFragment_to_MessageFragment, bundle);
                            } else {
                                //pass URI to file encoder
                                Log.d(TAG, "calling " + FileEncryptionFragment.class.getSimpleName());
                                NavHostFragment.findNavController(QRFragment.this)
                                        .navigate(R.id.action_QRFragment_to_fileEncryptionFragment, bundle);
                            }
                        } else {
                            if (MessageComposer.decode(text) == null) {
                                //this is not an OMS message, forward it to the text encryption fragment
                                var bundle = new Bundle();
                                bundle.putString(ARG_TEXT, text);

                                NavHostFragment.findNavController(QRFragment.this)
                                        .navigate(R.id.action_QRFragment_to_encryptTextFragment, bundle);
                            } else {
                                onMessage(text);
                            }
                        }
                        return true;
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(getContext(),
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()),
                    Toast.LENGTH_LONG).show();
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
        var cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                var preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());

                var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                cameraProvider.unbindAll();

                imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(requireContext().getMainExecutor(), new QRCodeAnalyzer(() -> binding.swZxing.isChecked()) {
                    @Override
                    public void onQRCodeFound(String barcodeValue) {
                        try {
                            parser.consume(barcodeValue);
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

        var bArr = MessageComposer.decode(message);
        if (bArr == null) {
            Toast.makeText(getContext(), getString(R.string.could_not_decode), Toast.LENGTH_LONG).show();
            return;
        }

        Runnable pinProtectedRunnable = null;

        try (var bais = new ByteArrayInputStream(bArr);
             var dataInputStream = new OmsDataInputStream(bais)) {

            var bundle = new Bundle();
            bundle.putByteArray(ARG_MESSAGE, bArr);
            var navController = NavHostFragment.findNavController(QRFragment.this);

            //other supported formats?
            if (new OneTimePassword(message).isValid()) {
                //time based OTP
                pinProtectedRunnable = () -> {
                    Log.d(TAG, "calling " + TotpImportFragment.class.getSimpleName());
                    navController.navigate(R.id.action_QRFragment_to_TotpImportFragment, bundle);
                };
            } else {
                //(1) application ID
                var applicationId = dataInputStream.readUnsignedShort();
                Log.d(TAG, "Application-ID: " + Integer.toHexString(applicationId));

                switch (applicationId) {
                    case MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER -> {
                        //key import is not PIN protected
                        Log.d(TAG, "calling " + KeyImportFragment.class.getSimpleName());
                        navController.navigate(R.id.action_QRFragment_to_keyImportFragment, bundle);
                    }
                    case MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER,
                            MessageComposer.APPLICATION_TOTP_URI_TRANSFER,
                            MessageComposer.APPLICATION_KEY_REQUEST -> {
                        pinProtectedRunnable = () -> {
                            Log.d(TAG, "calling " + MessageFragment.class.getSimpleName());
                            navController.navigate(R.id.action_QRFragment_to_MessageFragment, bundle);
                        };
                    }
                    default -> Log.d(TAG,
                            "No processor defined for application ID " +
                                    Integer.toHexString(applicationId)
                    );
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        //PIN protected functions
        if (pinProtectedRunnable != null)
            runPinProtected(pinProtectedRunnable,
                    () -> messageReceived.set(false) /* enable message processing again */,
                    true);
    }

    private void runPinProtected(Runnable onSuccess,
                                 @Nullable Runnable onCancel,
                                 boolean evaluateNextPinRequestTimestamp) {

        if (preferences.getBoolean(PinSetupFragment.PROP_PIN_ENABLED, false) &&
                (System.currentTimeMillis() > nextPinRequestTimestamp || !evaluateNextPinRequestTimestamp)) {

            new PinEntryFragment(() -> {
                if (evaluateNextPinRequestTimestamp) {
                    //calculate next pin request time
                    var interval_ms = preferences.getLong(PinSetupFragment.PROP_REQUEST_INTERVAL_MINUTES, 0) * 60_000L;
                    nextPinRequestTimestamp = interval_ms == 0 ? Long.MAX_VALUE : System.currentTimeMillis() + interval_ms;
                }

                onSuccess.run();
            }, onCancel).show(requireActivity().getSupportFragmentManager(), null);
        } else {
            requireContext().getMainExecutor().execute(onSuccess);
        }
    }

    private BitSet lastReceivedChunks = null;

    private void onChunkReceived(BitSet receivedChunks, int cntReceived, int totalChunks) {
        if (messageReceived.get()) return;
        if (receivedChunks.equals(lastReceivedChunks)) return;
        lastReceivedChunks = receivedChunks;

        var s = IntStream.range(0, totalChunks)
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
                //based on pre-launch test
                //Exception java.lang.NullPointerException: Attempt to invoke virtual method 'android.content.ClipData$Item android.content.ClipData.getItemAt(int)'
                // on a null object reference
                var clipData = clipboardManager.getPrimaryClip();
                if (clipData != null) {
                    var item = clipboardManager.getPrimaryClip().getItemAt(0);
                    var text = item.getText().toString();
                    if (text != null) {
                        onMessage(text);
                    }
                }
            } else if (menuItem.getItemId() == R.id.menuItemFeedbackEmail) {
                var crashReportData = new CrashReportData(null);

                Consumer<Boolean> sendEmail = b -> {
                    try {
                        var intentSendTo = new Intent(Intent.ACTION_SENDTO);
                        intentSendTo.setData(Uri.parse("mailto:"));
                        intentSendTo.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret feedback");
                        intentSendTo.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.contact_email)});
                        intentSendTo.putExtra(Intent.EXTRA_TEXT, getString(R.string.feedback_prompt) + "\n\n" + crashReportData.toString(b));
                        startActivity(intentSendTo);
                    } catch (ActivityNotFoundException ex) {
                        requireContext().getMainExecutor().execute(
                                () -> Toast.makeText(getContext(), "Could not send email", Toast.LENGTH_LONG).show());
                    }
                };

                var builder = new AlertDialog.Builder(requireContext());
                builder.setTitle("Include logcat into feedback?")
                        .setPositiveButton("Yes", (dialogInterface, i) -> sendEmail.accept(true))
                        .setNegativeButton("No", (dialogInterface, i) -> {
                            sendEmail.accept(false);
                            dialogInterface.dismiss();
                        });

                requireActivity().getMainExecutor().execute(() -> builder.create().show());
            } else if (menuItem.getItemId() == R.id.menuItemFeedbackDiscord) {
                Util.openUrl(R.string.discord_url, requireContext());
            } else if (menuItem.getItemId() == R.id.menuItemEncryptText) {
                NavHostFragment.findNavController(QRFragment.this)
                        .navigate(R.id.action_QRFragment_to_encryptTextFragment);
            } else if (menuItem.getItemId() == R.id.menuItemTotp) {
                NavHostFragment.findNavController(QRFragment.this)
                        .navigate(R.id.action_QRFragment_to_totpManualEntryFragment);
            } else if (menuItem.getItemId() == R.id.menuItemPinSetup) {
                runPinProtected(
                        () -> NavHostFragment.findNavController(QRFragment.this)
                                .navigate(R.id.action_QRFragment_to_pinSetupFragment),
                        null, false);
            } else if (menuItem.getItemId() == R.id.menuItemLogcat) {
                var sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, new CrashReportData(null).toString(true));

                sendIntent.putExtra(Intent.EXTRA_TITLE, "Diagnose Data");
                sendIntent.setType("text/plain");

                var shareIntent = Intent.createChooser(sendIntent, null);
                startActivity(shareIntent);
            } else if (menuItem.getItemId() == R.id.menuItemPanic) {
                if (preferences.getBoolean(PinSetupFragment.PROP_PIN_ENABLED, false)) {
                    nextPinRequestTimestamp = 0;
                    requireContext().getMainExecutor().execute(
                            () -> Toast.makeText(getContext(), R.string.locked, Toast.LENGTH_LONG).show());
                } else {
                    //PIN not enabled, go to PIN setup instead
                    requireContext().getMainExecutor().execute(
                            () -> Toast.makeText(getContext(), R.string.enable_pin_first, Toast.LENGTH_LONG).show());
                    NavHostFragment.findNavController(QRFragment.this)
                            .navigate(R.id.action_QRFragment_to_pinSetupFragment);
                }
            } else {
                return false;
            }

            return true;
        }
    }
}