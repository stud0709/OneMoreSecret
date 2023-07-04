package com.onemoresecret;

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
import com.onemoresecret.crypto.RsaTransformation;
import com.onemoresecret.databinding.FragmentMessageBinding;

import java.io.ByteArrayInputStream;
import java.util.NoSuchElementException;
import java.util.Objects;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MessageFragment extends Fragment {
    private static final String TAG = MessageFragment.class.getSimpleName();
    private byte[] cipherText, encryptedAesSecretKey, iv;
    private String rsaTransformation, aesTransformation;
    private FragmentMessageBinding binding;
    private boolean navBackOnResume = false;
    private boolean reveal = false;
    private Runnable revealHandler = null;
    private final MessageMenuProvider menuProvider = new MessageMenuProvider();
    private volatile boolean navBackIfPaused = true;
    private Fragment messageView = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMessageBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "fragment paused");

        if (navBackIfPaused) navBackOnResume = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        navBackIfPaused = true;

        if (navBackOnResume)
            NavHostFragment.findNavController(this).popBackStack();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);
        ((OutputFragment) binding.messageOutputFragment.getFragment()).setBeforePause(() -> navBackIfPaused = false);

        try (ByteArrayInputStream bais = new ByteArrayInputStream(requireArguments().getByteArray("MESSAGE"));
             var dataInputStream = new OmsDataInputStream(bais)) {

            //(1) Application ID
            var applicationId = dataInputStream.readUnsignedShort();
            switch (applicationId) {
                case MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER -> {
                    messageView = new HiddenTextFragment();
                    getChildFragmentManager().beginTransaction().add(R.id.fragmentMessageView, messageView).commit();
                }
                case MessageComposer.APPLICATION_TOTP_URI_TRANSFER -> {
                    messageView = new TotpFragment();
                    getChildFragmentManager().beginTransaction().add(R.id.fragmentMessageView, messageView).commit();
                }
                default ->
                        throw new IllegalArgumentException(getString(R.string.wrong_application) + " " + applicationId);
            }

            //(2) RSA transformation index
            rsaTransformation = RsaTransformation.values()[dataInputStream.readUnsignedShort()].transformation;
            Log.d(TAG, "RSA transformation: " + rsaTransformation);

            //(3) RSA fingerprint
            var fingerprint = dataInputStream.readByteArray();
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

            //******* decrypting ********

            var cryptographyManager = new CryptographyManager();
            var aliases = cryptographyManager.getByFingerprint(fingerprint);

            if (aliases.isEmpty())
                throw new NoSuchElementException(String.format(getString(R.string.no_key_found), Util.byteArrayToHex(fingerprint)));

            if (aliases.size() > 1)
                throw new NoSuchElementException(getString(R.string.multiple_keys_found));

            showBiometricPromptForDecryption(aliases.get(0));
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(getContext(), getString(R.string.wrong_message_format), Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    private void showBiometricPromptForDecryption(String alias) {

        BiometricPrompt.AuthenticationCallback authenticationCallback = new BiometricPrompt.AuthenticationCallback() {

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
                Log.d(TAG,
                        getString(R.string.auth_successful));

                var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();
                try {
                    assert cipher != null;
                    var aesSecretKeyData = cipher.doFinal(encryptedAesSecretKey);
                    var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");
                    var bArr = AESUtil.decrypt(cipherText, aesSecretKey, new IvParameterSpec(iv), aesTransformation);

                    onDecryptedData(bArr);
                } catch (Exception e) {
                    e.printStackTrace();
                    requireContext().getMainExecutor().execute(() -> {
                        Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                        NavHostFragment.findNavController(MessageFragment.this).popBackStack();
                    });
                }
            }
        };

        var biometricPrompt = new BiometricPrompt(this, authenticationCallback);

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.prompt_info_title))
                .setSubtitle(String.format(getString(R.string.prompt_info_subtitle), alias))
                .setDescription(getString(R.string.prompt_info_description))
                .setNegativeButtonText(getString(android.R.string.cancel))
                .setConfirmationRequired(false)
                .build();

        var cipher = new CryptographyManager().getInitializedCipherForDecryption(
                alias, rsaTransformation);

        biometricPrompt.authenticate(
                promptInfo,
                new BiometricPrompt.CryptoObject(cipher));
    }

    private void onDecryptedData(byte[] bArr) {
        var message = new String(bArr);

        if (messageView instanceof HiddenTextFragment) {
            var shareTitle = getString(R.string.oms_secret_message);
            revealHandler = () -> ((HiddenTextFragment) messageView).setText(reveal ? message : getString(R.string.hidden_text));
            var outputFragment = (OutputFragment) getChildFragmentManager().findFragmentById(R.id.messageOutputFragment);
            outputFragment.setMessage(message, shareTitle);
        }
        if (messageView instanceof TotpFragment) {
            revealHandler = () -> ((TotpFragment) messageView).refresh();
            var shareTitle = getString(R.string.one_time_password);
            ((TotpFragment) messageView).init(new OneTimePassword(message), digits -> reveal ? null : "●".repeat(digits), code -> {
                var outputFragment = (OutputFragment) getChildFragmentManager().findFragmentById(R.id.messageOutputFragment);
                outputFragment.setMessage(code, shareTitle);
            });
            ((TotpFragment) messageView).refresh();
        }
        requireActivity().invalidateOptionsMenu();
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