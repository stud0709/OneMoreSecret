package com.onemoresecret.crypto

import com.onemoresecret.OmsDataOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class EncryptedCryptoCurrencyAddress(
    ai: Int,
    privateKey: ByteArray,
    rsaPublicKeyMaterial: ByteArray,
    rsaTransformation: RsaTransformation,
    aesKeyLength: Int,
    aesTransformation: AesTransformation
) {
    @JvmField
    val message: ByteArray

    init {
        this.message = MessageComposer.createRsaAesEnvelope(
            rsaPublicKeyMaterial,
            rsaTransformation,
            aesKeyLength,
            aesTransformation,
            createPayload(ai, privateKey)
        )
    }

    private fun createPayload(ai: Int, privateKey: ByteArray): ByteArray {
        try {
            ByteArrayOutputStream().use { baos ->
                OmsDataOutputStream(baos).use { dataOutputStream ->

                    // (1) the real Application Identifier
                    dataOutputStream.writeUnsignedShort(ai)

                    // (2) private key as byte array
                    dataOutputStream.writeByteArray(privateKey)
                    return baos.toByteArray()
                }
            }
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }

    companion object {
        private val TAG: String = EncryptedCryptoCurrencyAddress::class.java.simpleName
    }
}
