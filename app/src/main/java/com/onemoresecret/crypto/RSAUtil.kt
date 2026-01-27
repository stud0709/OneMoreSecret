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

object RSAUtil {
    const val PROP_RSA_TRANSFORMATION_IDX: String = "rsa_transformation_idx"

    @JvmStatic
    fun getRsaTransformation(preferences: SharedPreferences): RsaTransformation {
        val idx = preferences.getInt(
            PROP_RSA_TRANSFORMATION_IDX,
            RsaTransformation.RSA_ECB_PKCS1Padding.ordinal)
        return RsaTransformation.entries[idx]
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class)
    fun getFingerprint(publicKey: RSAPublicKey): ByteArray {
        val sha256 = MessageDigest.getInstance("SHA-256")
        sha256.update(publicKey.modulus.toByteArray())
        return sha256.digest(publicKey.publicExponent.toByteArray())
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun restorePublicKey(encoded: ByteArray): RSAPublicKey {
        val publicKeySpec = X509EncodedKeySpec(encoded)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(publicKeySpec) as RSAPublicKey
    }

    @JvmStatic
    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidKeyException::class,
        IllegalBlockSizeException::class,
        BadPaddingException::class
    )
    fun process(
        cipherMode: Int,
        keyMaterial: ByteArray,
        rsaTransformation: RsaTransformation,
        data: ByteArray
    ): ByteArray {
        val cipher = Cipher.getInstance(rsaTransformation.transformation)
        cipher.init(cipherMode, if (cipherMode == Cipher.DECRYPT_MODE) restorePrivateKey(keyMaterial) else restorePublicKey(keyMaterial))

        return cipher.doFinal(data)
    }

    @JvmStatic
    @Throws(InvalidKeySpecException::class, NoSuchAlgorithmException::class)
    fun restorePrivateKey(encoded: ByteArray): RSAPrivateKey {
        val privateKeySpec = PKCS8EncodedKeySpec(encoded)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(privateKeySpec) as RSAPrivateKey
    }
}
