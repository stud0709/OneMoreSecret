package com.onemoresecret

import java.io.DataOutputStream
import java.io.IOException
import java.io.OutputStream
import java.math.BigInteger

/**
 * This is a OneMoreSecret specific extension of [java.io.DataOutputStream]. All additional methods boil down to a byte arrays prepended by its length.
 * The length is a [Short] value, thus limiting the array length to [Short.MAX_VALUE].
 */
class OmsDataOutputStream
/**
 * Creates a new data output stream to write data to the specified
 * underlying output stream. The counter `written` is
 * set to zero.
 *
 * @param out the underlying output stream, to be saved for later
 * use.
 */
    (outputStream: OutputStream?) : DataOutputStream(outputStream) {
    /**
     * This is a workaround method for writing UTF-8 strings, as the native method [modifies UTF-8](https://docs.oracle.com/javase/7/docs/api/java/io/DataInput.html#modified-utf-8).
     * Here, native UTF-8 byte array is written, prepended with the array length.
     */
    @Throws(IOException::class)
    fun writeString(s: String) {
        writeByteArray(s.toByteArray())
    }

    /**
     * The byte array is prepended by its length. The length is an unsigned short value, so the maximal length of the array is 65535 bytes.
     */
    @Throws(IOException::class)
    fun writeByteArray(bArr: ByteArray) {
        writeUnsignedShort(bArr.size)
        write(bArr)
    }

    @Throws(IOException::class)
    fun writeUnsignedShort(value: Int) {
        assert(value < 65536)
        writeShort(value.toShort().toInt())
    }

    /**
     * BigInteger's [BigInteger.toByteArray] representation prepended by the array's length.
     */
    @Throws(IOException::class)
    fun writeBigInteger(bi: BigInteger) {
        writeByteArray(bi.toByteArray())
    }
}
