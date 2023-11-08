package com.onemoresecret.msg_fragment_plugins;

import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.FileInfoFragment;
import com.onemoresecret.FileOutputFragment;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OmsFileProvider;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.Util;
import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.AesTransformation;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RsaTransformation;
import com.onemoresecret.databinding.FragmentMessageBinding;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MsgPluginEncryptedFile extends MessageFragmentPlugin<Uri> {
    private String aesTransformation;
    private byte[] encryptedAesSecretKey, iv;
    private final String filename;
    private final int filesize;
    private final Uri messageData;

    public MsgPluginEncryptedFile(MessageFragment messageFragment, Uri messageData, String filename, int filesize) throws IOException {
        super(messageFragment, messageData);
        this.filename = filename;
        this.filesize = filesize;
        this.messageData = messageData;
    }

    @Override
    protected void init(Uri messageData) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(messageData);
             var dataInputStream = new OmsDataInputStream(is)) {
            readHeaderUri(dataInputStream);
        }
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) {
            var fileInfoFragment = new FileInfoFragment();
            messageView = fileInfoFragment;
            context.getMainExecutor().execute(() -> fileInfoFragment.setValues(filename, filesize));
        }
        return messageView;
    }

    @Override
    public FragmentWithNotificationBeforePause getOutputView() {
        if (outputView == null)
            outputView = new FileOutputFragment();

        return outputView;
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

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {

        var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();

        try (var is = context.getContentResolver().openInputStream(messageData);
             var dataInputStream = new OmsDataInputStream(is)) {

            assert cipher != null;

            //re-read header to get to the start position of the encrypted data
            readHeaderUri(dataInputStream);

            var aesSecretKeyData = cipher.doFinal(encryptedAesSecretKey);
            var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");

            try {
                var oFileRecord = OmsFileProvider.create(context,
                        filename.substring(0, filename.length() - (MessageComposer.OMS_FILE_TYPE.length() + 1 /*the dot*/)),
                        true);

                try (FileOutputStream fos = new FileOutputStream(oFileRecord.path().toFile())) {
                    AESUtil.process(Cipher.DECRYPT_MODE, dataInputStream,
                            fos,
                            aesSecretKey,
                            new IvParameterSpec(iv),
                            aesTransformation);
                }

                ((FileOutputFragment)outputView).setUri(oFileRecord.uri());
            } catch (Exception ex) {
                ex.printStackTrace();
                activity.getMainExecutor().execute(() -> Toast.makeText(context,
                        String.format("%s: %s", ex.getClass().getSimpleName(), ex.getMessage()),
                        Toast.LENGTH_LONG).show());
            }

            //requireActivity().invalidateOptionsMenu();
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
