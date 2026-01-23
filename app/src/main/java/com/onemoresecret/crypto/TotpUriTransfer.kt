package com.onemoresecret.crypto

import java.security.interfaces.RSAPublicKey

class TotpUriTransfer(
    message: ByteArray,
    rsaPublicKey: RSAPublicKey,
    rsaTransformationIdx: Int,
    aesKeyLength: Int,
    aesTransformation: AesTransformation
) : EncryptedMessage(
    message,
    rsaPublicKey,
    rsaTransformationIdx,
    aesKeyLength,
    aesTransformation
) {
    override val applicationId: Int
        get() = MessageComposer.APPLICATION_TOTP_URI
}
