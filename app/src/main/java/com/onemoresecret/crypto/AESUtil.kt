package com.onemoresecret.crypto

import android.content.SharedPreferences
import com.onemoresecret.Util
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.util.function.Consumer
import java.util.function.Supplier
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object AESUtil {
    @JvmStatic
    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun getAesKeyMaterialFromPassword(
        password: CharArray,
        salt: ByteArray,
        keyAlgorithm: String,
        keyLength: Int,
        keySpecIterations: Int
    ): ByteArray {
        val factory = SecretKeyFactory.getInstance(keyAlgorithm)
        val spec = PBEKeySpec(password, salt, keySpecIterations, keyLength)
        return factory.generateSecret(spec).encoded
    }

    @JvmStatic
    fun generateSalt(saltLength: Int): ByteArray {
        val salt = ByteArray(saltLength)
        SecureRandom().nextBytes(salt)
        return salt
    }

    @JvmStatic
    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        BadPaddingException::class,
        IllegalBlockSizeException::class
    )
    fun process(
        cipherMode: Int,
        input: ByteArray,
        aesKeyMaterial: ByteArray,
        iv: Util.Ref<ByteArray>,
        aesTransformation: AesTransformation
    ): ByteArray {
        val cipher = Cipher.getInstance(aesTransformation.transformation)
        if (iv.value == null) {
            cipher.init(
                cipherMode,
                SecretKeySpec(aesKeyMaterial, "AES")
            )
            iv.value = cipher.iv
        } else {
            cipher.init(
                cipherMode,
                SecretKeySpec(aesKeyMaterial, "AES"),
                IvParameterSpec(iv.value)
            )
        }

        return cipher.doFinal(input)
    }

    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        BadPaddingException::class,
        IllegalBlockSizeException::class,
        IOException::class
    )
    @JvmStatic
    fun process(
        cipherMode: Int,
        `is`: InputStream,
        os: OutputStream,
        aesKeyMaterial: ByteArray,
        iv: Util.Ref<ByteArray>,
        aesTransformation: AesTransformation,
        cancellationSupplier: Supplier<Boolean?>?,
        progressConsumer: Consumer<Int?>?
    ) {
        val cipher = Cipher.getInstance(aesTransformation.transformation)
        if (iv.value == null) {
            cipher.init(
                cipherMode,
                SecretKeySpec(aesKeyMaterial, "AES")
            )
            iv.value = cipher.iv
        } else {
            cipher.init(
                cipherMode,
                SecretKeySpec(
                    aesKeyMaterial,
                    "AES"
                ),
                IvParameterSpec(iv.value)
            )
        }

        val iArr = ByteArray(1024)
        var length: Int
        var bytesProcessed = 0

        while ((`is`.read(iArr).also { length = it }) > 0 &&
            (cancellationSupplier == null || !cancellationSupplier.get()!!)
        ) {
            os.write(cipher.update(iArr, 0, length))
            bytesProcessed += length
            progressConsumer?.accept(bytesProcessed)
        }

        os.write(cipher.doFinal())
    }

    const val PROP_AES_KEY_LENGTH: String = "aes_key_length"
    const val PROP_AES_KEY_ITERATIONS: String = "aes_key_iterations"
    const val PROP_AES_SALT_LENGTH: String = "aes_salt_length"
    const val PROP_AES_TRANSFORMATION_IDX: String = "aes_transformation_idx"
    const val PROP_AES_KEY_ALGORITHM_IDX: String = "aes_key_algorithm_idx"

    @JvmStatic
    fun getAesTransformationIdx(preferences: SharedPreferences): Int {
        return preferences.getInt(
            PROP_AES_TRANSFORMATION_IDX,
            AesTransformation.AES_CBC_PKCS5Padding.ordinal
        )
    }

    fun getAesTransformation(preferences: SharedPreferences): AesTransformation {
        return AesTransformation.entries[getAesTransformationIdx(preferences)]
    }

    @JvmStatic
    fun getAesKeyAlgorithmIdx(preferences: SharedPreferences): Int {
        return preferences.getInt(
            PROP_AES_KEY_ALGORITHM_IDX,
            AesKeyAlgorithm.PBKDF2WithHmacSHA256.ordinal
        )
    }

    @JvmStatic
    fun getAesKeyAlgorithm(preferences: SharedPreferences): AesKeyAlgorithm {
        return AesKeyAlgorithm.entries[getAesKeyAlgorithmIdx(preferences)]
    }

    @JvmStatic
    fun getSaltLength(preferences: SharedPreferences): Int {
        return preferences.getInt(PROP_AES_SALT_LENGTH, 16)
    }

    @JvmStatic
    fun getKeyspecIterations(preferences: SharedPreferences): Int {
        return preferences.getInt(PROP_AES_KEY_ITERATIONS, 1024)
    }

    @JvmStatic
    fun getKeyLength(preferences: SharedPreferences): Int {
        return preferences.getInt(PROP_AES_KEY_LENGTH, 256)
    }
}
