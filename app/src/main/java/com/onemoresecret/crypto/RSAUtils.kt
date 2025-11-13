package com.onemoresecret.crypto

import android.content.SharedPreferences
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

object RSAUtils {
    const val PROP_RSA_TRANSFORMATION_IDX: String = "rsa_transformation_idx"

    @JvmStatic
    fun getRsaTransformationIdx(preferences: SharedPreferences): Int {
        return preferences.getInt(PROP_RSA_TRANSFORMATION_IDX, 0)
    }

    @JvmStatic
    fun getRsaTransformation(preferences: SharedPreferences): RsaTransformation {
        return RsaTransformation.entries[getRsaTransformationIdx(preferences)]
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class)
    fun getFingerprint(publicKey: RSAPublicKey): ByteArray {
        val sha256 = MessageDigest.getInstance("SHA-256")
        sha256.update(publicKey.getModulus().toByteArray())
        return sha256.digest(publicKey.getPublicExponent().toByteArray())
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun restorePublicKey(encoded: ByteArray?): RSAPublicKey? {
        val publicKeySpec = X509EncodedKeySpec(encoded)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(publicKeySpec) as RSAPublicKey?
    }

    @JvmStatic
    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidKeyException::class,
        IllegalBlockSizeException::class,
        BadPaddingException::class
    )
    fun process(cipherMode: Int, key: Key, transformation: String, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(cipherMode, key)

        return cipher.doFinal(data)
    }

    @JvmStatic
    @Throws(InvalidKeySpecException::class, NoSuchAlgorithmException::class)
    fun restorePrivateKey(encoded: ByteArray?): RSAPrivateKey? {
        val privateKeySpec = PKCS8EncodedKeySpec(encoded)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(privateKeySpec) as RSAPrivateKey?
    }
}
