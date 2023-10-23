package com.onemoresecret.msg_fragment_plugins;

import android.util.Log;
import android.widget.Toast;

import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.HiddenTextFragment;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.Util;
import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.AesTransformation;
import com.onemoresecret.crypto.RsaTransformation;
import com.onemoresecret.databinding.FragmentMessageBinding;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MsgPluginEncryptedMessage extends MessageFragmentPlugin<byte[]> {

    protected byte[] encryptedAesSecretKey, iv, cipherText;
    private String aesTransformation;

    public MsgPluginEncryptedMessage(MessageFragment messageFragment,
                                     OutputFragment outputFragment,
                                     FragmentMessageBinding binding,
                                     byte[] messageData) throws IOException {

        super(messageFragment, outputFragment, binding, messageData);
    }

    @Override
    protected void init(byte[] messageData) throws IOException {
        try (var bais = new ByteArrayInputStream(messageData);
             var dataInputStream = new OmsDataInputStream(bais)) {

            //(1) Application ID
            var applicationId = dataInputStream.readUnsignedShort();

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
        }
    }

    protected void afterDecrypt(byte[] bArr) {
        var message = new String(bArr);

        var hiddenTextFragment = (HiddenTextFragment) messageView;
        messageFragment.getHiddenState().observe(hiddenTextFragment, hidden -> hiddenTextFragment.setText(hidden ? context.getString(R.string.hidden_text) : message));
        outputFragment.setMessage(message, context.getString(R.string.oms_secret_message));
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new HiddenTextFragment();
        return messageView;
    }

    @Override
    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();
        try {
            assert cipher != null;
            var aesSecretKeyData = cipher.doFinal(encryptedAesSecretKey);
            var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");
            var bArr = AESUtil.process(Cipher.DECRYPT_MODE, cipherText,
                    aesSecretKey,
                    new IvParameterSpec(iv),
                    aesTransformation);

            afterDecrypt(bArr);

            activity.invalidateOptionsMenu();
        } catch (Exception e) {
            e.printStackTrace();
            context.getMainExecutor().execute(() -> {
                Toast.makeText(activity,
                        e.getMessage() == null ? String.format(context.getString(R.string.authentication_failed_s), e.getClass().getName()) : e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(messageFragment).popBackStack();
            });
        }
    }
}
