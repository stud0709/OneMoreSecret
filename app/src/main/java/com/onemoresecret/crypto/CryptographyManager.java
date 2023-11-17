package com.onemoresecret.crypto;

import android.content.Context;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Log;

import androidx.annotation.NonNull;

import org.spongycastle.crypto.generators.RSAKeyPairGenerator;
import org.spongycastle.crypto.params.RSAKeyGenerationParameters;
import org.spongycastle.crypto.util.PrivateKeyInfoFactory;
import org.spongycastle.crypto.util.SubjectPublicKeyInfoFactory;
import org.spongycastle.jcajce.provider.asymmetric.util.PrimeCertaintyCalculator;
import org.spongycastle.operator.OperatorCreationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;


public class CryptographyManager {
    public static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TAG = CryptographyManager.class.getSimpleName();
    @NonNull
    public final KeyStore keyStore;

    public static final String[] ENCRYPTION_PADDINGS = {
            KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
            KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
    };

    public static final int DEFAULT_DAYS_VALID = 9999;

    public CryptographyManager() {
        try {
            keyStore = KeyStore.getInstance(CryptographyManager.ANDROID_KEYSTORE);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        try {
            keyStore.load(null); // Keystore must be loaded before it can be accessed
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public Cipher getInitializedCipherForEncryption(String keyName, String rsaTransformation) throws
            KeyStoreException,
            NoSuchPaddingException,
            NoSuchAlgorithmException,
            InvalidKeyException {

        var cipher = Cipher.getInstance(rsaTransformation);
        cipher.init(Cipher.ENCRYPT_MODE, keyStore.getCertificate(keyName).getPublicKey());
        return cipher;
    }

    public Cipher getInitializedCipherForDecryption(String keyName, String transformation) {
        try {
            var cipher = Cipher.getInstance(transformation);
            var secretKey = Objects.requireNonNull(getPrivateKey(keyName));
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return cipher;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * If SecretKey was previously created for that keyName, then grab and return it.
     */
    public PrivateKey getPrivateKey(String keyName) throws
            KeyStoreException,
            NoSuchAlgorithmException,
            UnrecoverableKeyException {

        var key = keyStore.getKey(keyName, null);
        return key == null ? null : (PrivateKey) key;
    }

    public Certificate getCertificate(String keyName) throws KeyStoreException {
        return keyStore.getCertificate(keyName);
    }

    public KeyPair generateKeyPair(KeyGenParameterSpec spec) throws
            InvalidAlgorithmParameterException,
            NoSuchAlgorithmException,
            NoSuchProviderException {

        var keyPairGenerator = KeyPairGenerator.
                getInstance(
                        KeyProperties.KEY_ALGORITHM_RSA,
                        ANDROID_KEYSTORE
                );

        keyPairGenerator.initialize(spec);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Generate key pair by means of the BouncyCastle library to have access to the key material.
     */
    public static KeyPair generateKeyPair(int keyLength)
            throws IOException,
            NoSuchAlgorithmException,
            InvalidKeySpecException {

        var rsaKeyPairGenerator = new RSAKeyPairGenerator();
        
        rsaKeyPairGenerator.init(new RSAKeyGenerationParameters(
                BigInteger.valueOf(0x10001),
                new SecureRandom(),
                keyLength,
                PrimeCertaintyCalculator.getDefaultCertainty(keyLength)));

        var asymmetricCipherKeyPair = rsaKeyPairGenerator.generateKeyPair();
        var privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(asymmetricCipherKeyPair.getPrivate());
        var privateKeyMaterial = privateKeyInfo.getEncoded();
        var publicKeyMaterial = SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(asymmetricCipherKeyPair.getPublic()).getEncoded();
        return restoreKeyPair(privateKeyMaterial, publicKeyMaterial);
    }

    public static X509Certificate restoreCertificate(byte[] certificateData) throws
            CertificateException {
        var cf = CertificateFactory.getInstance("X509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificateData));
    }

    public static KeyPair restoreKeyPair(byte[] privateKeyMaterial, byte[] publicKeyMaterial) throws
            NoSuchAlgorithmException,
            InvalidKeySpecException {
        var keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA);
        var keySpec = new PKCS8EncodedKeySpec(privateKeyMaterial);
        var privateKey = keyFactory.generatePrivate(keySpec);
        var publicKeySpec = new X509EncodedKeySpec(publicKeyMaterial);
        var publicKey = keyFactory.generatePublic(publicKeySpec);
        return new KeyPair(publicKey, privateKey);
    }

    /**
     * Import RSA key pair using encoded key data from {@link PrivateKey#getEncoded()} and {@link PublicKey#getEncoded()}.
     */
    public void importKey(String keyName, KeyPair keyPair, Context ctx) throws
            CertificateException,
            KeyStoreException,
            IOException,
            OperatorCreationException {

        //create self-signed certificate with the specified end validity
        var certificate = SelfSignedCertGenerator.generate(keyPair,
                "SHA256withRSA",
                "OneMoreSecret",
                DEFAULT_DAYS_VALID);


        KeyStore.PrivateKeyEntry privateKeyEntry = new KeyStore.PrivateKeyEntry(keyPair.getPrivate(), new Certificate[]{certificate});

        var keyProtection = new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setUserAuthenticationRequired(true)
                .setEncryptionPaddings(ENCRYPTION_PADDINGS)
                .setIsStrongBoxBacked(ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE))
                .build();

        keyStore.setEntry(keyName, privateKeyEntry, keyProtection);

        Log.d(TAG, "Private key '" + keyName + "' successfully imported. Certificate: " + certificate.getSerialNumber());
    }

    public void deleteKey(String keyName) throws
            KeyStoreException {
        keyStore.deleteEntry(keyName);
    }

    /**
     * Resolve key by its modulus and public exponent
     *
     * @param fingerprint SHA-256 hash of modulus and public exponent, {@link RSAUtils#getFingerprint(RSAPublicKey)}
     * @return all key alias matching that SHA-256
     */
    public List<String> getByFingerprint(byte[] fingerprint) throws
            KeyStoreException {
        List<String> result = new ArrayList<>();

        var aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            var alias = aliases.nextElement();

            try {
                var cert = (X509Certificate) getCertificate(alias);
                var publicKey = (RSAPublicKey) cert.getPublicKey();
                var _fingerprint = RSAUtils.getFingerprint(publicKey);

                if (Arrays.equals(fingerprint, _fingerprint)) {
                    result.add(alias);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return result;
    }

}

