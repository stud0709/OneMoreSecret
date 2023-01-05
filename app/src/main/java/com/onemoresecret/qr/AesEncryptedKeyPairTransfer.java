package com.onemoresecret.qr;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.R;
import com.onemoresecret.crypto.AESUtil;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Consumer;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class AesEncryptedKeyPairTransfer implements MessageProcessorApplication<Void> {
    private static final String TAG = AesEncryptedKeyPairTransfer.class.getSimpleName();

    @Override
    public void processData(String message, Context ctx, Consumer<Void> onSuccess, Consumer<Exception> onException) {
        String sArr[] = message.split("\t");

        try  {
            //(1) Application ID
            int applicationId = Integer.parseInt(sArr[0]);
            if (applicationId != MessageProcessorApplication.APPLICATION_AES_ENCRYPTED_KEY_PAIR_TRANSFER)
                throw new IllegalArgumentException("wrong applicationId: " + applicationId);

            //(2) alias
            String alias = sArr[1];
            Log.d(TAG, "alias: " + alias);

            // --- AES parameter ---

            //(3) salt
            byte[] salt = Base64.getDecoder().decode(sArr[2]);
            Log.d(TAG, "salt: " + BluetoothController.byteArrayToHex(salt));

            //(4) IV
            byte[] iv = Base64.getDecoder().decode(sArr[3]);
            Log.d(TAG, "IV: " + BluetoothController.byteArrayToHex(iv));

            //(5) AES transformation
            String aesTransformation = sArr[4];
            Log.d(TAG, "cipher algorithm: " + aesTransformation);

            //(6) key algorithm
            String aesKeyAlg = sArr[5];
            Log.d(TAG, "AES key algorithm: " + aesKeyAlg);

            //(7) key length
            int aesKeyLength = Integer.parseInt(sArr[6]);
            Log.d(TAG, "AES key length: " + aesKeyLength);

            //(8) AES iterations
            int iterations = Integer.parseInt(sArr[7]);
            Log.d(TAG, "iterations: " + iterations);

            //(9) cipher text
            byte[] cipherText = Base64.getDecoder().decode(sArr[7]);
            Log.d(TAG, Integer.toString(cipherText.length) + " bytes cipher text read");

            //todo: dummy code! Call activity asking for password passing all the parts
            ContextCompat.getMainExecutor(ctx).execute(() -> {
                try {
                    onPasswordEntry(salt,
                            iv,
                            cipherText,
                            ctx.getString(R.string.dummy_password),
                            aesTransformation,
                            aesKeyAlg,
                            aesKeyLength,
                            iterations,
                            alias,
                            ctx);
                } catch (Exception e) {
                    //todo: dummy!
                    e.printStackTrace();
                }
            });
        } catch (Exception ex) {
            onException.accept(ex);
        }
    }

    public static void onPasswordEntry(
            byte[] salt,
            byte[] iv,
            byte[] encrypted,
            String password,
            String aesTransformation,
            String keyAlg,
            int keyLength,
            int iterations,
            String keyAlias,
            Context context) throws Exception {

        //try decrypt
        SecretKey secretKey = AESUtil.getKeyFromPassword(password, salt, keyAlg, keyLength, iterations);
        byte[] data = AESUtil.decrypt(encrypted, secretKey, new IvParameterSpec(iv),aesTransformation);
        String s = new String(data);
        String sArr[] = s.split("\t");

        //(1) RSA Key
        byte[] rsaKey = Base64.getDecoder().decode(sArr[0]);

        //(2) RSA Certificate
        byte[] rsaCert = Base64.getDecoder().decode(sArr[1]);

        //(3) hash
        byte[] _hash = Base64.getDecoder().decode(sArr[2]);

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(rsaKey);
        byte[] hash = sha256.digest(rsaCert);

        //compare hash values
        if (!Arrays.equals(hash, _hash)) {
            throw new IllegalArgumentException("Could not confirm data integrity");
        }

        new CryptographyManager().importKey(keyAlias, rsaKey, rsaCert);
        Toast.makeText(context, "Private key '" + keyAlias + "' successfully imported", Toast.LENGTH_LONG).show();

    }
}
