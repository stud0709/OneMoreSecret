package com.onemoresecret.msg_fragment_plugins;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;

import com.onemoresecret.HiddenTextFragment;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OmsDataOutputStream;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.Util;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.crypto.RsaTransformation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;

public class MsgPluginKeyRequest extends MessageFragmentPlugin<byte[]> {
    protected String reference;
    protected PublicKey rsaPublicKey;
    protected byte[] cipherText;

    public MsgPluginKeyRequest(MessageFragment messageFragment, byte[] messageData) throws Exception {
        super(messageFragment);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
             var dataInputStream = new OmsDataInputStream(bais)) {

            //(1) Application ID
            var applicationId = dataInputStream.readUnsignedShort();
            assert applicationId == MessageComposer.APPLICATION_KEY_REQUEST;

            //(2) reference (e.g. file name)
            reference = dataInputStream.readString();

            //(3) RSA public key
            rsaPublicKey = RSAUtils.restorePublicKey(dataInputStream.readByteArray());

            //(4) fingerprint of the requested key
            fingerprint = dataInputStream.readByteArray();

            //(5) transformation index for decryption
            rsaTransformation = RsaTransformation.values()[dataInputStream.readUnsignedShort()].transformation;

            //(6) AES key subject to decryption with RSA key specified by fingerprint at (4)
            cipherText = dataInputStream.readByteArray();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new HiddenTextFragment();
        return messageView;
    }

    @Override
    public String getReference() {
        return String.format(context.getString(R.string.reference), reference);
    }

    @Override
    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();
        try {
            assert cipher != null;

            //decrypt AES key
            var aesKeyMaterial = cipher.doFinal(cipherText);

            //encrypt AES key with the provided public key
            var rsaEncryptedAesKey = RSAUtils.process(Cipher.ENCRYPT_MODE, rsaPublicKey, RSAUtils.getRsaTransformation(preferences).transformation, aesKeyMaterial);

            try (var baos = new ByteArrayOutputStream();
                 var dataOutputStream = new OmsDataOutputStream(baos)) {

                // (1) Application identifier
                dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_KEY_RESPONSE);

                // (2) RSA transformation
                dataOutputStream.writeUnsignedShort(RSAUtils.getRsaTransformationIdx(preferences));

                // (3) RSA encrypted AES key
                dataOutputStream.writeByteArray(rsaEncryptedAesKey);

                var base64Message = Base64.getEncoder().encodeToString(baos.toByteArray());

                var hiddenTextFragment = (HiddenTextFragment) messageView;
                hiddenTextFragment.setText(String.format(context.getString(R.string.key_response_is_ready), reference));

                ((OutputFragment) outputView).setMessage(base64Message + "\n" /* hit ENTER at the end signalling omsCompanion to resume */, context.getString(R.string.key_response));

                activity.invalidateOptionsMenu();
            }
        } catch (Exception e) {
            e.printStackTrace();
            context.getMainExecutor().execute(() -> {
                Toast.makeText(context,
                        e.getMessage() == null ? String.format(context.getString(R.string.authentication_failed_s), e.getClass().getName()) : e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Util.discardBackStack(messageFragment);
            });
        }
    }
}
