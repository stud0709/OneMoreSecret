package com.onemoresecret.crypto

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
import java.security.interfaces.RSAPublicKey
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
            result = 31 * result + iv.hashCode()
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
        @JvmField val encryptedAesSecretKey: ByteArray
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
        private val TAG: String = MessageComposer::class.java.getSimpleName()
        const val APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER: Int = 0
        const val APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED: Int = 1
        const val APPLICATION_TOTP_URI_DEPRECATED: Int = 2
        const val APPLICATION_ENCRYPTED_FILE: Int = 3
        const val APPLICATION_KEY_REQUEST: Int = 4
        const val APPLICATION_KEY_RESPONSE: Int = 5

        /**
         * Until now, it was possible to understand what kind of information is contained in the message.
         * The generic message will only allow to decrypt it, all other information will be found inside.
         */
        const val APPLICATION_RSA_AES_GENERIC: Int = 6
        const val APPLICATION_BITCOIN_ADDRESS: Int = 7
        const val APPLICATION_ENCRYPTED_MESSAGE: Int = 8
        const val APPLICATION_TOTP_URI: Int = 9
        const val APPLICATION_WIFI_PAIRING: Int = 10
        const val APPLICATION_KEY_REQUEST_PAIRING: Int = 11

        /**
         * Assigns a drawable to an applicationId. This is used to display recent entries.
         * **ApplicationId without a drawableId wil not be added to recent entries.**
         *
         * @param applicationId
         * @return drawableId
         */
        @JvmStatic
        fun getDrawableIdForApplicationId(applicationId: Int): Optional<Int> {
            when (applicationId) {
                APPLICATION_BITCOIN_ADDRESS -> {
                    return Optional.of<Int>(R.drawable.baseline_currency_bitcoin_24)
                }

                APPLICATION_ENCRYPTED_MESSAGE, APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED -> {
                    return Optional.of<Int>(R.drawable.baseline_password_24)
                }

                APPLICATION_TOTP_URI, APPLICATION_TOTP_URI_DEPRECATED -> {
                    return Optional.of<Int>(R.drawable.baseline_timelapse_24)
                }

                else -> {
                    return Optional.empty<Int>()
                }
            }
        }

        /**
         * Prefix of a text encoded message.
         */
        const val OMS_PREFIX: String = "oms00_"

        const val OMS_FILE_TYPE: String = "oms00"

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
            return OMS_PREFIX + Base64.getEncoder().encodeToString(message)
        }

        @JvmStatic
        fun decode(omsText: String): ByteArray? {
            var omsText = omsText
            val m: Matcher = OMS_PATTERN.matcher(omsText)

            if (!m.find()) {
                //TOTP?
                if (OneTimePassword(omsText).valid) {
                    //this is a time based OTP, pass unchanged
                    return omsText.toByteArray()
                }
                // not a valid OMS message
                return null
            }

            val result: ByteArray?

            val version = Objects.requireNonNull(m.group(1)).toInt()

            // (1) remove prefix and line breaks
            omsText = omsText.substring(m.group().length)
            omsText = omsText.replace("\\s+".toRegex(), "")

            if (version == 0) {
                // (2) convert to byte array
                result = Base64.getDecoder().decode(omsText)
            } else {
                throw UnsupportedOperationException("Unsupported version: $version")
            }

            return result
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
            rsaPublicKey: RSAPublicKey,
            rsaTransformationIdx: Int,
            aesKeyLength: Int,
            aesTransformationIdx: AesTransformation,
            payload: ByteArray
        ): ByteArray {
            return createRsaAesEnvelope(
                APPLICATION_RSA_AES_GENERIC,
                rsaPublicKey,
                rsaTransformationIdx,
                aesKeyLength,
                aesTransformationIdx,
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
            rsaPublicKey: RSAPublicKey,
            rsaTransformationIdx: Int,
            aesKeyLength: Int,
            aesTransformation: AesTransformation,
            payload: ByteArray
        ): ByteArray {
            try {
                ByteArrayOutputStream().use { baos ->
                    OmsDataOutputStream(baos).use { dataOutputStream ->
                        val aesEncryptionParameters: AesEncryptionParameters =
                            prepareRsaAesEnvelope(
                                dataOutputStream,
                                applicationId,
                                rsaPublicKey,
                                rsaTransformationIdx,
                                aesKeyLength,
                                aesTransformation
                            )
                        // (7) AES-encrypted message
                        dataOutputStream.writeByteArray(
                            process(
                                Cipher.ENCRYPT_MODE,
                                payload,
                                aesEncryptionParameters.aesKeyMaterial,
                                aesEncryptionParameters.iv,
                                aesTransformation
                            )
                        )
                        //wipe
                        aesEncryptionParameters.aesKeyMaterial.fill(0)
                        aesEncryptionParameters.iv.fill(0)
                        return baos.toByteArray()
                    }
                }
            } catch (ex: IOException) {
                throw RuntimeException(ex)
            }
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
            rsaPublicKey: RSAPublicKey,
            rsaTransformationIdx: Int,
            aesKeyLength: Int,
            aesTransformation: AesTransformation
        ): AesEncryptionParameters {
            // init AES

            val aesKeyMaterial = ByteArray(aesKeyLength/8)
            SecureRandom().nextBytes(aesKeyMaterial)

            // encrypt AES secret key with RSA
            val rsaTransformation = RsaTransformation.entries[rsaTransformationIdx]
            val cipher = Cipher.getInstance(rsaTransformation.transformation)
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)
            val iv = AESUtil.generateIv(aesTransformation)

            val encryptedSecretKey = cipher.doFinal(aesKeyMaterial)

            // (1) application-ID
            dataOutputStream.writeUnsignedShort(applicationId)

            // (2) RSA transformation index
            dataOutputStream.writeUnsignedShort(rsaTransformationIdx)

            // (3) fingerprint
            dataOutputStream.writeByteArray(RSAUtils.getFingerprint(rsaPublicKey))

            // (4) AES transformation index
            dataOutputStream.writeUnsignedShort(aesTransformation.ordinal)

            // (5) IV
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
