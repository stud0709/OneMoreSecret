package com.onemoresecret

import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.math.BigInteger

/**
 * This is a OneMoreSecret specific extension of [java.io.DataOutputStream]. All additional methods boil down to a byte arrays prepended by its length.
 * The length is a unsigned short value, thus limiting the array length to 65535.
 */
class OmsDataInputStream
/**
 * Creates a DataInputStream that uses the specified
 * underlying InputStream.
 *
 * @param in the specified input stream
 */
    (inputStream: InputStream?) : DataInputStream(inputStream) {
    /**
     * This is a workaround method for reading UTF-8 strings, as the native method [modifies UTF-8](https://docs.oracle.com/javase/7/docs/api/java/io/DataInput.html#modified-utf-8).
     * Here, native UTF-8 byte array is written, prepended with the array length.
     */
    @Throws(IOException::class)
    fun readString(): String {
        return String(readByteArray())
    }

    /**
     * The byte array is prepended by its length. The length is an unsigned short value, so the maximal length of the array is 65535 bytes.
     */
    @Throws(IOException::class)
    fun readByteArray(): ByteArray {
        val size = readUnsignedShort()
        val bArr = ByteArray(size)
        if (read(bArr) != size) throw IOException()
        return bArr
    }

    /**
     * Reads a [BigInteger] from a byte array prepended by the array's length.
     */
    @Throws(IOException::class)
    fun readBigInteger(): BigInteger {
        return BigInteger(readByteArray())
    }
}
