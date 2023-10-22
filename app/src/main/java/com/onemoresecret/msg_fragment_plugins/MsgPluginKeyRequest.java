package com.onemoresecret.msg_fragment_plugins;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.HiddenTextFragment;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.crypto.RsaTransformation;
import com.onemoresecret.databinding.FragmentMessageBinding;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.crypto.Cipher;

public class MsgPluginKeyRequest extends MessageFragmentPlugin<byte[]> {
    protected String description;
    protected PublicKey rsaPublicKey;
    protected final SharedPreferences preferences;
    protected byte[] cipherText;

    public MsgPluginKeyRequest(MessageFragment messageFragment, OutputFragment outputFragment, FragmentMessageBinding binding, byte[] messageData) throws IOException {
        super(messageFragment, outputFragment, binding, messageData);
        preferences = activity.getPreferences(Context.MODE_PRIVATE);
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new HiddenTextFragment();
        return messageView;
    }

    @Override
    public String getDescription() {
       return String.format(context.getString(R.string.reference), description);
    }

    @Override
    protected void init(byte[] messageData) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
             var dataInputStream = new OmsDataInputStream(bais)) {

            //(1) Application ID
            var applicationId = dataInputStream.readUnsignedShort();

            //(2) reference (e.g. file name)
            description = dataInputStream.readString();

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
    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();
        try {
            assert cipher != null;

            //decrypt AES key
            var aesKeyMaterial = cipher.doFinal(cipherText);

            //encrypt AES key with the provided public key
            var message = RSAUtils.process(Cipher.ENCRYPT_MODE, rsaPublicKey, RSAUtils.getRsaTransformation(preferences).transformation, aesKeyMaterial);

            var base64Message = Base64.getEncoder().encodeToString(message);

            var hiddenTextFragment = (HiddenTextFragment) messageView;
            hiddenTextFragment.setText("Ready to TYPE...");

            outputFragment.setMessage(base64Message, context.getString(R.string.oms_secret_message));

            activity.invalidateOptionsMenu();
        } catch (Exception e) {
            e.printStackTrace();
            context.getMainExecutor().execute(() -> {
                Toast.makeText(context,
                        e.getMessage() == null ? String.format(context.getString(R.string.authentication_failed_s), e.getClass().getName()) : e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(messageFragment).popBackStack();
            });
        }
    }
}
