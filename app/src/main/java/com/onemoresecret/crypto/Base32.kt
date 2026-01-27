package com.onemoresecret.crypto

import java.io.UnsupportedEncodingException
import java.nio.charset.StandardCharsets
import java.util.Locale

object Base32 {
    //as of https://github.com/rnewman/base32-standalone/blob/master/src/main/java/org/emergent/android/weave/client/Base32.java
    private const val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val base32Lookup = intArrayOf(
        0xFF, 0xFF, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F,  // '0', '1', '2', '3', '4', '5', '6', '7'
        0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,  // '8', '9', ':', ';', '<', '=', '>', '?'
        0xFF, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,  // '@', 'A', 'B', 'C', 'D', 'E', 'F', 'G'
        0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,  // 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O'
        0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,  // 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W'
        0x17, 0x18, 0x19, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,  // 'X', 'Y', 'Z', '[', '\', ']', '^', '_'
        0xFF, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06,  // '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g'
        0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,  // 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o'
        0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,  // 'p', 'q', 'r', 's', 't', 'u', 'v', 'w'
        0x17, 0x18, 0x19, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF // 'x', 'y', 'z', '{', '|', '}', '~', 'DEL'
    )

    @Throws(UnsupportedEncodingException::class)
    fun encode(data: ByteArray): ByteArray {
        val lower = encodeOriginal(data).lowercase(Locale.getDefault())
        return lower.toByteArray(StandardCharsets.US_ASCII)
    }

    fun decodeModified(data: String): ByteArray {
        return decode(data.replace('8', 'L').replace('9', 'O'))
    }


    /**
     * Encodes byte array to Base32 String.
     *
     * @param bytes Bytes to encode.
     * @return Encoded byte array `bytes` as a String.
     */
    fun encodeOriginal(bytes: ByteArray): String {
        var i = 0
        var index = 0
        var digit: Int
        var currByte: Int
        var nextByte: Int
        val base32 = StringBuilder((bytes.size + 7) * 8 / 5)

        while (i < bytes.size) {
            currByte = if (bytes[i] >= 0) bytes[i].toInt() else (bytes[i] + 256) // unsign

            /* Is the current digit going to span a byte boundary? */
            if (index > 3) {
                if ((i + 1) < bytes.size) {
                    nextByte =
                        if (bytes[i + 1] >= 0) bytes[i + 1].toInt() else (bytes[i + 1] + 256)
                } else {
                    nextByte = 0
                }

                digit = currByte and (0xFF shr index)
                index = (index + 5) % 8
                digit = digit shl index
                digit = digit or (nextByte shr (8 - index))
                i++
            } else {
                digit = (currByte shr (8 - (index + 5))) and 0x1F
                index = (index + 5) % 8
                if (index == 0) i++
            }
            base32.append(base32Chars[digit])
        }

        return base32.toString()
    }

    /**
     * Decodes the given Base32 String to a raw byte array.
     *
     * @return Decoded `base32` String as a raw byte array.
     */
    @JvmStatic
    fun decode(base32: String): ByteArray {
        var index: Int
        var lookup: Int
        var digit: Int
        val iArr = IntArray(base32.length * 5 / 8)

        var i = 0
        index = 0
        var offset = 0
        while (i < base32.length) {
            lookup = base32[i].code - '0'.code

            /* Skip chars outside the lookup table */
            if (lookup < 0 || lookup >= base32Lookup.size) {
                i++
                continue
            }

            digit = base32Lookup[lookup]

            /* If this digit is not in the table, ignore it */
            if (digit == 0xFF) {
                i++
                continue
            }

            if (index <= 3) {
                index = (index + 5) % 8
                if (index == 0) {
                    iArr[offset] = (iArr[offset] or digit)
                    offset++
                    if (offset >= iArr.size) break
                } else {
                    iArr[offset] =
                        (iArr[offset] or (digit shl (8 - index)))
                }
            } else {
                index = (index + 5) % 8
                iArr[offset] = (iArr[offset] or (digit ushr index))
                offset++

                if (offset >= iArr.size) {
                    break
                }
                iArr[offset] = (iArr[offset] or (digit shl (8 - index)))
            }
            i++
        }
        return ByteArray(iArr.size) { i -> iArr[i].toByte() }
    }
}
