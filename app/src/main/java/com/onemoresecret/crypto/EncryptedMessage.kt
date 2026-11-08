package com.onemoresecret.crypto

import com.onemoresecret.OmsDataOutputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

open class EncryptedMessage(
    message: ByteArray,
    rsaPublicKeyMaterial: ByteArray,
    rsaTransformation: RsaTransformation,
    aesKeyLength: Int,
    aesTransformation: AesTransformation
) {
    @JvmField
    val message: ByteArray

    init {
        this.message = org.onemoresecret.protocol.OmsProtocol.seal(RSAUtil.restorePublicKey(rsaPublicKeyMaterial), this.applicationId, message)
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

    protected open val applicationId: Int
        get() = MessageComposer.APPLICATION_ENCRYPTED_MESSAGE
}
