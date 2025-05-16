package com.onemoresecret.crypto

import android.util.Log
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OmsDataOutputStream
import com.onemoresecret.R
import com.onemoresecret.Util
import com.onemoresecret.crypto.AESUtil.generateIv
import com.onemoresecret.crypto.AESUtil.generateRandomSecretKey
import com.onemoresecret.crypto.AESUtil.process
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Objects
import java.util.Optional
import java.util.regex.Pattern
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

abstract class MessageComposer {
    @JvmRecord
    data class AesEncryptionParameters(val secretKey: SecretKey, val iv: IvParameterSpec)

    @JvmRecord
    data class RsaAesEnvelope(
        @JvmField val applicationId: Int,
        @JvmField val rsaTransormation: String,
        @JvmField val fingerprint: ByteArray,
        @JvmField val aesTransformation: String,
        @JvmField val iv: ByteArray,
        @JvmField val encryptedAesSecretKey: ByteArray
    )

    companion object {
        private val TAG: String = MessageComposer::class.java.simpleName
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
            return when (applicationId) {
                APPLICATION_BITCOIN_ADDRESS -> {
                    Optional.of(R.drawable.baseline_currency_bitcoin_24)
                }

                APPLICATION_ENCRYPTED_MESSAGE, APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED -> {
                    Optional.of(R.drawable.baseline_password_24)
                }

                APPLICATION_TOTP_URI, APPLICATION_TOTP_URI_DEPRECATED -> {
                    Optional.of(R.drawable.baseline_timelapse_24)
                }

                else -> {
                    Optional.empty()
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
        fun encodeAsOmsText(message: ByteArray?): String {
            return OMS_PREFIX + Base64.getEncoder().encodeToString(message)
        }

        @JvmStatic
        fun decode(omsText: String): ByteArray? {
            val m = OMS_PATTERN.matcher(omsText)

            if (!m.find()) {
                //TOTP?
                if (OneTimePassword(omsText).isValid) {
                    //this is a time based OTP, pass unchanged
                    return omsText.toByteArray()
                }
                // not a valid OMS message
                return null
            }

            val result: ByteArray

            val version = Objects.requireNonNull(m.group(1)).toInt()

            var tmpOmsText = omsText
            // (1) remove prefix and line breaks
            tmpOmsText = tmpOmsText.substring(m.group().length)
            tmpOmsText = tmpOmsText.replace("\\s+".toRegex(), "")

            if (version == 0) {
                // (2) convert to byte array
                result = Base64.getDecoder().decode(tmpOmsText)
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
            aesTransformationIdx: Int,
            payload: ByteArray?
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
            aesTransformationIdx: Int,
            payload: ByteArray?
        ): ByteArray {
            try {
                ByteArrayOutputStream().use { baos ->
                    OmsDataOutputStream(baos).use { dataOutputStream ->
                        val aesEncryptionParameters = prepareRsaAesEnvelope(
                            dataOutputStream,
                            applicationId,
                            rsaPublicKey,
                            rsaTransformationIdx,
                            aesKeyLength,
                            aesTransformationIdx
                        )
                        // (7) AES-encrypted message
                        dataOutputStream.writeByteArray(
                            process(
                                Cipher.ENCRYPT_MODE,
                                payload,
                                aesEncryptionParameters.secretKey,
                                aesEncryptionParameters.iv,
                                AesTransformation.entries[aesTransformationIdx].transformation
                            )
                        )
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
            aesTransformationIdx: Int
        ): AesEncryptionParameters {
            // init AES

            val iv = generateIv()
            val secretKey = generateRandomSecretKey(aesKeyLength)

            // encrypt AES secret key with RSA
            val cipher =
                Cipher.getInstance(RsaTransformation.entries[rsaTransformationIdx].transformation)
            cipher.init(Cipher.ENCRYPT_MODE, rsaPublicKey)

            val encryptedSecretKey = cipher.doFinal(secretKey.encoded)

            // (1) application-ID
            dataOutputStream.writeUnsignedShort(applicationId)

            // (2) RSA transformation index
            dataOutputStream.writeUnsignedShort(rsaTransformationIdx)

            // (3) fingerprint
            dataOutputStream.writeByteArray(RSAUtils.getFingerprint(rsaPublicKey))

            // (4) AES transformation index
            dataOutputStream.writeUnsignedShort(aesTransformationIdx)

            // (5) IV
            dataOutputStream.writeByteArray(iv.iv)

            // (6) RSA-encrypted AES secret key
            dataOutputStream.writeByteArray(encryptedSecretKey)

            return AesEncryptionParameters(secretKey, iv)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readRsaAesEnvelope(dataInputStream: OmsDataInputStream): RsaAesEnvelope {
            //(1) Application ID
            val applicationId = dataInputStream.readUnsignedShort()

            //(2) RSA transformation index
            val rsaTransformation =
                RsaTransformation.entries[dataInputStream.readUnsignedShort()].transformation
            Log.d(
                TAG,
                "RSA transformation: $rsaTransformation"
            )

            //(3) RSA fingerprint
            val fingerprint = dataInputStream.readByteArray()
            Log.d(TAG, "RSA fingerprint: " + Util.byteArrayToHex(fingerprint))

            // (4) AES transformation index
            val aesTransformation =
                AesTransformation.entries[dataInputStream.readUnsignedShort()].transformation
            Log.d(
                TAG,
                "AES transformation: $aesTransformation"
            )

            //(5) IV
            val iv = dataInputStream.readByteArray()
            Log.d(TAG, "IV: " + Util.byteArrayToHex(iv))

            //(6) RSA-encrypted AES secret key
            val encryptedAesSecretKey = dataInputStream.readByteArray()

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
