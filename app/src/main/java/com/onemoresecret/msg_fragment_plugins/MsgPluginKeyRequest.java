package com.onemoresecret.msg_fragment_plugins;

import android.widget.Toast;

import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;

import com.onemoresecret.HiddenTextFragment;
import com.onemoresecret.KeyRequestPairingFragment;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OmsDataOutputStream;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.Util;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtil;
import com.onemoresecret.crypto.RsaTransformation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;

public class MsgPluginKeyRequest extends MessageFragmentPlugin {
    protected String reference;
    protected PublicKey rsaPublicKey;
    protected byte[] cipherText;
    protected int applicationId;

    public MsgPluginKeyRequest(MessageFragment messageFragment, byte[] messageData) throws Exception {
        super(messageFragment);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
             var dataInputStream = new OmsDataInputStream(bais)) {

            //(1) Application ID
            applicationId = dataInputStream.readUnsignedShort();

            assert Arrays.asList(MessageComposer.APPLICATION_KEY_REQUEST,
                    MessageComposer.APPLICATION_KEY_REQUEST_PAIRING).contains(applicationId);

            switch (applicationId) {
                case MessageComposer.APPLICATION_KEY_REQUEST -> {
                    //(2) reference (e.g. file name)
                    reference = dataInputStream.readString();

                    //(3) RSA public key
                    rsaPublicKey = RSAUtil.restorePublicKey(dataInputStream.readByteArray());

                    //(4) fingerprint of the requested key
                    fingerprint = dataInputStream.readByteArray();

                    //(5) transformation index for decryption
                    rsaTransformation = RsaTransformation.getEntries().get(dataInputStream.readUnsignedShort());

                    //(6) AES key subject to decryption with RSA key specified by fingerprint at (4)
                    cipherText = dataInputStream.readByteArray();
                }
                case MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                    // (2) reference (file name)
                    reference = dataInputStream.readString();

                    // (3) fingerprint of the requested RSA key (from the file header)
                    fingerprint = dataInputStream.readByteArray();

                    // (5) RSA transformation index for decryption
                    rsaTransformation = RsaTransformation.getEntries().get(dataInputStream.readUnsignedShort());

                    // (6) encrypted AES key from the file header
                    cipherText = dataInputStream.readByteArray();
                }
            }


        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) {
            messageView = new HiddenTextFragment();
            context.getMainExecutor().execute(() -> ((HiddenTextFragment) messageView).setText(""));
        }
        return messageView;
    }

    @Override
    public FragmentWithNotificationBeforePause getOutputView() {
        if (applicationId == MessageComposer.APPLICATION_KEY_REQUEST)
            return super.getOutputView();

        if (outputView == null) {
            outputView = new KeyRequestPairingFragment();
        }
        return outputView;
    }

    @Override
    public String getReference() {
        return String.format(context.getString(R.string.reference), reference);
    }

    @Override
    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        var masterRsaCipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();
        assert masterRsaCipher != null;

        var keyStoreEntry = new CryptographyManager().getByFingerprint(fingerprint, preferences);
        assert keyStoreEntry != null;

        var userRsaCipherBox = new CryptographyManager().getInitializedUserRsaCipher(
                masterRsaCipher,
                keyStoreEntry,
                rsaTransformation,
                Cipher.DECRYPT_MODE);

        try {
            //decrypt AES key
            var aesSecretKeyData = userRsaCipherBox.getCipher().doFinal(cipherText);
            userRsaCipherBox.getWipe().invoke(); //wipe User RSA key data

            switch (applicationId) {
                case MessageComposer.APPLICATION_KEY_REQUEST -> {//encrypt AES key with the provided public key
                    var rsaEncryptedAesKey = RSAUtil.process(
                            Cipher.ENCRYPT_MODE,
                            rsaPublicKey,
                            RSAUtil.getRsaTransformation(preferences),
                            aesSecretKeyData);

                    Arrays.fill(aesSecretKeyData, (byte)0); //wipe AES key data

                    try (var baos = new ByteArrayOutputStream();
                         var dataOutputStream = new OmsDataOutputStream(baos)) {

                        // (1) Application identifier
                        dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_KEY_RESPONSE);

                        // (2) RSA transformation
                        dataOutputStream.writeUnsignedShort(RSAUtil.getRsaTransformationIdx(preferences));

                        // (3) RSA encrypted AES key
                        dataOutputStream.writeByteArray(rsaEncryptedAesKey);

                        var base64Message = Base64.getEncoder().encodeToString(baos.toByteArray());

                        var hiddenTextFragment = (HiddenTextFragment) messageView;
                        hiddenTextFragment.setText(String.format(context.getString(R.string.key_response_is_ready), reference));

                        ((OutputFragment) outputView).setMessage(base64Message + "\n" /* hit ENTER at the end signalling omsCompanion to resume */, context.getString(R.string.key_response));

                        activity.invalidateOptionsMenu();
                    }
                }
                case MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                    var hiddenTextFragment = (HiddenTextFragment) messageView;
                    hiddenTextFragment.setText(String.format(context.getString(R.string.key_response_is_ready), reference));
                    try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                         OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {
                        dataOutputStream.writeByteArray(aesSecretKeyData);
                        ((KeyRequestPairingFragment) getOutputView()).setReply(baos.toByteArray());
                    }
                }
            }
        } catch (Exception e) {
            Util.printStackTrace(e);
            context.getMainExecutor().execute(() -> {
                Toast.makeText(context,
                        e.getMessage() == null ? String.format(context.getString(R.string.authentication_failed_s), e.getClass().getName()) : e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Util.discardBackStack(messageFragment);
            });
        }
    }
}
