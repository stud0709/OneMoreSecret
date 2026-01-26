package com.onemoresecret.crypto

import java.security.interfaces.RSAPublicKey

class TotpUriTransfer(
    message: ByteArray,
    rsaPublicKey: RSAPublicKey,
    rsaTransformation: RsaTransformation,
    aesKeyLength: Int,
    aesTransformation: AesTransformation
) : EncryptedMessage(
    message,
    rsaPublicKey,
    rsaTransformation,
    aesKeyLength,
    aesTransformation
) {
    override val applicationId: Int
        get() = MessageComposer.APPLICATION_TOTP_URI
}
