package com.onemoresecret.crypto

import com.onemoresecret.OmsDataOutputStream
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
        require(alias.toByteArray(Charsets.UTF_8).size <= 4096 && privateKeyMaterial.size <= 16384 && publicKeyMaterial.size <= 16384) { "Backup field too large" }
        org.onemoresecret.protocol.OmsProtocol.validateKeyPair(privateKeyMaterial, publicKeyMaterial)
        val body = ByteArrayOutputStream()
        java.io.DataOutputStream(body).use { out ->
            org.onemoresecret.protocol.OmsProtocol.writeBlob(out, alias.toByteArray(Charsets.UTF_8))
            org.onemoresecret.protocol.OmsProtocol.writeBlob(out, privateKeyMaterial)
            org.onemoresecret.protocol.OmsProtocol.writeBlob(out, publicKeyMaterial)
        }
        message = org.onemoresecret.protocol.OmsProtocol.passwordSeal(aesKeyMaterial, salt, aesKeyspecIterations, 0, body.toByteArray())
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
