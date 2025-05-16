package com.onemoresecret;

import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;

/**
 * This is a OneMoreSecret specific extension of {@link java.io.DataOutputStream}. All additional methods boil down to a byte arrays prepended by its length.
 * The length is a {@link Short} value, thus limiting the array length to {@link Short#MAX_VALUE}.
 */
public class OmsDataOutputStream extends DataOutputStream {
    /**
     * Creates a new data output stream to write data to the specified
     * underlying output stream. The counter <code>written</code> is
     * set to zero.
     *
     * @param out the underlying output stream, to be saved for later
     *            use.
     * @see FilterOutputStream#out
     */
    public OmsDataOutputStream(OutputStream out) {
        super(out);
    }

    /**
     * This is a workaround method for writing UTF-8 strings, as the native method <a href="https://docs.oracle.com/javase/7/docs/api/java/io/DataInput.html#modified-utf-8">modifies UTF-8</a>.
     * Here, native UTF-8 byte array is written, prepended with the array length.
     */
    public void writeString(String s) throws IOException {
        writeByteArray(s.getBytes());
    }

    /**
     * The byte array is prepended by its length. The length is an unsigned short value, so the maximal length of the array is 65535 bytes.
     */
    public void writeByteArray(byte[] bArr) throws IOException {
        writeUnsignedShort(bArr.length);
        write(bArr);
    }

    public void writeUnsignedShort(int value) throws IOException {
        assert value < 65536;
        writeShort((short) value);
    }

    /**
     * BigInteger's {@link BigInteger#toByteArray()} representation prepended by the array's length.
     */
    public void writeBigInteger(BigInteger bi) throws IOException {
        writeByteArray(bi.toByteArray());
    }
}
