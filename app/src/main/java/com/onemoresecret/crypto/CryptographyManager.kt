package com.onemoresecret.crypto

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import com.onemoresecret.Util
import com.onemoresecret.crypto.RSAUtils.getFingerprint
import org.spongycastle.crypto.generators.RSAKeyPairGenerator
import org.spongycastle.crypto.params.RSAKeyGenerationParameters
import org.spongycastle.crypto.util.PrivateKeyInfoFactory
import org.spongycastle.crypto.util.SubjectPublicKeyInfoFactory
import org.spongycastle.jcajce.provider.asymmetric.util.PrimeCertaintyCalculator
import java.io.IOException
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import androidx.core.content.edit
import java.security.Key
import java.security.spec.MGF1ParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

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

    data class UserRsaCipherBox(val cipher: Cipher, val wipe: () -> Unit)

    /**
     * Initialize Cipher for user RSA key.
     * @param masterRsaCihper ready to use Master RSA cipher (decryption)
     * @param opmode Cipher.DECRYPT_MODE or Cipher.ENCRYPT_MODE
     * @return Cipher ready to use along with a function to wipe the cipher
     */
    fun getInitializedUserRsaCipher(
        masterRsaCihper: Cipher,
        keyStoreEntry: KeyStoreEntry,
        rsaTransformation: RsaTransformation,
        opmode: Int
    ): UserRsaCipherBox {
        try {
            val cipher = Cipher.getInstance(rsaTransformation.transformation)
            val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)

            var key: Key
            var wipeKey: () -> Unit = {}

            if (opmode == Cipher.ENCRYPT_MODE) {
                val publicKeySpec = X509EncodedKeySpec(keyStoreEntry.public)
                key = keyFactory.generatePublic(publicKeySpec) as RSAPublicKey
            } else {
                //decrypt AES key
                val aesKeyMaterial = masterRsaCihper.doFinal(keyStoreEntry.aesRawBytesEncrypted)
                val privateKeyMaterial = AESUtil.process(
                    Cipher.DECRYPT_MODE,
                    keyStoreEntry.private,
                    aesKeyMaterial,
                    Util.Ref(keyStoreEntry.iv),
                    AesTransformation.AES_GCM_NO_PADDING
                )

                aesKeyMaterial.fill(0) //wipe

                val keySpec = PKCS8EncodedKeySpec(privateKeyMaterial)
                key = keyFactory.generatePrivate(keySpec)
                wipeKey = { privateKeyMaterial.fill(0) }
            }
            cipher.init(Cipher.DECRYPT_MODE, key)

            return UserRsaCipherBox(cipher, wipeKey)
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    /**
     * Initialize Cipher for Master RSA key. Decryption requires biometric authentication.
     */
    fun getInitializedMasterRsaCipher(opmode: Int): Cipher {
        try {
            val cipher =
                Cipher.getInstance(RsaTransformation.RSA_ECB_OAEPWithSHA_256AndMGF1Padding.transformation)
            val oaepSpec = OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec.SHA1,
                PSource.PSpecified.DEFAULT
            )
            cipher.init(
                opmode,
                if (opmode == Cipher.DECRYPT_MODE)
                //decrypt with private key
                    keyStore.getKey(MASTER_KEY_ALIAS, null)
                else
                //encrypt with public key
                    keyStore.getCertificate(MASTER_KEY_ALIAS).publicKey,
                oaepSpec
            )

            return cipher
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    @Throws(
        InvalidAlgorithmParameterException::class,
        NoSuchAlgorithmException::class,
        NoSuchProviderException::class
    )

    fun importRsaKey(
        preferences: SharedPreferences,
        privateKeyMaterial: ByteArray,
        publicKeyMaterial: ByteArray,
        keyAlias: String
    ) {
        //generate RSA key instance from the provided key material
        val keyFactory = KeyFactory.getInstance(KeyProperties.KEY_ALGORITHM_RSA)
        val publicKeySpec = X509EncodedKeySpec(publicKeyMaterial)
        val publicKey = keyFactory.generatePublic(publicKeySpec) as RSAPublicKey

        //prepare AES key to encrypt this RSA key
        val aesRawBytes = ByteArray(32)
        SecureRandom().nextBytes(aesRawBytes)

        //encrypt raw bytes
        val masterRsaCipher = getInitializedMasterRsaCipher(Cipher.ENCRYPT_MODE)
        val aesRawBytesEncrypted = masterRsaCipher.doFinal(aesRawBytes)

        //use AES key to encrypt private key
        val ivRef = Util.Ref<ByteArray>()
        val privateKeyEncrypted = AESUtil.process(
            Cipher.ENCRYPT_MODE,
            privateKeyMaterial,
            aesRawBytes,
            ivRef,
            AesTransformation.AES_GCM_NO_PADDING
        )

        //wipe AES key
        aesRawBytes.fill(0)

        //create KeyStoreEntry
        val fingerprintBytes = getFingerprint(publicKey)
        val entry = KeyStoreEntry(
            keyAlias,
            "RSA",
            fingerprintBytes,
            publicKeyMaterial,
            aesRawBytesEncrypted,
            ivRef.value!!,
            privateKeyEncrypted
        )

        //add the new KeyStoreEntry to the keystore property
        val keystoreSet: Set<String?> = preferences.getStringSet(PROP_KEYSTORE, setOf<String?>())!!
        val mutableSet = keystoreSet.toMutableSet()
        mutableSet.add(Util.JACKSON_MAPPER.writeValueAsString(entry))
        preferences.edit { putStringSet(PROP_KEYSTORE, mutableSet) }
    }

    /**
     * This key is created with the biometric protection in the Strongbox (or TEE if not available).
     * Its only function is to protect the AES keys of keystore entries
     */
    fun createMasterRsaKey(ctx: Context) {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )

        val specBuilder = KeyGenParameterSpec.Builder(
            MASTER_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setUserAuthenticationRequired(true)
            // Require authentication for every single use (0 seconds)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .setIsStrongBoxBacked(
                ctx.packageManager
                    .hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            )

        try {
            keyPairGenerator.initialize(specBuilder.build())
            keyPairGenerator.generateKeyPair()
        } catch (e: StrongBoxUnavailableException) {
            // Fallback to TEE if StrongBox fails despite the system feature check
            specBuilder.setIsStrongBoxBacked(false)
            keyPairGenerator.initialize(specBuilder.build())
            keyPairGenerator.generateKeyPair()
        }
    }

    fun deleteKey(alias: String, preferences: SharedPreferences) {
        val keystore: Set<String> =
            preferences.getStringSet(PROP_KEYSTORE, mutableSetOf<String>())!!
        val result = keystore
            .map { s -> Util.JACKSON_MAPPER.readValue(s, KeyStoreEntry::class.java) }
            .filter { it.alias != alias }
            .map { Util.JACKSON_MAPPER.writeValueAsString(it) }
            .toSet()
        preferences.edit { putStringSet(PROP_KEYSTORE, result) }
    }

    /**
     * Resolve key by its modulus and public exponent
     *
     * @param fingerprint SHA-256 hash of modulus and public exponent, [RSAUtils.getFingerprint]
     * @return all key alias matching that SHA-256
     */
    fun getByFingerprint(fingerprint: ByteArray, preferences: SharedPreferences): KeyStoreEntry? {
        val keystore: Set<String> =
            preferences.getStringSet(PROP_KEYSTORE, mutableSetOf<String>())!!
        return keystore
            .map { s -> Util.JACKSON_MAPPER.readValue(s, KeyStoreEntry::class.java) }
            .find { it.fingerprint contentEquals fingerprint }
    }

    fun getByAlias(alias: String, preferences: SharedPreferences): KeyStoreEntry? {
        val keystore: Set<String> =
            preferences.getStringSet(PROP_KEYSTORE, mutableSetOf<String>())!!
        val result = keystore
            .map { s -> Util.JACKSON_MAPPER.readValue(s, KeyStoreEntry::class.java) }
            .find { it.alias == alias }
        return result
    }

    companion object {
        const val AES_GCM_NO_PADDING: String = "AES/GCM/NoPadding"
        const val MASTER_KEY_ALIAS: String = "oms_master"
        const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
        private val TAG: String = CryptographyManager::class.java.simpleName
        const val PROP_KEYSTORE: String = "keystore"

        /**
         * Generate key pair using BouncyCastle library
         */
        @JvmStatic
        @Throws(IOException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        fun generateKeyPair(keyLength: Int): Pair<ByteArray, ByteArray> {
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
            val privateKeyMaterial = PrivateKeyInfoFactory
                .createPrivateKeyInfo(asymmetricCipherKeyPair.private).encoded
            val publicKeyMaterial = SubjectPublicKeyInfoFactory
                .createSubjectPublicKeyInfo(asymmetricCipherKeyPair.public).encoded

            return Pair(publicKeyMaterial, privateKeyMaterial)
        }
    }
}

