package com.onemoresecret.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Log;

import androidx.annotation.NonNull;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory;
import org.bouncycastle.jcajce.provider.asymmetric.util.PrimeCertaintyCalculator;
import org.bouncycastle.operator.OperatorCreationException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
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
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

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
            ex.printStackTrace();
        }
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
     */
    public PrivateKey getPrivateKey(String keyName) throws
            KeyStoreException,
            NoSuchAlgorithmException,
            UnrecoverableKeyException {

        Key key = keyStore.getKey(keyName, null);

        if (key != null) {
            return (PrivateKey) key;
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
     * @see CryptographyManager#getDefaultSpecBuilder(String, SharedPreferences)
     */
    public KeyPair generateKeyPair(KeyGenParameterSpec spec) throws
            InvalidAlgorithmParameterException,
            NoSuchAlgorithmException,
            NoSuchProviderException {

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.
                getInstance(
                        KeyProperties.KEY_ALGORITHM_RSA,
                        ANDROID_KEYSTORE
                );

        keyPairGenerator.initialize(spec);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Generate private key material by means of the BouncyCastle library.
     *
     * @return key material
     * @throws IOException
     */
    public static byte[] generatePrivateKeyMaterial(SharedPreferences preferences) throws IOException {
        int keyLength = RSAUtils.getKeyLength(preferences);
        RSAKeyPairGenerator rsaKeyPairGenerator = new RSAKeyPairGenerator();
        rsaKeyPairGenerator.init(new RSAKeyGenerationParameters(
                BigInteger.valueOf(0x10001),
                new SecureRandom(),
                keyLength,
                PrimeCertaintyCalculator.getDefaultCertainty(keyLength)));
        AsymmetricCipherKeyPair asymmetricCipherKeyPair = rsaKeyPairGenerator.generateKeyPair();
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfoFactory.createPrivateKeyInfo(asymmetricCipherKeyPair.getPrivate());
        return privateKeyInfo.getEncoded();
    }

    public KeyGenParameterSpec.Builder getDefaultSpecBuilder(String keyName, SharedPreferences preferences) {
        return new KeyGenParameterSpec.Builder(
                keyName,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(RSAUtils.getKeyLength(preferences))
                .setEncryptionPaddings(ENCRYPTION_PADDINGS);
    }

    public static X509Certificate createCertificate(byte[] certificateData) throws
            CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certificateData));
    }

    public static RSAPrivateCrtKey createPrivateKey(byte[] keyMaterial) throws
            NoSuchAlgorithmException,
            InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyMaterial);
        return (RSAPrivateCrtKey) keyFactory.generatePrivate(keySpec);
    }

    /**
     * Import RSA key pair using encoded key data from {@link PrivateKey#getEncoded()} and {@link PublicKey#getEncoded()}.
     */
    public void importKey(String keyName,
                          @NonNull PrivateKey privateKey, Context ctx) throws
            CertificateException,
            NoSuchAlgorithmException,
            InvalidKeySpecException,
            KeyStoreException,
            IOException,
            OperatorCreationException {

        int daysValid = DEFAULT_DAYS_VALID;

        //create self-signed certificate with the specified end validity
        PublicKey publicKey = createPublicFromPrivateKey((RSAPrivateCrtKey) privateKey);
        KeyPair keyPair = new KeyPair(publicKey, privateKey);
        X509Certificate certificate = SelfSignedCertGenerator.generate(keyPair,
                "SHA256withRSA",
                "OneMoreSecret",
                daysValid);


        KeyStore.PrivateKeyEntry privateKeyEntry = new KeyStore.PrivateKeyEntry(privateKey, new Certificate[]{certificate});

        KeyProtection keyProtection = new KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setUserAuthenticationRequired(true)
                .setEncryptionPaddings(ENCRYPTION_PADDINGS)
                .setIsStrongBoxBacked(ctx.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE))
                .build();

        keyStore.setEntry(keyName, privateKeyEntry, keyProtection);

        Log.d(TAG, "Private key '" + keyName + "' successfully imported. Certificate: " + certificate.getSerialNumber());
    }

    public static PublicKey createPublicFromPrivateKey(RSAPrivateCrtKey privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return KeyFactory.
                getInstance("RSA").
                generatePublic(new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
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

