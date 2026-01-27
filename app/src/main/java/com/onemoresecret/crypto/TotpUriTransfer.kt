package com.onemoresecret.crypto


class TotpUriTransfer(
    message: ByteArray,
    rsaPublicKeyMaterial: ByteArray,
    rsaTransformation: RsaTransformation,
    aesKeyLength: Int,
    aesTransformation: AesTransformation
) : EncryptedMessage(
    message,
    rsaPublicKeyMaterial,
    rsaTransformation,
    aesKeyLength,
    aesTransformation
) {
    override val applicationId: Int
        get() = MessageComposer.APPLICATION_TOTP_URI
}
