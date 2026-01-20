package com.onemoresecret.crypto

data class KeyStoreEntry (
    val alias: String = "",
    val type: String = "",
    val fingerprint: ByteArray = ByteArray(0),
    val public: ByteArray = ByteArray(0),
    /**
     * AES key (encrypted by RSA master key) protecting protecting the private key
     */
    val aesRawBytesEncrypted: ByteArray = ByteArray(0),
    /**
     * IV used for private key encryption along with the AES key
     */
    val iv: ByteArray = ByteArray(0),
    val private: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KeyStoreEntry) return false

        if (type != other.type) return false
        if (!fingerprint.contentEquals(other.fingerprint)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + fingerprint.contentHashCode()
        return result
    }
}