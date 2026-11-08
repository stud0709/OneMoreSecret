package com.onemoresecret.crypto

import org.onemoresecret.protocol.OmsProtocol
import android.util.Log
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OmsDataOutputStream
import com.onemoresecret.R
import com.onemoresecret.Util
import com.onemoresecret.crypto.AESUtil.process
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.Base64
import java.util.Objects
import java.util.Optional
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException

abstract class MessageComposer {
    @JvmRecord
    data class AesEncryptionParameters(val aesKeyMaterial: ByteArray, val iv: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AesEncryptionParameters) return false

            if (!aesKeyMaterial.contentEquals(other.aesKeyMaterial)) return false
            if (!iv.contentEquals(other.iv)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = aesKeyMaterial.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            return result
        }
    }

    @JvmRecord
    data class RsaAesEnvelope(
        @JvmField val applicationId: Int,
        @JvmField val rsaTransformation: RsaTransformation,
        @JvmField val fingerprint: ByteArray,
        @JvmField val aesTransformation: AesTransformation,
        @JvmField val iv: ByteArray,
        @JvmField val encryptedAesSecretKey: ByteArray,
        @JvmField val protocolHeader: OmsProtocol.Header? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RsaAesEnvelope) return false

            if (applicationId != other.applicationId) return false
            if (rsaTransformation != other.rsaTransformation) return false
            if (!fingerprint.contentEquals(other.fingerprint)) return false
            if (aesTransformation != other.aesTransformation) return false
            if (!iv.contentEquals(other.iv)) return false
            if (!encryptedAesSecretKey.contentEquals(other.encryptedAesSecretKey)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = applicationId
            result = 31 * result + rsaTransformation.hashCode()
            result = 31 * result + fingerprint.contentHashCode()
            result = 31 * result + aesTransformation.hashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + encryptedAesSecretKey.contentHashCode()
            return result
        }
    }

    companion object {
        private val TAG: String = MessageComposer::class.java.simpleName
        const val APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER: Int = 0
        const val APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED: Int = 1
        const val APPLICATION_TOTP_URI_DEPRECATED: Int = 2
        const val APPLICATION_ENCRYPTED_FILE: Int = 3
        const val APPLICATION_KEY_REQUEST: Int = 4
        const val APPLICATION_KEY_RESPONSE: Int = 5

        /*
         * Until now, it was possible to understand what kind of information is contained in the message.
         * The generic message will only allow to decrypt it, all other information will be found inside.
         */
        const val APPLICATION_RSA_AES_GENERIC: Int = 6
        const val APPLICATION_BITCOIN_ADDRESS: Int = 7
        const val APPLICATION_ENCRYPTED_MESSAGE: Int = 8
        const val APPLICATION_TOTP_URI: Int = 9
        const val APPLICATION_NOSTR_PAIRING: Int = 10
        @Deprecated("Use APPLICATION_NOSTR_PAIRING instead")
        const val APPLICATION_WIFI_PAIRING: Int = 10
        const val APPLICATION_KEY_REQUEST_PAIRING: Int = 11
        const val APPLICATION_ENCRYPTED_OTP = 12
        const val APPLICATION_OMS4WEB_CALLBACK_REQUEST = 13

        /**
         * Checks if an applicationId should be added to recent entries.
         *
         * @param applicationId
         * @return boolean
         */
        @JvmStatic
        fun isApplicationIdStorableInRecent(applicationId: Int): Boolean {
            return when (applicationId) {
                APPLICATION_BITCOIN_ADDRESS,
                APPLICATION_ENCRYPTED_MESSAGE, APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED,
                APPLICATION_TOTP_URI, APPLICATION_TOTP_URI_DEPRECATED -> true
                else -> false
            }
        }

        /**
         * Prefix of a text encoded message.
         */
        const val OMS_PREFIX: String = "oms00_"

        const val OMS_FILE_TYPE: String = "oms"

        /**
         * Text encoded OMS messages begin with omsXX_ with XX being the protocol
         * version.
         */
        val OMS_PATTERN: Pattern = Pattern.compile("oms([\\da-f]{2})_")

        /**
         * You can pass messages through the clipboard. A message begins with
         * [MessageComposer.OMS_PREFIX]. Version 00 of OMS protocol:
         *
         *  1. BASE64 encode `message`prepend (1) with
         * [MessageComposer.OMS_PREFIX]
         *
         */
        @JvmStatic
        fun encodeAsOmsText(message: ByteArray): String {
            return OmsProtocol.encodeText(message)
    }

        @JvmStatic
        fun decode(omsText: String): ByteArray? {
            OmsProtocol.decodeText(omsText)?.let { return it }
            return if (OneTimePassword(omsText).valid) omsText.toByteArray() else null
    }

        @JvmStatic
        @Throws(
            NoSuchAlgorithmException::class,
            NoSuchPaddingException::class,
            IllegalBlockSizeException::class,
            BadPaddingException::class,
            InvalidKeyException::class,
            InvalidAlgorithmParameterException::class
        )
        fun createRsaAesEnvelope(
            rsaPublicKeyMaterial: ByteArray,
            rsaTransformation: RsaTransformation,
            aesKeyLength: Int,
            aesTransformation: AesTransformation,
            payload: ByteArray
        ): ByteArray {
            return createRsaAesEnvelope(
                APPLICATION_RSA_AES_GENERIC,
                rsaPublicKeyMaterial,
                rsaTransformation,
                aesKeyLength,
                aesTransformation,
                payload
            )
        }

        @Throws(
            NoSuchAlgorithmException::class,
            NoSuchPaddingException::class,
            IllegalBlockSizeException::class,
            BadPaddingException::class,
            InvalidKeyException::class,
            InvalidAlgorithmParameterException::class
        )
        fun createRsaAesEnvelope(
            applicationId: Int,
            rsaPublicKeyMaterial: ByteArray,
            rsaTransformation: RsaTransformation,
            aesKeyLength: Int,
            aesTransformation: AesTransformation,
            payload: ByteArray
        ): ByteArray {
            val publicKey = RSAUtil.restorePublicKey(rsaPublicKeyMaterial)
            if (applicationId == APPLICATION_RSA_AES_GENERIC) {
                OmsDataInputStream(java.io.ByteArrayInputStream(payload)).use { input ->
                    val innerId = input.readUnsignedShort()
                    val innerBody = input.readByteArray()
                    require(input.read() == -1) { "Trailing generic payload" }
                    return OmsProtocol.seal(publicKey, innerId, innerBody)
                }
            }
            return OmsProtocol.seal(publicKey, applicationId, payload)
    }

        @Throws(
            NoSuchAlgorithmException::class,
            IOException::class,
            NoSuchPaddingException::class,
            InvalidKeyException::class,
            IllegalBlockSizeException::class,
            BadPaddingException::class
        )
        fun prepareRsaAesEnvelope(
            dataOutputStream: OmsDataOutputStream,
            applicationId: Int,
            rsaPublicKeyMaterial: ByteArray,
            rsaTransformation: RsaTransformation,
            aesKeyLength: Int,
            aesTransformation: AesTransformation
        ): AesEncryptionParameters {
            // init AES

            val aesKeyMaterial = ByteArray(aesKeyLength/8)
            SecureRandom().nextBytes(aesKeyMaterial)

            // encrypt AES secret key with RSA
            val rsaCipher = Cipher.getInstance(rsaTransformation.transformation)
            val rsaPublicKey = RSAUtil.restorePublicKey(rsaPublicKeyMaterial)
            rsaCipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)

            val encryptedSecretKey = rsaCipher.doFinal(aesKeyMaterial)

            // (1) application-ID
            dataOutputStream.writeUnsignedShort(applicationId)

            // (2) RSA transformation index
            dataOutputStream.writeUnsignedShort(rsaTransformation.ordinal)

            // (3) fingerprint
            dataOutputStream.writeByteArray(RSAUtil.getFingerprint(rsaPublicKey))

            // (4) AES transformation index
            dataOutputStream.writeUnsignedShort(aesTransformation.ordinal)

            // (5) IV
            val iv = AESUtil.generateIv(aesTransformation)
            dataOutputStream.writeByteArray(iv)

            // (6) RSA-encrypted AES secret key
            dataOutputStream.writeByteArray(encryptedSecretKey)

            return AesEncryptionParameters(aesKeyMaterial, iv)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readRsaAesEnvelope(dataInputStream: OmsDataInputStream): RsaAesEnvelope {
            //(1) Application ID
            val applicationId = dataInputStream.readUnsignedShort()
            if (applicationId == 0x254f) {
                val header = OmsProtocol.readHeader(dataInputStream, applicationId)
                require(header.kind == OmsProtocol.RSA) { "Not an RSA envelope" }
                return RsaAesEnvelope(APPLICATION_RSA_AES_GENERIC, RsaTransformation.entries[2],
                    header.fingerprint, AesTransformation.AES_GCM_NO_PADDING, header.noncePrefix, header.wrappedKey, header)
            }

            //(2) RSA transformation index
            val rsaTransformation =
                RsaTransformation.entries[dataInputStream.readUnsignedShort()]
            Log.d(TAG, "RSA transformation: ${rsaTransformation.transformation}")

            //(3) RSA fingerprint
            val fingerprint = dataInputStream.readByteArray()
            Log.d(TAG, "RSA fingerprint: " + Util.byteArrayToHex(fingerprint))

            // (4) AES transformation index
            val aesTransformation =
                AesTransformation.entries[dataInputStream.readUnsignedShort()]
            Log.d(TAG, "AES transformation: ${aesTransformation.transformation}")

            //(5) IV
            val iv = dataInputStream.readByteArray()
            Log.d(TAG, "IV: " + Util.byteArrayToHex(iv))

            //(6) RSA-encrypted AES secret key
            val encryptedAesSecretKey = dataInputStream.readByteArray()
            Log.d(TAG, "RSA-encrypted AES secret key: " + Util.byteArrayToHex(encryptedAesSecretKey))

            //(7) AES-encrypted message <= leave here
            return RsaAesEnvelope(
                applicationId,
                rsaTransformation,
                fingerprint,
                aesTransformation,
                iv,
                encryptedAesSecretKey
            )
        }
    }
}
