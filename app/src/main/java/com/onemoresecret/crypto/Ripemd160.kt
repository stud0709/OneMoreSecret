package com.onemoresecret.crypto

object Ripemd160 {
    private val r1 = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        7, 4, 13, 1, 10, 6, 15, 3, 12, 0, 9, 5, 2, 14, 11, 8,
        3, 10, 14, 4, 9, 15, 8, 1, 2, 7, 0, 6, 13, 11, 5, 12,
        1, 9, 11, 10, 0, 8, 12, 4, 13, 3, 7, 15, 14, 5, 6, 2,
        4, 0, 5, 9, 7, 12, 2, 10, 14, 1, 3, 8, 11, 6, 15, 13
    )

    private val r2 = intArrayOf(
        5, 14, 7, 0, 9, 2, 11, 4, 13, 6, 15, 8, 1, 10, 3, 12,
        6, 11, 3, 7, 0, 13, 5, 10, 14, 15, 8, 12, 4, 9, 1, 2,
        15, 5, 1, 3, 7, 14, 6, 9, 11, 8, 12, 2, 10, 0, 4, 13,
        8, 6, 4, 1, 3, 11, 15, 0, 5, 12, 2, 13, 9, 7, 10, 14,
        12, 15, 10, 4, 1, 5, 8, 7, 6, 2, 13, 14, 0, 3, 9, 11
    )

    private val s1 = intArrayOf(
        11, 14, 15, 12, 5, 8, 7, 9, 11, 13, 14, 15, 6, 7, 9, 8,
        7, 6, 8, 13, 11, 9, 7, 15, 7, 12, 15, 9, 11, 7, 13, 12,
        11, 13, 6, 7, 14, 9, 13, 15, 14, 8, 13, 6, 5, 12, 7, 5,
        11, 12, 14, 15, 14, 15, 9, 8, 9, 14, 5, 6, 8, 6, 5, 12,
        9, 15, 5, 11, 6, 8, 13, 12, 5, 12, 13, 14, 11, 8, 5, 6
    )

    private val s2 = intArrayOf(
        8, 9, 9, 11, 13, 15, 15, 5, 7, 7, 8, 11, 14, 14, 12, 6,
        9, 13, 15, 7, 12, 8, 9, 11, 7, 7, 12, 7, 6, 15, 13, 11,
        9, 7, 15, 11, 8, 6, 6, 14, 12, 13, 5, 14, 13, 13, 7, 5,
        15, 5, 8, 11, 14, 14, 6, 14, 6, 9, 12, 9, 12, 5, 15, 8,
        8, 5, 12, 9, 12, 5, 14, 6, 8, 13, 6, 5, 15, 13, 11, 11
    )

    @JvmStatic
    fun digest(input: ByteArray): ByteArray {
        val padded = pad(input)
        var h0 = 0x67452301
        var h1 = 0xefcdab89.toInt()
        var h2 = 0x98badcfe.toInt()
        var h3 = 0x10325476
        var h4 = 0xc3d2e1f0.toInt()

        val block = IntArray(16)
        var offset = 0

        while (offset < padded.size) {
            for (i in 0 until 16) {
                val base = offset + i * 4
                block[i] = (padded[base].toInt() and 0xff) or
                    ((padded[base + 1].toInt() and 0xff) shl 8) or
                    ((padded[base + 2].toInt() and 0xff) shl 16) or
                    ((padded[base + 3].toInt() and 0xff) shl 24)
            }

            var a1 = h0
            var b1 = h1
            var c1 = h2
            var d1 = h3
            var e1 = h4

            var a2 = h0
            var b2 = h1
            var c2 = h2
            var d2 = h3
            var e2 = h4

            for (j in 0 until 80) {
                val t1 = rol(a1 + f1(j, b1, c1, d1) + block[r1[j]] + k1(j), s1[j]) + e1
                a1 = e1
                e1 = d1
                d1 = rol(c1, 10)
                c1 = b1
                b1 = t1

                val t2 = rol(a2 + f2(j, b2, c2, d2) + block[r2[j]] + k2(j), s2[j]) + e2
                a2 = e2
                e2 = d2
                d2 = rol(c2, 10)
                c2 = b2
                b2 = t2
            }

            val t = h1 + c1 + d2
            h1 = h2 + d1 + e2
            h2 = h3 + e1 + a2
            h3 = h4 + a1 + b2
            h4 = h0 + b1 + c2
            h0 = t

            offset += 64
        }

        return toBytes(h0, h1, h2, h3, h4)
    }

    private fun pad(input: ByteArray): ByteArray {
        val bitLength = input.size.toLong() * 8
        val paddingLength = ((56 - ((input.size + 1) % 64)) + 64) % 64
        val padded = ByteArray(input.size + 1 + paddingLength + 8)
        input.copyInto(padded, 0, 0, input.size)
        padded[input.size] = 0x80.toByte()

        for (i in 0 until 8) {
            padded[padded.size - 8 + i] = ((bitLength ushr (8 * i)) and 0xff).toByte()
        }

        return padded
    }

    private fun rol(value: Int, bits: Int): Int =
        (value shl bits) or (value ushr (32 - bits))

    private fun f1(j: Int, x: Int, y: Int, z: Int): Int =
        when (j) {
            in 0..15 -> x xor y xor z
            in 16..31 -> (x and y) or (x.inv() and z)
            in 32..47 -> (x or y.inv()) xor z
            in 48..63 -> (x and z) or (y and z.inv())
            else -> x xor (y or z.inv())
        }

    private fun f2(j: Int, x: Int, y: Int, z: Int): Int =
        when (j) {
            in 0..15 -> x xor (y or z.inv())
            in 16..31 -> (x and z) or (y and z.inv())
            in 32..47 -> (x or y.inv()) xor z
            in 48..63 -> (x and y) or (x.inv() and z)
            else -> x xor y xor z
        }

    private fun k1(j: Int): Int =
        when (j) {
            in 0..15 -> 0x00000000
            in 16..31 -> 0x5a827999
            in 32..47 -> 0x6ed9eba1
            in 48..63 -> 0x8f1bbcdc.toInt()
            else -> 0xa953fd4e.toInt()
        }

    private fun k2(j: Int): Int =
        when (j) {
            in 0..15 -> 0x50a28be6.toInt()
            in 16..31 -> 0x5c4dd124
            in 32..47 -> 0x6d703ef3
            in 48..63 -> 0x7a6d76e9
            else -> 0x00000000
        }

    private fun toBytes(vararg words: Int): ByteArray {
        val result = ByteArray(words.size * 4)
        var offset = 0

        for (word in words) {
            result[offset] = word.toByte()
            result[offset + 1] = (word ushr 8).toByte()
            result[offset + 2] = (word ushr 16).toByte()
            result[offset + 3] = (word ushr 24).toByte()
            offset += 4
        }

        return result
    }
}
