package com.onemoresecret.crypto

import com.onemoresecret.OmsDataOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.interfaces.RSAPublicKey

open class EncryptedMessage(
    message: ByteArray,
    rsaPublicKey: RSAPublicKey,
    rsaTransformationIdx: Int,
    aesKeyLength: Int,
    aesTransformationIdx: Int
) {
    @JvmField
    val message: ByteArray

    protected open val applicationId: Int = MessageComposer.APPLICATION_ENCRYPTED_MESSAGE

    init {
        this.message = MessageComposer.createRsaAesEnvelope(
            rsaPublicKey,
            rsaTransformationIdx,
            aesKeyLength,
            aesTransformationIdx,
            createPayload(applicationId, message)
        )
    }

    private fun createPayload(ai: Int, message: ByteArray): ByteArray {
        try {
            ByteArrayOutputStream().use { baos ->
                OmsDataOutputStream(baos).use { dataOutputStream ->

                    // (1) the real Application Identifier
                    dataOutputStream.writeUnsignedShort(ai)

                    // (2) message key as byte array
                    dataOutputStream.writeByteArray(message)
                    return baos.toByteArray()
                }
            }
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }


}
