package com.onemoresecret.crypto;

import android.util.Log;

import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OmsDataOutputStream;
import com.onemoresecret.Util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public abstract class MessageComposer {
    private static final String TAG = MessageComposer.class.getSimpleName();
    public static final int
            APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER = 0,
            APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED = 1,
            APPLICATION_TOTP_URI_DEPRECATED = 2,
            APPLICATION_ENCRYPTED_FILE = 3,
            APPLICATION_KEY_REQUEST = 4,
            APPLICATION_KEY_RESPONSE = 5,
    /**
     * Until now, it was possible to understand what kind of information is contained in the message.
     * The generic message will only allow to decrypt it, all other information will be found inside.
     */
    APPLICATION_RSA_AES_GENERIC = 6,
            APPLICATION_BITCOIN_ADDRESS = 7,
            APPLICATION_ENCRYPTED_MESSAGE = 8,
            APPLICATION_TOTP_URI = 9,
            APPLICATION_WIFI_PAIRING = 10;

    /**
     * Prefix of a text encoded message.
     */
    public static final String OMS_PREFIX = "oms00_";

    public static final String OMS_FILE_TYPE = "oms00";

    /**
     * Text encoded OMS messages begin with omsXX_ with XX being the protocol
     * version.
     */
    public static final Pattern OMS_PATTERN = Pattern.compile("oms([\\da-f]{2})_");

    /**
     * You can pass messages through the clipboard. A message begins with
     * {@link MessageComposer#OMS_PREFIX}. Version 00 of OMS protocol:
     * <ol>
     * <li>BASE64 encode {@code message}</li></li>prepend (1) with
     * {@link MessageComposer#OMS_PREFIX}
     * </ol>
     */
    public static String encodeAsOmsText(byte[] message) {
        return OMS_PREFIX + Base64.getEncoder().encodeToString(message);
    }

    public static byte[] decode(String omsText) {
        var m = OMS_PATTERN.matcher(omsText);

        if (!m.find()) {
            //TOT?
            if (new OneTimePassword(omsText).isValid()) {
                //this is a time based OTP, pass unchanged
                return omsText.getBytes();
            }
            // not a valid OMS message
            return null;
        }

        byte[] result;

        var version = Integer.parseInt(Objects.requireNonNull(m.group(1)));

        // (1) remove prefix and line breaks
        omsText = omsText.substring(m.group().length());
        omsText = omsText.replaceAll("\\s+", "");

        if (version == 0) {
            // (2) convert to byte array
            result = Base64.getDecoder().decode(omsText);
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + version);
        }

        return result;
    }

    public static byte[] createRsaAesEnvelope(RSAPublicKey rsaPublicKey,
                                              int rsaTransformationIdx,
                                              int aesKeyLength,
                                              int aesTransformationIdx,
                                              byte[] payload) throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            IllegalBlockSizeException,
            BadPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException {

        return createRsaAesEnvelope(MessageComposer.APPLICATION_RSA_AES_GENERIC,
                rsaPublicKey,
                rsaTransformationIdx,
                aesKeyLength,
                aesTransformationIdx,
                payload);
    }

    public static byte[] createRsaAesEnvelope(int applicationId,
                                              RSAPublicKey rsaPublicKey,
                                              int rsaTransformationIdx,
                                              int aesKeyLength,
                                              int aesTransformationIdx,
                                              byte[] payload) throws
            NoSuchAlgorithmException,
            NoSuchPaddingException,
            IllegalBlockSizeException,
            BadPaddingException,
            InvalidKeyException,
            InvalidAlgorithmParameterException {

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

            var aesEncryptionParameters = prepareRsaAesEnvelope(
                    dataOutputStream,
                    applicationId,
                    rsaPublicKey,
                    rsaTransformationIdx,
                    aesKeyLength,
                    aesTransformationIdx);

            // (7) AES-encrypted message
            dataOutputStream.writeByteArray(
                    AESUtil.process(
                            Cipher.ENCRYPT_MODE,
                            payload,
                            aesEncryptionParameters.secretKey(),
                            aesEncryptionParameters.iv(),
                            AesTransformation.values()[aesTransformationIdx].transformation));

            return baos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public record AesEncryptionParameters(SecretKey secretKey, IvParameterSpec iv) {
    }

    public static AesEncryptionParameters prepareRsaAesEnvelope(OmsDataOutputStream dataOutputStream,
                                                                int applicationId,
                                                                RSAPublicKey rsaPublicKey,
                                                                int rsaTransformationIdx,
                                                                int aesKeyLength,
                                                                int aesTransformationIdx) throws
            NoSuchAlgorithmException,
            IOException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {

        // init AES
        var iv = AESUtil.generateIv();
        var secretKey = AESUtil.generateRandomSecretKey(aesKeyLength);

        // encrypt AES secret key with RSA
        var cipher = Cipher.getInstance(RsaTransformation.values()[rsaTransformationIdx].transformation);
        cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey);

        var encryptedSecretKey = cipher.doFinal(secretKey.getEncoded());

        // (1) application-ID
        dataOutputStream.writeUnsignedShort(applicationId);

        // (2) RSA transformation index
        dataOutputStream.writeUnsignedShort(rsaTransformationIdx);

        // (3) fingerprint
        dataOutputStream.writeByteArray(RSAUtils.getFingerprint(rsaPublicKey));

        // (4) AES transformation index
        dataOutputStream.writeUnsignedShort(aesTransformationIdx);

        // (5) IV
        dataOutputStream.writeByteArray(iv.getIV());

        // (6) RSA-encrypted AES secret key
        dataOutputStream.writeByteArray(encryptedSecretKey);

        return new AesEncryptionParameters(secretKey, iv);
    }

    public record RsaAesEnvelope(int applicationId,
                                 String rsaTransormation,
                                 byte[] fingerprint,
                                 String aesTransformation,
                                 byte[] iv,
                                 byte[] encryptedAesSecretKey) {
    }

    public static RsaAesEnvelope readRsaAesEnvelope(OmsDataInputStream dataInputStream) throws IOException {
        //(1) Application ID
        var applicationId = dataInputStream.readUnsignedShort();

        //(2) RSA transformation index
        var rsaTransformation = RsaTransformation.values()[dataInputStream.readUnsignedShort()].transformation;
        Log.d(TAG, "RSA transformation: " + rsaTransformation);

        //(3) RSA fingerprint
        var fingerprint = dataInputStream.readByteArray();
        Log.d(TAG, "RSA fingerprint: " + Util.byteArrayToHex(fingerprint));

        // (4) AES transformation index
        var aesTransformation = AesTransformation.values()[dataInputStream.readUnsignedShort()].transformation;
        Log.d(TAG, "AES transformation: " + aesTransformation);

        //(5) IV
        var iv = dataInputStream.readByteArray();
        Log.d(TAG, "IV: " + Util.byteArrayToHex(iv));

        //(6) RSA-encrypted AES secret key
        var encryptedAesSecretKey = dataInputStream.readByteArray();

        //(7) AES-encrypted message <= leave here

        return new RsaAesEnvelope(applicationId, rsaTransformation, fingerprint, aesTransformation, iv, encryptedAesSecretKey);
    }
}
