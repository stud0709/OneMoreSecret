package com.onemoresecret.crypto

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Log
import org.spongycastle.crypto.generators.RSAKeyPairGenerator
import org.spongycastle.crypto.params.RSAKeyGenerationParameters
import org.spongycastle.crypto.util.PrivateKeyInfoFactory
import org.spongycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.spongycastle.jcajce.provider.asymmetric.util.PrimeCertaintyCalculator
import org.spongycastle.operator.OperatorCreationException
import java.io.ByteArrayInputStream
import java.io.IOException
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Objects
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException

class CryptographyManager {
    @JvmField
    val keyStore: KeyStore

    init {
        try {
            keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }

        try {
            keyStore.load(null) // Keystore must be loaded before it can be accessed
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    @Throws(
        KeyStoreException::class,
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidKeyException::class
    )
    fun getInitializedCipherForEncryption(keyName: String?, rsaTransformation: String?): Cipher {
        val cipher = Cipher.getInstance(rsaTransformation)
        cipher.init(Cipher.ENCRYPT_MODE, keyStore.getCertificate(keyName).publicKey)
        return cipher
    }

    fun getInitializedCipherForDecryption(keyName: String?, transformation: String?): Cipher {
        try {
            val cipher = Cipher.getInstance(transformation)
            val secretKey = Objects.requireNonNull<PrivateKey>(getPrivateKey(keyName))
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            return cipher
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    /**
     * If SecretKey was previously created for that keyName, then grab and return it.
     */
    @Throws(
        KeyStoreException::class,
        NoSuchAlgorithmException::class,
        UnrecoverableKeyException::class
    )
    fun getPrivateKey(keyName: String?): PrivateKey {
        val key = keyStore.getKey(keyName, null)
        return key as PrivateKey
    }

    @Throws(KeyStoreException::class)
    fun getCertificate(keyName: String?): Certificate? {
        return keyStore.getCertificate(keyName)
    }

    @Throws(
        InvalidAlgorithmParameterException::class,
        NoSuchAlgorithmException::class,
        NoSuchProviderException::class
    )
    fun generateKeyPair(spec: KeyGenParameterSpec?): KeyPair? {
        val keyPairGenerator: KeyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            ANDROID_KEYSTORE
        )

        keyPairGenerator.initialize(spec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Import RSA key pair using encoded key data from [PrivateKey.getEncoded] and [java.security.PublicKey.getEncoded].
     */
    @Throws(
        CertificateException::class,
        KeyStoreException::class,
        IOException::class,
        OperatorCreationException::class
    )
    fun importKey(keyName: String?, keyPair: KeyPair, ctx: Context) {
        //create self-signed certificate with the specified end validity

        val certificate = SelfSignedCertGenerator.generate(
            keyPair,
            "SHA256withRSA",
            "OneMoreSecret",
            DEFAULT_DAYS_VALID
        )

        val privateKeyEntry =
            KeyStore.PrivateKeyEntry(keyPair.private, arrayOf<Certificate?>(certificate))

        val keyProtection =
            KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setUserAuthenticationRequired(true)
                .setEncryptionPaddings(*ENCRYPTION_PADDINGS)
                .setIsStrongBoxBacked(
                    ctx.packageManager
                        .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
                )
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .build()

        keyStore.setEntry(keyName, privateKeyEntry, keyProtection)

        Log.d(
            TAG,
            "Private key '" + keyName + "' successfully imported. Certificate: " + certificate?.serialNumber
        )
    }

    @Throws(KeyStoreException::class)
    fun deleteKey(keyName: String?) {
        keyStore.deleteEntry(keyName)
    }

    /**
     * Resolve key by its modulus and public exponent
     *
     * @param fingerprint SHA-256 hash of modulus and public exponent, [RSAUtils.getFingerprint]
     * @return all key alias matching that SHA-256
     */
    @Throws(KeyStoreException::class)
    fun getByFingerprint(fingerprint: ByteArray?): MutableList<String?> {
        val result: MutableList<String?> = ArrayList<String?>()

        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val alias = aliases.nextElement()

            try {
                val cert = getCertificate(alias) as X509Certificate
                val publicKey = cert.publicKey as RSAPublicKey
                val fp = RSAUtils.getFingerprint(publicKey)

                if (fingerprint.contentEquals(fp)) {
                    result.add(alias)
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        return result
    }

    companion object {
        const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
        private val TAG: String = CryptographyManager::class.java.simpleName
        val ENCRYPTION_PADDINGS: Array<String?> = arrayOf<String?>(
            KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
            KeyProperties.ENCRYPTION_PADDING_RSA_OAEP
        )

        const val DEFAULT_DAYS_VALID: Int = 9999

        /**
         * Generate key pair by means of the BouncyCastle library to have access to the key material.
         */
        @JvmStatic
        @Throws(IOException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        fun generateKeyPair(keyLength: Int): KeyPair {
            val rsaKeyPairGenerator = RSAKeyPairGenerator()

            rsaKeyPairGenerator.init(
                RSAKeyGenerationParameters(
                    BigInteger.valueOf(0x10001),
                    SecureRandom(),
                    keyLength,
                    PrimeCertaintyCalculator.getDefaultCertainty(keyLength)
                )
            )

            val asymmetricCipherKeyPair = rsaKeyPairGenerator.generateKeyPair()
            val privateKeyInfo =
                PrivateKeyInfoFactory.createPrivateKeyInfo(asymmetricCipherKeyPair.private)
            val privateKeyMaterial = privateKeyInfo.getEncoded()
            val publicKeyMaterial =
                SubjectPublicKeyInfoFactory.createSubjectPublicKeyInfo(asymmetricCipherKeyPair.public)
                    .getEncoded()
            return restoreKeyPair(privateKeyMaterial, publicKeyMaterial)
        }

        @Throws(CertificateException::class)
        fun restoreCertificate(certificateData: ByteArray?): X509Certificate? {
            val cf = CertificateFactory.getInstance("X509")
            return cf.generateCertificate(ByteArrayInputStream(certificateData)) as X509Certificate?
        }

        @JvmStatic
        @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        fun restoreKeyPair(privateKeyMaterial: ByteArray?, publicKeyMaterial: ByteArray?): KeyPair {
            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
            val keySpec = PKCS8EncodedKeySpec(privateKeyMaterial)
            val privateKey = keyFactory.generatePrivate(keySpec)
            val publicKeySpec = X509EncodedKeySpec(publicKeyMaterial)
            val publicKey = keyFactory.generatePublic(publicKeySpec)
            return KeyPair(publicKey, privateKey)
        }
    }
}

