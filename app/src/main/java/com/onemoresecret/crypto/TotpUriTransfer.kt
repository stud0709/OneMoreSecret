package com.onemoresecret.crypto

import java.security.interfaces.RSAPublicKey

class TotpUriTransfer(
    message: ByteArray,
    rsaPublicKey: RSAPublicKey,
    rsaTransformationIdx: Int,
    aesKeyLength: Int,
    aesTransformationIdx: Int
) : EncryptedMessage(
    message,
    rsaPublicKey,
    rsaTransformationIdx,
    aesKeyLength,
    aesTransformationIdx
) {
    override val applicationId: Int
        get() = MessageComposer.APPLICATION_TOTP_URI
}
