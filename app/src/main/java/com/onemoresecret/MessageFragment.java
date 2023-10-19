package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.AesTransformation;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.OneTimePassword;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.crypto.RsaTransformation;
import com.onemoresecret.databinding.FragmentMessageBinding;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MessageFragment extends Fragment {
    private static final String TAG = MessageFragment.class.getSimpleName();
    private byte[] encryptedAesSecretKey, iv, fingerprint;
    private String rsaTransformation, aesTransformation;
    private FragmentMessageBinding binding;
    private boolean reveal = false;
    private Runnable revealHandler = null;
    private final MessageMenuProvider menuProvider = new MessageMenuProvider();
    private volatile boolean navBackIfPaused = true;
    private Fragment messageView = null;
    private Consumer<BiometricPrompt.AuthenticationResult> onAuthenticationSucceeded = null;
    private SharedPreferences preferences;

    private final BiometricPrompt.AuthenticationCallback authenticationCallback = new BiometricPrompt.AuthenticationCallback() {

        @Override
        public void onAuthenticationError(int errCode, @NonNull CharSequence errString) {
            Log.d(TAG, String.format("Authentication failed: %s (%s)", errString, errCode));
            requireContext().getMainExecutor().execute(() -> {
                Toast.makeText(getContext(), errString + " (" + errCode + ")", Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(MessageFragment.this).popBackStack();
            });
        }

        @Override
        public void onAuthenticationFailed() {
            Log.d(TAG,
                    "User biometrics rejected");
            requireContext().getMainExecutor().execute(() -> {
                Toast.makeText(getContext(), getString(R.string.auth_failed), Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(MessageFragment.this).popBackStack();
            });
        }

        @Override
        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
            Log.d(TAG, getString(R.string.auth_successful));

            MessageFragment.this.onAuthenticationSucceeded.accept(result);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMessageBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        var navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination().getId() != R.id.MessageFragment) {
            Log.d(TAG, String.format("Already navigating to %s", navController.getCurrentDestination()));
            return;
        }
        if (navBackIfPaused) NavHostFragment.findNavController(this).popBackStack();
    }

    @Override
    public void onResume() {
        super.onResume();

        //rearm
        navBackIfPaused = true;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);
        requireActivity().addMenuProvider(menuProvider);

        ((OutputFragment) binding.messageOutputFragment.getFragment())
                .setBeforePause(() -> navBackIfPaused = false /* disarm backward navigation */);

        if (requireArguments().containsKey(QRFragment.ARG_MESSAGE)) {
            onMessage();
        } else if (requireArguments().containsKey(QRFragment.ARG_URI)) {
            onUri();
        }
    }

    private void onUri() {
        var uri = (Uri) getArguments().getParcelable("URI");

        try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
             var dataInputStream = new OmsDataInputStream(is)) {

            readHeaderUri(dataInputStream);

            //initialize file info fragment
            var fileInfoFragment = new FileInfoFragment();
            getChildFragmentManager().beginTransaction().add(R.id.fragmentMessageView, fileInfoFragment).commit();
            requireContext().getMainExecutor().execute(() -> fileInfoFragment.setValues(getArguments().getString(QRFragment.ARG_FILENAME), getArguments().getInt(QRFragment.ARG_FILESIZE)));

            onAuthenticationSucceeded = getForDataInputStream();

            showBiometricPromptForDecryption(null);
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(getContext(), getString(R.string.wrong_message_format), Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    private void readHeaderUri(OmsDataInputStream dataInputStream) throws IOException {
        //(1) Application ID
        var applicationId = dataInputStream.readUnsignedShort();
        assert applicationId == MessageComposer.APPLICATION_ENCRYPTED_FILE;

        //(2) RSA transformation index
        rsaTransformation = RsaTransformation.values()[dataInputStream.readUnsignedShort()].transformation;
        Log.d(TAG, "RSA transformation: " + rsaTransformation);

        //(3) RSA fingerprint
        fingerprint = dataInputStream.readByteArray();
        Log.d(TAG, "RSA fingerprint: " + Util.byteArrayToHex(fingerprint));

        // (4) AES transformation index
        aesTransformation = AesTransformation.values()[dataInputStream.readUnsignedShort()].transformation;
        Log.d(TAG, "AES transformation: " + aesTransformation);

        //(5) IV
        iv = dataInputStream.readByteArray();
        Log.d(TAG, "IV: " + Util.byteArrayToHex(iv));

        //(6) RSA-encrypted AES secret key
        encryptedAesSecretKey = dataInputStream.readByteArray();

        //the remaining data is the payload
    }

    private Consumer<BiometricPrompt.AuthenticationResult> getForDataInputStream() {
        return result -> {
            var uri = (Uri) getArguments().getParcelable("URI");

            var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();

            try (InputStream is = requireContext().getContentResolver().openInputStream(uri);
                 var dataInputStream = new OmsDataInputStream(is)) {

                assert cipher != null;

                //re-read header to get to the start position of the encrypted data
                readHeaderUri(dataInputStream);

                var aesSecretKeyData = cipher.doFinal(encryptedAesSecretKey);
                var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");
                var filename = getArguments().getString(QRFragment.ARG_FILENAME);

                try {
                    var oFileRecord = OmsFileProvider.create(requireContext(),
                            filename.substring(0, filename.length() - (MessageComposer.OMS_FILE_TYPE.length() + 1 /*the dot*/)),
                            true);

                    try (FileOutputStream fos = new FileOutputStream(oFileRecord.path().toFile())) {
                        AESUtil.process(Cipher.DECRYPT_MODE, dataInputStream,
                                fos,
                                aesSecretKey,
                                new IvParameterSpec(iv),
                                aesTransformation);
                    }

                    var intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("application/octet-stream");
                    intent.putExtra(Intent.EXTRA_STREAM, oFileRecord.uri());
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    startActivity(intent);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    requireActivity().getMainExecutor().execute(() -> Toast.makeText(requireContext(),
                            String.format("%s: %s", ex.getClass().getSimpleName(), ex.getMessage()),
                            Toast.LENGTH_LONG).show());
                }

                //requireActivity().invalidateOptionsMenu();
            } catch (Exception e) {
                e.printStackTrace();
                requireContext().getMainExecutor().execute(() -> {
                    Toast.makeText(getContext(),
                            e.getMessage() == null ? String.format(requireContext().getString(R.string.authentication_failed_s), e.getClass().getName()) : e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(MessageFragment.this).popBackStack();
                });
            }
        };
    }

    private void onMessage() {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(requireArguments().getByteArray(QRFragment.ARG_MESSAGE));
             var dataInputStream = new OmsDataInputStream(bais)) {

            String description = null;

            //(1) Application ID
            var applicationId = dataInputStream.readUnsignedShort();

            Consumer<byte[]> messageConsumer = null;

            switch (applicationId) {
                case MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER, MessageComposer.APPLICATION_KEY_REQUEST -> {
                    messageView = new HiddenTextFragment();
                    messageConsumer = onTextMessage;
                }
                case MessageComposer.APPLICATION_TOTP_URI_TRANSFER -> {
                    messageView = new TotpFragment();
                    messageConsumer = onTotpMessage;
                }
                default ->
                        throw new IllegalArgumentException(getString(R.string.wrong_application) + " " + applicationId);
            }

            getChildFragmentManager().beginTransaction().add(R.id.fragmentMessageView, messageView).commit();

            byte[] cipherText;

            switch (applicationId) {
                case MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER, MessageComposer.APPLICATION_TOTP_URI_TRANSFER -> {
                    //(2) RSA transformation index
                    rsaTransformation = RsaTransformation.values()[dataInputStream.readUnsignedShort()].transformation;
                    Log.d(TAG, "RSA transformation: " + rsaTransformation);

                    //(3) RSA fingerprint
                    fingerprint = dataInputStream.readByteArray();
                    Log.d(TAG, "RSA fingerprint: " + Util.byteArrayToHex(fingerprint));

                    // (4) AES transformation index
                    aesTransformation = AesTransformation.values()[dataInputStream.readUnsignedShort()].transformation;
                    Log.d(TAG, "AES transformation: " + aesTransformation);

                    //(5) IV
                    iv = dataInputStream.readByteArray();
                    Log.d(TAG, "IV: " + Util.byteArrayToHex(iv));

                    //(6) RSA-encrypted AES secret key
                    encryptedAesSecretKey = dataInputStream.readByteArray();

                    //(7) AES-encrypted message
                    cipherText = dataInputStream.readByteArray();

                    onAuthenticationSucceeded = getForByteArray(cipherText, messageConsumer);
                }
                case MessageComposer.APPLICATION_KEY_REQUEST -> {
                    //(2) reference
                    description = String.format(getString(R.string.provided_reference), dataInputStream.readString());

                    //(3) RSA public key
                    var rsaPublicKey = RSAUtils.restorePublicKey(dataInputStream.readByteArray());

                    //(4) fingerprint of the requested key
                    fingerprint = dataInputStream.readByteArray();

                    //(5) transformation index for decryption
                    rsaTransformation = RsaTransformation.values()[dataInputStream.readUnsignedShort()].transformation;

                    //(6) AES key subject to decryption with RSA key specified by fingerprint at (4)
                    cipherText = dataInputStream.readByteArray();

                    onAuthenticationSucceeded = getForKeyRequest(cipherText, rsaPublicKey);
                }
            }

            //******* decrypting ********
            showBiometricPromptForDecryption(description);
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(getContext(), getString(R.string.wrong_message_format), Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    private Consumer<BiometricPrompt.AuthenticationResult> getForKeyRequest(byte[] cipherText, PublicKey rsaPublicKey) {
        return result -> {
            var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();
            try {
                assert cipher != null;

                //decrypt AES key
                var aesKeyMaterial = cipher.doFinal(cipherText);

                //encrypt AES key with the provided public key
                var message = RSAUtils.process(Cipher.ENCRYPT_MODE, rsaPublicKey, RSAUtils.getRsaTransformation(preferences).transformation, aesKeyMaterial);

                var base64Message = Base64.getEncoder().encodeToString(message);

                //make groups of four removing padding
                var pat = Pattern.compile("[^=]{1,4}");
                var matcher = pat.matcher(base64Message);
                StringBuilder sb = new StringBuilder();

                matcher.find();
                do {
                    sb.append(matcher.group());
                    if (!matcher.find()) break;
                    sb.append("-");
                } while (true);

                ((OutputFragment) getChildFragmentManager().findFragmentById(R.id.messageOutputFragment))
                        .setMessage(sb.toString(), getString(R.string.oms_secret_message));

                requireActivity().invalidateOptionsMenu();
            } catch (Exception e) {
                e.printStackTrace();
                requireContext().getMainExecutor().execute(() -> {
                    Toast.makeText(getContext(),
                            e.getMessage() == null ? String.format(requireContext().getString(R.string.authentication_failed_s), e.getClass().getName()) : e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(MessageFragment.this).popBackStack();
                });
            }
        };
    }

    private final Consumer<byte[]> onTextMessage = bArr -> {
        var message = new String(bArr);

        revealHandler = () -> ((HiddenTextFragment) messageView).setText(reveal ? message : getString(R.string.hidden_text));
        ((OutputFragment) getChildFragmentManager().findFragmentById(R.id.messageOutputFragment))
                .setMessage(message, getString(R.string.oms_secret_message));
    };

    private final Consumer<byte[]> onTotpMessage = bArr -> {
        var message = new String(bArr);

        revealHandler = () -> ((TotpFragment) messageView).refresh();
        ((TotpFragment) messageView).init(new OneTimePassword(message), digits -> reveal ? null : "●".repeat(digits), code -> {
            var outputFragment = (OutputFragment) getChildFragmentManager().findFragmentById(R.id.messageOutputFragment);
            outputFragment.setMessage(code, getString(R.string.one_time_password));
        });
        ((TotpFragment) messageView).refresh();
    };

    private Consumer<BiometricPrompt.AuthenticationResult> getForByteArray(byte[] cipherText, Consumer<byte[]> andThen) {
        return result -> {
            var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();
            try {
                assert cipher != null;
                var aesSecretKeyData = cipher.doFinal(encryptedAesSecretKey);
                var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");
                var bArr = AESUtil.process(Cipher.DECRYPT_MODE, cipherText, aesSecretKey, new IvParameterSpec(iv), aesTransformation);

                andThen.accept(bArr);

                requireActivity().invalidateOptionsMenu();
            } catch (Exception e) {
                e.printStackTrace();
                requireContext().getMainExecutor().execute(() -> {
                    Toast.makeText(getContext(),
                            e.getMessage() == null ? String.format(requireContext().getString(R.string.authentication_failed_s), e.getClass().getName()) : e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    NavHostFragment.findNavController(MessageFragment.this).popBackStack();
                });
            }
        };
    }

    private void showBiometricPromptForDecryption(@Nullable String description) throws KeyStoreException {
        var cryptographyManager = new CryptographyManager();
        var aliases = cryptographyManager.getByFingerprint(fingerprint);

        if (aliases.isEmpty())
            throw new NoSuchElementException(String.format(getString(R.string.no_key_found), Util.byteArrayToHex(fingerprint)));

        if (aliases.size() > 1)
            throw new NoSuchElementException(getString(R.string.multiple_keys_found));

        var biometricPrompt = new BiometricPrompt(this, authenticationCallback);
        var alias = aliases.get(0);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.prompt_info_title))
                .setSubtitle(String.format(getString(R.string.prompt_info_subtitle), alias))
                .setDescription(Objects.requireNonNullElse(description, getString(R.string.prompt_info_description)))
                .setNegativeButtonText(getString(android.R.string.cancel))
                .setConfirmationRequired(false)
                .build();

        var cipher = new CryptographyManager().getInitializedCipherForDecryption(
                alias, rsaTransformation);

        biometricPrompt.authenticate(
                promptInfo,
                new BiometricPrompt.CryptoObject(cipher));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        binding = null;
    }

    private class MessageMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_message, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            MenuProvider.super.onPrepareMenu(menu);
            menu.findItem(R.id.menuItemMsgVisibility).setVisible(revealHandler != null);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemMsgVisibility) {
                if (revealHandler == null) return true;

                reveal = !reveal;
                menuItem.setIcon(reveal ? R.drawable.baseline_visibility_off_24 : R.drawable.baseline_visibility_24);
                revealHandler.run();
            } else if (menuItem.getItemId() == R.id.menuItemMsgHelp) {
                Util.openUrl(R.string.decrypted_message_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }
    }
}