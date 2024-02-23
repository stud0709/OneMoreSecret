package com.onemoresecret.msg_fragment_plugins;

import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;

import com.onemoresecret.FileInfoFragment;
import com.onemoresecret.FileOutputFragment;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OmsFileProvider;
import com.onemoresecret.R;
import com.onemoresecret.Util;
import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.MessageComposer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MsgPluginEncryptedFile extends MessageFragmentPlugin<Uri> {
    private final String filename;
    private final int filesize;
    private final Uri uri;

    public MsgPluginEncryptedFile(MessageFragment messageFragment,
                                  Uri messageData,
                                  String filename,
                                  int filesize) throws Exception {
        super(messageFragment);
        this.filename = filename;
        this.filesize = filesize;
        this.uri = messageData;

        try (InputStream is = context.getContentResolver().openInputStream(uri);
             var dataInputStream = new OmsDataInputStream(is)) {
            //read file header
            var rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream);
            rsaTransformation = rsaAesEnvelope.rsaTransormation();
            fingerprint = rsaAesEnvelope.fingerprint();
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

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {

        var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();

        try (var is = context.getContentResolver().openInputStream(uri);
             var dataInputStream = new OmsDataInputStream(is)) {

            assert cipher != null;

            //re-read header to get to the start position of the encrypted data
            var rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream);

            var aesSecretKeyData = cipher.doFinal(rsaAesEnvelope.encryptedAesSecretKey());
            var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");

            try {
                var oFileRecord = OmsFileProvider.create(context,
                        filename.substring(0, filename.length() - (MessageComposer.OMS_FILE_TYPE.length() + 1 /*the dot*/)),
                        true);

                try (FileOutputStream fos = new FileOutputStream(oFileRecord.path().toFile())) {
                    AESUtil.process(Cipher.DECRYPT_MODE, dataInputStream,
                            fos,
                            aesSecretKey,
                            new IvParameterSpec(rsaAesEnvelope.iv()),
                            rsaAesEnvelope.aesTransformation());
                }

                ((FileOutputFragment) outputView).setUri(oFileRecord.uri());
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
                Util.discardBackStack(messageFragment);
            });
        }
    }
}
