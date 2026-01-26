package com.onemoresecret.crypto

import com.onemoresecret.OmsDataOutputStream
import com.onemoresecret.Util
import com.onemoresecret.crypto.AESUtil.process
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

class AesEncryptedPrivateKeyTransfer(
    alias: String,
    privateKeyMaterial: ByteArray,
    publicKeyMaterial: ByteArray,
    aesKeyMaterial: ByteArray,
    iv: ByteArray,
    salt: ByteArray,
    aesTransformation: AesTransformation,
    aesKeyAlgorithm: AesKeyAlgorithm,
    aesKeyLength: Int,
    aesKeyspecIterations: Int
) : MessageComposer() {
    @JvmField
    val message: ByteArray

    init {
        // --- create message ---
        try {
            ByteArrayOutputStream().use { baos ->
                OmsDataOutputStream(baos).use { dataOutputStream ->
                    // (1) application-ID
                    dataOutputStream.writeUnsignedShort(
                        APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER
                    )

                    // (2) alias
                    dataOutputStream.writeString(alias)

                    // --- AES parameter ---

                    // (3) salt
                    dataOutputStream.writeByteArray(salt)

                    // (4) iv
                    dataOutputStream.writeByteArray(iv)

                    // (5) AES transformation index
                    dataOutputStream.writeUnsignedShort(aesTransformation.ordinal)

                    // (6) key algorithm index
                    dataOutputStream.writeUnsignedShort(aesKeyAlgorithm.ordinal)

                    // (7) keyspec length
                    dataOutputStream.writeUnsignedShort(aesKeyLength)

                    // (8) keyspec iterations
                    dataOutputStream.writeUnsignedShort(aesKeyspecIterations)

                    // --- encrypted data ---

                    // (9) cipher text
                    dataOutputStream.writeByteArray(
                        getCipherText(
                            publicKeyMaterial,
                            privateKeyMaterial,
                            aesKeyMaterial,
                            iv,
                            aesTransformation
                        )
                    )
                    this.message = baos.toByteArray()
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Throws(
        InvalidAlgorithmParameterException::class,
        NoSuchPaddingException::class,
        IllegalBlockSizeException::class,
        NoSuchAlgorithmException::class,
        BadPaddingException::class,
        InvalidKeyException::class
    )
    private fun getCipherText(
        publicKeyMaterial: ByteArray,
        privateKeyMaterial: ByteArray,
        aesKeyMaterial: ByteArray,
        iv: ByteArray,
        aesTransformation: AesTransformation
    ): ByteArray {
        try {
            ByteArrayOutputStream().use { baos ->
                OmsDataOutputStream(baos).use { dataOutputStreamCipher ->
                    // (9.1) - private key material
                    dataOutputStreamCipher.writeByteArray(privateKeyMaterial)

                    // (9.2) - public key material
                    dataOutputStreamCipher.writeByteArray(publicKeyMaterial)
                    return process(
                        Cipher.ENCRYPT_MODE,
                        baos.toByteArray(),
                        aesKeyMaterial,
                        iv,
                        aesTransformation
                    )
                }
            }
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }
}
