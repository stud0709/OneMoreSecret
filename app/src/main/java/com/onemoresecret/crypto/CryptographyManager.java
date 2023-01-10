package com.onemoresecret.crypto;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;

import com.onemoresecret.R;

import org.bouncycastle.operator.OperatorCreationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;


public class CryptographyManager {
    public static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TAG = CryptographyManager.class.getSimpleName();
    public final KeyStore keyStore;

    public CryptographyManager() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, KeyStoreException {
        KeyStore _keyStore;

        try {
            _keyStore = KeyStore.getInstance(CryptographyManager.ANDROID_KEYSTORE);
        } catch (Exception ex) {
            ex.printStackTrace();
            keyStore = null;
            return;
        }

        try {
            _keyStore.load(null); // Keystore must be loaded before it can be accessed
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        keyStore = _keyStore;
    }

    public Cipher getInitializedCipherForEncryption(String keyName, String rsaTransformation) throws
            KeyStoreException,
            NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidKeyException {

        Cipher cipher = getCipher(rsaTransformation);
        cipher.init(Cipher.ENCRYPT_MODE, keyStore.getCertificate(keyName).getPublicKey());
        return cipher;
    }

    public Cipher getInitializedCipherForDecryption(String keyName, String transformation) {
        try {
            Cipher cipher = getCipher(transformation);
            PrivateKey secretKey = getPrivateKey(keyName);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    private static Cipher getCipher(String transformation) throws NoSuchPaddingException, NoSuchAlgorithmException {
        return Cipher.getInstance(transformation);
    }

    /**
     * If SecretKey was previously created for that keyName, then grab and return it.
     *
     * @param keyName
     * @return
     * @throws KeyStoreException
     * @throws CertificateException
     * @throws IOException
     * @throws NoSuchAlgorithmException
     * @throws UnrecoverableKeyException
     */
    public PrivateKey getPrivateKey(String keyName) throws
            KeyStoreException,
            NoSuchAlgorithmException,
            UnrecoverableKeyException {

        Key key = keyStore.getKey(keyName, null);

        if (key != null) {
            PrivateKey privateKey = (PrivateKey) key;
            return privateKey;
        }

        return null;
    }

    public Certificate getCertificate(String keyName) throws KeyStoreException {
        return keyStore.getCertificate(keyName);
    }


    /**
     * Creates a key pair in AndroidKeyStore. The key material will not be accessible!
     *
     * @return new {@link KeyPair}
     */
    public KeyPair createKeyPair(KeyGenParameterSpec spec) throws
            InvalidAlgorithmParameterException,
            NoSuchAlgorithmException,
            NoSuchProviderException {

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE);

        keyPairGenerator.initialize(spec);
        KeyPair kp = keyPairGenerator.generateKeyPair();
        return kp;
    }

    public KeyGenParameterSpec.Builder getDefaultSpecBuilder(String keyName) {
        return new KeyGenParameterSpec.Builder(
                keyName,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(2048)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1);
    }

    public static X509Certificate createCertificate(byte[] certificateData) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificateData));
    }

    public static RSAPrivateCrtKey createPrivateKey(byte[] keyMaterial) throws NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyMaterial);
        return (RSAPrivateCrtKey) keyFactory.generatePrivate(keySpec);
    }

    /**
     * Import RSA key pair using encoded key data from {@link PrivateKey#getEncoded()} and {@link PublicKey#getEncoded()}.
     *
     * @param privateKey
     * @param certificate if {@code null}, the private key will be signed by the generated self-signed certificate.
     * @param keyName
     * @throws CertificateException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeySpecException
     * @throws KeyStoreException
     * @throws IOException
     */
    public void importKey(String keyName, @NonNull PrivateKey privateKey, X509Certificate certificate) throws
            CertificateException,
            NoSuchAlgorithmException,
            InvalidKeySpecException,
            KeyStoreException,
            IOException,
            OperatorCreationException {

        if (certificate == null) {
            //create self-signed certificate
            PublicKey publicKey = createPublicFromPrivateKey((RSAPrivateCrtKey) privateKey);
            KeyPair keyPair = new KeyPair(publicKey, privateKey);
            certificate = SelfSignedCertGenerator.generate(keyPair, "SHA256withRSA", "OneMoreSecret", 730);
        }

        KeyStore.PrivateKeyEntry privateKeyEntry = new KeyStore.PrivateKeyEntry(privateKey, new Certificate[]{certificate});

        KeyProtection keyProtection = new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setUserAuthenticationRequired(true)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                //.setIsStrongBoxBacked(true)
                .build();

        keyStore.setEntry(keyName, privateKeyEntry, keyProtection);

        Log.d(TAG, "Private key '" + keyName + "' successfully imported. Certificate: " + certificate.getSerialNumber());
    }

    public static PublicKey createPublicFromPrivateKey(RSAPrivateCrtKey privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        //https://gist.github.com/manzke/1068441
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
    }

    public void deleteKey(String keyName) throws
            KeyStoreException {
        keyStore.deleteEntry(keyName);
    }

    /**
     * Resolve key by its modulus and public exponent
     *
     * @param fingerprint SHA-256 hash of modulus and public exponent, {@link CryptographyManager#getFingerprint(RSAPublicKey)}
     * @return all key alias matching that SHA-256
     * @throws KeyStoreException
     */
    public List<String> getByFingerprint(byte[] fingerprint) throws
            KeyStoreException {
        List<String> result = new ArrayList<>();

        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();

            try {
                X509Certificate cert = (X509Certificate) getCertificate(alias);
                RSAPublicKey publicKey = (RSAPublicKey) cert.getPublicKey();
                byte[] _fingerprint = getFingerprint(publicKey);

                if (Arrays.equals(fingerprint, _fingerprint)) {
                    result.add(alias);
                }
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }

        return result;
    }

    public static byte[] getFingerprint(RSAPrivateCrtKey privateKey) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(privateKey.getModulus().toByteArray());
        return sha256.digest(privateKey.getPublicExponent().toByteArray());
    }

    public static byte[] getFingerprint(RSAPublicKey publicKey) throws NoSuchAlgorithmException {
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        sha256.update(publicKey.getModulus().toByteArray());
        return sha256.digest(publicKey.getPublicExponent().toByteArray());
    }


}

