package com.onemoresecret.msg_fragment_plugins;

import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MsgPluginEncryptedFile extends MessageFragmentPlugin {
    private final Util.UriFileInfo fileInfo;
    private final Uri uri;
    private int lastProgressPrc;
    private boolean destroyed;

    public MsgPluginEncryptedFile(MessageFragment messageFragment,
                                  Uri uri) throws Exception {
        super(messageFragment);
        this.fileInfo = Util.getFileInfo(context, uri);
        this.uri = uri;

        try (InputStream is = context.getContentResolver().openInputStream(this.uri);
             var dataInputStream = new OmsDataInputStream(is)) {
            //read file header
            var rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream);
            rsaTransformation = rsaAesEnvelope.rsaTransormation;
            fingerprint = rsaAesEnvelope.fingerprint;
        }
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) {
            var fileInfoFragment = new FileInfoFragment();
            messageView = fileInfoFragment;
            context.getMainExecutor().execute(() -> fileInfoFragment.setValues(fileInfo.filename, fileInfo.fileSize));
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
        new Thread(() -> {
            var cipher = Objects.requireNonNull(result.getCryptoObject()).getCipher();

            try (var is = context.getContentResolver().openInputStream(uri);
                 var dataInputStream = new OmsDataInputStream(is)) {

                assert cipher != null;

                //re-read header to get to the start position of the encrypted data
                var rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream);

                var aesSecretKeyData = cipher.doFinal(rsaAesEnvelope.encryptedAesSecretKey);
                var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");
                lastProgressPrc = -1;

                try {
                    var fileRecord = OmsFileProvider.create(context,
                            fileInfo.filename.substring(0, fileInfo.filename.length() - (MessageComposer.OMS_FILE_TYPE.length() + 1 /*the dot*/)),
                            true);

                    try (FileOutputStream fos = new FileOutputStream(fileRecord.path.toFile())) {
                        AESUtil.process(Cipher.DECRYPT_MODE, dataInputStream,
                                fos,
                                aesSecretKey,
                                new IvParameterSpec(rsaAesEnvelope.iv),
                                rsaAesEnvelope.aesTransformation,
                                () -> destroyed,
                                this::updateProgress);
                    }

                    if (destroyed) {
                        Files.delete(fileRecord.path);
                    } else {
                        updateProgress(fileInfo.fileSize); //100%
                        ((FileOutputFragment) outputView).setUri(fileRecord.uri);
                    }
                } catch (Exception ex) {
                    Util.printStackTrace(ex);
                    activity.getMainExecutor().execute(() -> Toast.makeText(context,
                            String.format("%s: %s", ex.getClass().getSimpleName(), ex.getMessage()),
                            Toast.LENGTH_LONG).show());
                }

                //requireActivity().invalidateOptionsMenu();
            } catch (Exception e) {
                Util.printStackTrace(e);
                context.getMainExecutor().execute(() -> {
                    Toast.makeText(context,
                            e.getMessage() == null ? String.format(context.getString(R.string.authentication_failed_s), e.getClass().getName()) : e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    Util.discardBackStack(messageFragment);
                });
            }
        }).start();
    }

    private void updateProgress(@Nullable Integer value) {
        String s = "";
        if (value != null) {
            var progressPrc = (int) ((double) value / (double) fileInfo.fileSize * 100D);
            if (lastProgressPrc == progressPrc) return;

            lastProgressPrc = progressPrc;
            s = lastProgressPrc == 100 ?
                    context.getString(R.string.done) :
                    String.format(Locale.getDefault(), context.getString(R.string.working_prc), lastProgressPrc);
        }

        ((FileOutputFragment) outputView).setProgress(s);
    }

    @Override
    public void onDestroyView() {
        destroyed = true;
    }
}
