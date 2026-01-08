package com.onemoresecret.crypto

import com.onemoresecret.OmsDataOutputStream
import com.onemoresecret.crypto.AESUtil.process
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.NoSuchAlgorithmException
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class AesEncryptedPrivateKeyTransfer(
    alias: String,
    rsaKeyPair: KeyPair,
    aesKey: SecretKey,
    iv: IvParameterSpec,
    salt: ByteArray,
    aesTransformationIdx: Int,
    aesKeyAlgorithmIdx: Int,
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
                    dataOutputStream.writeByteArray(iv.iv)

                    // (5) AES transformation index
                    dataOutputStream.writeUnsignedShort(aesTransformationIdx)

                    // (6) key algorithm index
                    dataOutputStream.writeUnsignedShort(aesKeyAlgorithmIdx)

                    // (7) keyspec length
                    dataOutputStream.writeUnsignedShort(aesKeyLength)

                    // (8) keyspec iterations
                    dataOutputStream.writeUnsignedShort(aesKeyspecIterations)

                    // --- encrypted data ---

                    // (9) cipher text
                    dataOutputStream.writeByteArray(
                        getCipherText(
                            rsaKeyPair,
                            aesKey,
                            iv,
                            aesTransformationIdx
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
        rsaKeyPair: KeyPair,
        aesKey: SecretKey,
        iv: IvParameterSpec,
        aesTransformationIdx: Int
    ): ByteArray? {
        try {
            ByteArrayOutputStream().use { baos ->
                OmsDataOutputStream(baos).use { dataOutputStreamCipher ->
                    val publicKey = rsaKeyPair.public as RSAPublicKey
                    val privateKey = rsaKeyPair.private as RSAPrivateKey

                    // (9.1) - private key material
                    dataOutputStreamCipher.writeByteArray(privateKey.encoded)

                    // (9.2) - public key material
                    dataOutputStreamCipher.writeByteArray(publicKey.encoded)
                    return process(
                        Cipher.ENCRYPT_MODE, baos.toByteArray(),
                        aesKey,
                        iv,
                        AesTransformation.entries[aesTransformationIdx]
                    )
                }
            }
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }
}
