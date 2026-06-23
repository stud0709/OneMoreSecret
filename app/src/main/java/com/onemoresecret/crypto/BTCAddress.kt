package com.onemoresecret.crypto

import com.onemoresecret.crypto.Base58.decode
import fr.acinq.secp256k1.Secp256k1
import org.jetbrains.annotations.Contract
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom

object BTCAddress {
    private val TAG: String = BTCAddress::class.java.simpleName
    private val secureRandom = SecureRandom()

    private fun sha256(bytes: ByteArray): ByteArray {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
    }

    private fun requirePrivateKeyBytes(privateKey: ByteArray): ByteArray {
        require(privateKey.size == 32) { "Private key must be 32 bytes" }
        return privateKey
    }

    private fun requirePrivateKey(privateKey: ByteArray): ByteArray {
        val normalizedPrivateKey = requirePrivateKeyBytes(privateKey)
        require(Secp256k1.secKeyVerify(normalizedPrivateKey)) { "Invalid secp256k1 private key" }
        return normalizedPrivateKey
    }

    @JvmStatic
    @Contract("_ -> new")
    fun toKeyPair(privateKey: ByteArray?): ECKeyPair {
        val normalizedPrivateKey = requirePrivateKey(requireNotNull(privateKey))
        return ECKeyPair(
            normalizedPrivateKey,
            Secp256k1.pubkeyCreate(normalizedPrivateKey)
        )
    }

    @JvmStatic
    fun newKeyPair(): ECKeyPair {
        val privateKey = ByteArray(32)
        do {
            secureRandom.nextBytes(privateKey)
        } while (!Secp256k1.secKeyVerify(privateKey))

        return ECKeyPair(privateKey, Secp256k1.pubkeyCreate(privateKey))
    }

    fun toBTCAddress(publicKey: ByteArray): ByteArray {
        // https://gobittest.appspot.com/Address
        require(publicKey.size == 65 && publicKey[0] == 0x04.toByte()) {
            "Public key must be an uncompressed 65-byte secp256k1 public key"
        }

        //SHA-256 and RIPEMD digest
        val digest = Ripemd160.digest(sha256(publicKey))

        //prepend with version byte (0x00)
        val digestWithVersionByte = ByteArray(digest.size + 1)
        System.arraycopy(digest, 0, digestWithVersionByte, 1, digest.size)

        //calculate checksum
        val checksum = sha256(sha256(digestWithVersionByte))

        //BTC address = version byte + digest + first 4 bytes of the checksum
        val btcAddress = ByteArray(digestWithVersionByte.size + 4)
        System.arraycopy(digestWithVersionByte, 0, btcAddress, 0, digestWithVersionByte.size)
        //append first 4 bytes of digest
        System.arraycopy(checksum, 0, btcAddress, digestWithVersionByte.size, 4)

        return btcAddress
    }

    fun toBTCKeyPair(keyPair: ECKeyPair): BTCKeyPair {
        return BTCKeyPair(toWIF(keyPair.privateKey), toBTCAddress(keyPair.publicKey))
    }

    /**
     * Convert private key to WIF format.
     *
     * @param privateKey Private Key to encode
     * @return WIF byte array. Encrypt it with [Base58.encode] for the readable representation.
     */
    fun toWIF(privateKey: ByteArray): ByteArray {
        //as of https://gobittest.appspot.com/PrivateKey
        val normalizedPrivateKey = requirePrivateKeyBytes(privateKey)

        //prepend with 0x80

        val withQualifier = ByteArray(normalizedPrivateKey.size + 1)
        withQualifier[0] = 0x80.toByte()
        System.arraycopy(normalizedPrivateKey, 0, withQualifier, 1, normalizedPrivateKey.size)

        val digest = sha256(sha256(withQualifier))

        val wif = ByteArray(withQualifier.size + 4)
        System.arraycopy(withQualifier, 0, wif, 0, withQualifier.size)
        //append first 4 bytes of digest
        System.arraycopy(digest, 0, wif, withQualifier.size, 4)

        return wif
    }

    fun validateWIF(wifString: String): Boolean {
        //as of https://gobittest.appspot.com/PrivateKey

        //decode Base58

        val wif = decode(wifString)

        //drop last 4 bytes
        val withQualifier = wif.copyOfRange(0, wif.size - 4)

        //calculate dual sha256
        val digest = sha256(sha256(withQualifier))

        //compare checksums
        return digest.copyOfRange(0, 4).contentEquals( //first 4 bytes of the digest
            wif.copyOfRange(wif.size - 4, wif.size) //last 4 bytes of WIF byte array
        )
    }

    /**
     * Restore private key from WIF
     *
     * @param wif Wallet Interchange Format as byte array (apply [Base58.decode] to WIF string first)
     */
    @JvmStatic
    fun toPrivateKey(wif: ByteArray): ByteArray {
        //drop first one and last 4 bytes
        return requirePrivateKeyBytes(wif.copyOfRange(1, wif.size - 4))
    }

    fun sign(data: ByteArray?, privateKey: ByteArray): ByteArray {
        val messageHash = sha256(requireNotNull(data))
        return Secp256k1.compact2der(Secp256k1.sign(messageHash, requirePrivateKey(privateKey)))
    }

    fun validate(publicKey: ByteArray, data: ByteArray?, signature: ByteArray?): Boolean {
        val messageHash = sha256(requireNotNull(data))
        return Secp256k1.verify(
            Secp256k1.der2compact(requireNotNull(signature)),
            messageHash,
            publicKey
        )
    }

    data class ECKeyPair(@JvmField val privateKey: ByteArray, val publicKey: ByteArray) {
        fun toBTCKeyPair(): BTCKeyPair {
            return toBTCKeyPair(this)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ECKeyPair) return false

            if (!privateKey.contentEquals(other.privateKey)) return false
            if (!publicKey.contentEquals(other.publicKey)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = privateKey.contentHashCode()
            result = 31 * result + publicKey.contentHashCode()
            return result
        }
    }

    @JvmRecord
    data class BTCKeyPair(@JvmField val wif: ByteArray, val btcAddress: ByteArray) {
        @Contract(" -> new")
        fun toECKeyPair(): ECKeyPair {
            return toKeyPair(toPrivateKey(wif))
        }

        val wifBase58: String
            get() = Base58.encode(wif)

        val btcAddressBase58: String
            get() = Base58.encode(btcAddress)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BTCKeyPair) return false

            if (!wif.contentEquals(other.wif)) return false
            if (!btcAddress.contentEquals(other.btcAddress)) return false
            if (wifBase58 != other.wifBase58) return false
            if (btcAddressBase58 != other.btcAddressBase58) return false

            return true
        }

        override fun hashCode(): Int {
            var result = wif.contentHashCode()
            result = 31 * result + btcAddress.contentHashCode()
            result = 31 * result + wifBase58.hashCode()
            result = 31 * result + btcAddressBase58.hashCode()
            return result
        }
    }
}
