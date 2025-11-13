package com.onemoresecret.crypto

import android.net.Uri
import com.onemoresecret.crypto.Base32.decode
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.math.BigDecimal
import java.nio.ByteBuffer
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.util.Arrays
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import androidx.core.net.toUri

class OneTimePassword(s: String) {
    val uri: Uri = s.toUri()

    init {
        //        Log.d(TAG, s);
    }

    val valid: Boolean
        get() {
            if (OTP_SCHEME == uri.scheme
                && TOTP == uri.authority
                && this.name != null && !this.name!!.isEmpty()
            ) {
                try {
                    this.digits
                    this.algorithm
                    generateResponseCode(0)
                    return true
                } catch (_: Exception) {
                }
            }
            return false
        }

    val name: String?
        get() {
            val path = uri.path
            if (path == null || !path.startsWith("/")) {
                return null
            }
            // path is "/name", so remove leading "/", and trailing white spaces
            val name = path.substring(1).trim { it <= ' ' }
            if (name.isEmpty()) {
                return null // only white spaces.
            }
            return name
        }

    val issuer: String?
        get() = uri.getQueryParameter(ISSUER_PARAM)

    val secret: String
        get() = uri.getQueryParameter(SECRET_PARAM)!!
            .uppercase(Locale.getDefault())

    val period: Int
        get() {
            val s = uri.getQueryParameter(PERIOD_PARAM)
            return if (s == null || s.isEmpty()) DEFAULT_PERIOD else s.toInt()
        }

    val digits: Int
        /**
         * Length of the TOTP token
         * @return
         */
        get() {
            val s = uri.getQueryParameter(DIGITS_PARAM)
            val d: String =
                if (s == null || s.isEmpty()) DIGITS[0] else s
            require(
                Arrays.stream(DIGITS)
                    .anyMatch { anObject -> d == anObject }) { "Invalid digits: $d" }
            return d.toInt()
        }

    val algorithm: String
        get() {
            val s = uri.getQueryParameter(ALGORITHM_PARAM)
            val alg: String =
                if (s == null || s.isEmpty()) ALGORITHM[0] else s
            require(
                Arrays.stream(ALGORITHM)
                    .anyMatch { anObject ->
                        alg == anObject
                    }) { "invalid algorithm: $alg" }
            return alg
        }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class)
    fun sign(data: ByteArray?): ByteArray {
        val mac = Mac.getInstance("Hmac${this.algorithm}")
        mac.init(SecretKeySpec(decode(this.secret), ""))
        return mac.doFinal(data)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class, IOException::class)
    fun generateResponseCode(state: Long): String {
        val value = ByteBuffer.allocate(8).putLong(state).array()
        return generateResponseCode(value)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class, IOException::class)
    fun generateResponseCode(state: Long, challenge: ByteArray?): String {
        if (challenge == null) {
            return generateResponseCode(state)
        } else {
            // Allocate space for combination and store.
            val value = ByteBuffer.allocate(8 + challenge.size)
                .putLong(state) // Write out OTP state
                .put(challenge, 0, challenge.size) // Concatenate with challenge.
                .array()
            return generateResponseCode(value)
        }
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeyException::class, IOException::class)
    fun generateResponseCode(challenge: ByteArray?): String {
        val hash = sign(challenge)

        // Dynamically truncate the hash
        // OffsetBits are the low order bits of the last byte of the hash
        val offset = hash[hash.size - 1].toInt() and 0xF
        // Grab a positive integer value starting at the given offset.
        val truncatedHash = hashToInt(hash, offset) and 0x7FFFFFFF
        val pinValue = truncatedHash % BigDecimal.TEN.pow(this.digits).toInt()
        return padOutput(pinValue)
    }

    /**
     * Grabs a positive integer value from the input array starting at
     * the given offset.
     *
     * @param bytes the array of bytes
     * @param start the index into the array to start grabbing bytes
     * @return the integer constructed from the four bytes in the array
     */
    @Throws(IOException::class)
    private fun hashToInt(bytes: ByteArray, start: Int): Int {
        return DataInputStream(
            ByteArrayInputStream(
                bytes,
                start,
                bytes.size - start
            )
        ).readInt()
    }

    private fun padOutput(value: Int): String {
        var result = value.toString()
        while (result.length < this.digits) {
            result = "0" + result
        }
        return result
    }

    @JvmRecord
    data class OtpState(@JvmField val current: Long, @JvmField val secondsUntilNext: Long)

    val state: OtpState
        get() {
            val nowSeconds =
                TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
            return OtpState(nowSeconds / this.period, nowSeconds % this.period)
        }

    companion object {
        const val OTP_SCHEME: String = "otpauth"
        const val TOTP: String = "totp" // time-based
        private const val ISSUER_PARAM = "issuer"
        const val SECRET_PARAM: String = "secret"

        const val DEFAULT_PERIOD: Int = 30
        const val PERIOD_PARAM: String = "period"
        @JvmField
        val DIGITS: Array<String> = arrayOf<String>("6", "8")
        const val DIGITS_PARAM: String = "digits"
        const val ALGORITHM_PARAM: String = "algorithm"
        @JvmField
        val ALGORITHM: Array<String> = arrayOf<String>("SHA1", "SHA256", "SHA512")

        private val TAG: String = OneTimePassword::class.java.simpleName
    }
}
