package com.onemoresecret;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;

/**
 * This is a OneMoreSecret specific extension of {@link java.io.DataOutputStream}. All additional methods boil down to a byte arrays prepended by its length.
 * The length is a unsigned short value, thus limiting the array length to 65535.
 */
public class OmsDataInputStream extends DataInputStream {
    /**
     * Creates a DataInputStream that uses the specified
     * underlying InputStream.
     *
     * @param in the specified input stream
     */
    public OmsDataInputStream(InputStream in) {
        super(in);
    }

    /**
     * This is a workaround method for reading UTF-8 strings, as the native method <a href="https://docs.oracle.com/javase/7/docs/api/java/io/DataInput.html#modified-utf-8">modifies UTF-8</a>.
     * Here, native UTF-8 byte array is written, prepended with the array length.
     */
    public String readString() throws IOException {
        return new String(readByteArray());
    }

    /**
     * The byte array is prepended by its length. The length is an unsigned short value, so the maximal length of the array is 65535 bytes.
     */
    public byte[] readByteArray() throws IOException {
        var size = readUnsignedShort();
        var bArr = new byte[size];
        assert read(bArr) == size;
        return bArr;
    }

    /**
     * Reads a {@link BigInteger} from a byte array prepended by the array's length.
     *
     */
    public BigInteger readBigInteger() throws IOException {
        return new BigInteger(readByteArray());
    }
}
