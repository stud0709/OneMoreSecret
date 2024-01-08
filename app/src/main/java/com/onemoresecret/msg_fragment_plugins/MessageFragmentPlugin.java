package com.onemoresecret.msg_fragment_plugins;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.MessageFragment;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.Util;
import com.onemoresecret.crypto.CryptographyManager;

import java.io.IOException;
import java.security.KeyStoreException;
import java.util.NoSuchElementException;
import java.util.Objects;

public abstract class MessageFragmentPlugin<T> extends BiometricPrompt.AuthenticationCallback {
    protected final MessageFragment messageFragment;
    protected final Context context;
    protected final FragmentActivity activity;
    protected byte[] fingerprint;
    protected String rsaTransformation;
    protected final String TAG = getClass().getSimpleName();
    protected Fragment messageView;
    protected FragmentWithNotificationBeforePause outputView;

    public MessageFragmentPlugin(MessageFragment messageFragment,
                                 T messageData) throws IOException {
        this.messageFragment = messageFragment;
        this.context = messageFragment.requireContext();
        this.activity = messageFragment.requireActivity();
        init(messageData);
    }

    protected abstract void init(T messageData) throws IOException;

    public abstract Fragment getMessageView();

    public FragmentWithNotificationBeforePause getOutputView() {
        if (outputView == null) outputView = new OutputFragment();
        return outputView;
    }

    protected String getDescription() {
        return null;
    }

    public void showBiometricPromptForDecryption() throws KeyStoreException {
        var cryptographyManager = new CryptographyManager();
        var aliases = cryptographyManager.getByFingerprint(fingerprint);

        if (aliases.isEmpty())
            throw new NoSuchElementException(String.format(context.getString(R.string.no_key_found), Util.byteArrayToHex(fingerprint)));

        if (aliases.size() > 1)
            throw new NoSuchElementException(context.getString(R.string.multiple_keys_found));

        var biometricPrompt = new BiometricPrompt(activity, this);
        var alias = aliases.get(0);

        var promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.prompt_info_title))
                .setSubtitle(String.format(context.getString(R.string.prompt_info_subtitle), alias))
                .setDescription(Objects.requireNonNullElse(getDescription(), context.getString(R.string.prompt_info_description)))
                .setNegativeButtonText(context.getString(android.R.string.cancel))
                .setConfirmationRequired(false)
                .build();

        var cipher = new CryptographyManager().getInitializedCipherForDecryption(
                alias, rsaTransformation);

        context.getMainExecutor().execute(() -> {
            biometricPrompt.authenticate(
                    promptInfo,
                    new BiometricPrompt.CryptoObject(cipher));
        });
    }


    @Override
    public void onAuthenticationError(int errCode, @NonNull CharSequence errString) {
        Log.d(TAG, String.format("Authentication failed: %s (%s)", errString, errCode));
        context.getMainExecutor().execute(() -> {
            Toast.makeText(context, errString + " (" + errCode + ")", Toast.LENGTH_SHORT).show();
            Util.discardBackStack(messageFragment);
        });
    }

    @Override
    public void onAuthenticationFailed() {
        Log.d(TAG,
                "User biometrics rejected");
        context.getMainExecutor().execute(() -> {
            Toast.makeText(context, context.getString(R.string.auth_failed), Toast.LENGTH_SHORT).show();
            Util.discardBackStack(messageFragment);
        });
    }
}
