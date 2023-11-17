package com.onemoresecret.crypto;

import android.net.Uri;


import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class OneTimePassword {
    public static final String OTP_SCHEME = "otpauth";
    public static final String TOTP = "totp"; // time-based
    private static final String ISSUER_PARAM = "issuer";
    public static final String SECRET_PARAM = "secret";

    public static final int DEFAULT_PERIOD = 30;
    public static final String PERIOD_PARAM = "period";
    public static final String[] DIGITS = {"6", "8"};
    public static final String DIGITS_PARAM = "digits";
    public static final String ALGORITHM_PARAM = "algorithm";
    public static final String[] ALGORITHM = {"SHA1", "SHA256", "SHA512"};

    private static final String TAG = OneTimePassword.class.getSimpleName();

    private final Uri uri;

    public OneTimePassword(String s) {
        this.uri = Uri.parse(s);
//        Log.d(TAG, s);
    }

    public Uri getUri() {
        return uri;
    }

    public boolean isValid() {
        if (OneTimePassword.OTP_SCHEME.equals(uri.getScheme())
                && OneTimePassword.TOTP.equals(uri.getAuthority())
                && getName() != null
                && !getName().isEmpty()) {
            try {
                getDigits();
                getAlgorithm();
                generateResponseCode(0);
                return true;
            } catch (Exception ignored) {

            }
        }
        return false;
    }

    public String getName() {
        var path = uri.getPath();
        if (path == null || !path.startsWith("/")) {
            return null;
        }
        // path is "/name", so remove leading "/", and trailing white spaces
        var name = path.substring(1).trim();
        if (name.length() == 0) {
            return null; // only white spaces.
        }
        return name;
    }

    public String getIssuer() {
        return uri.getQueryParameter(ISSUER_PARAM);
    }

    public String getSecret() {
        return uri.getQueryParameter(SECRET_PARAM).toUpperCase();
    }

    public int getPeriod() {
        var s = uri.getQueryParameter(PERIOD_PARAM);
        return (s == null || s.isEmpty()) ? DEFAULT_PERIOD : Integer.parseInt(s);
    }

    /**
     * Length of the TOTP token
     * @return
     */
    public int getDigits() {
        var s = uri.getQueryParameter(DIGITS_PARAM);
        var d = (s == null || s.isEmpty()) ? DIGITS[0] : s;
        if (!Arrays.stream(DIGITS).anyMatch(d::equals)) {
            throw new IllegalArgumentException("Invalid digits: " + d);
        }
        return Integer.parseInt(d);
    }

    public String getAlgorithm() {
        var s = uri.getQueryParameter(ALGORITHM_PARAM);
        var alg = (s == null || s.isEmpty()) ? ALGORITHM[0] : s;
        if (!Arrays.stream(ALGORITHM).anyMatch(alg::equals)) {
            throw new IllegalArgumentException("invalid algorithm: " + alg);
        }
        return alg;
    }

    public byte[] sign(byte[] data) throws NoSuchAlgorithmException, InvalidKeyException {
        var mac = Mac.getInstance("Hmac" + getAlgorithm());
        mac.init(new SecretKeySpec(Base32.decode(getSecret()), ""));
        return mac.doFinal(data);
    }

    public String generateResponseCode(long state)
            throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        var value = ByteBuffer.allocate(8).putLong(state).array();
        return generateResponseCode(value);
    }

    public String generateResponseCode(long state, byte[] challenge)
            throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        if (challenge == null) {
            return generateResponseCode(state);
        } else {
            // Allocate space for combination and store.
            var value = ByteBuffer.allocate(8 + challenge.length)
                    .putLong(state) // Write out OTP state
                    .put(challenge, 0, challenge.length) // Concatenate with challenge.
                    .array();
            return generateResponseCode(value);
        }
    }

    public String generateResponseCode(byte[] challenge)
            throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        var hash = sign(challenge);

        // Dynamically truncate the hash
        // OffsetBits are the low order bits of the last byte of the hash
        var offset = hash[hash.length - 1] & 0xF;
        // Grab a positive integer value starting at the given offset.
        var truncatedHash = hashToInt(hash, offset) & 0x7FFFFFFF;
        var pinValue = truncatedHash % BigDecimal.TEN.pow(getDigits()).intValue();
        return padOutput(pinValue);
    }

    /**
     * Grabs a positive integer value from the input array starting at
     * the given offset.
     *
     * @param bytes the array of bytes
     * @param start the index into the array to start grabbing bytes
     * @return the integer constructed from the four bytes in the array
     */
    private int hashToInt(byte[] bytes, int start) throws IOException {
        return new DataInputStream(
                new ByteArrayInputStream(
                        bytes,
                        start,
                        bytes.length - start
                )).readInt();
    }

    private String padOutput(int value) {
        var result = Integer.toString(value);
        while (result.length() < getDigits()) {
            result = "0" + result;
        }
        return result;
    }

    public record OtpState(long current, long secondsUntilNext){};

    public OtpState getState() {
        var now_s = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        return new OtpState(now_s / getPeriod(), now_s % getPeriod());
    }

    class Base32 {
        //as of https://github.com/rnewman/base32-standalone/blob/master/src/main/java/org/emergent/android/weave/client/Base32.java
        private static final String base32Chars =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        private static final int[] base32Lookup =
                { 0xFF,0xFF,0x1A,0x1B,0x1C,0x1D,0x1E,0x1F, // '0', '1', '2', '3', '4', '5', '6', '7'
                        0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF, // '8', '9', ':', ';', '<', '=', '>', '?'
                        0xFF,0x00,0x01,0x02,0x03,0x04,0x05,0x06, // '@', 'A', 'B', 'C', 'D', 'E', 'F', 'G'
                        0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E, // 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O'
                        0x0F,0x10,0x11,0x12,0x13,0x14,0x15,0x16, // 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W'
                        0x17,0x18,0x19,0xFF,0xFF,0xFF,0xFF,0xFF, // 'X', 'Y', 'Z', '[', '\', ']', '^', '_'
                        0xFF,0x00,0x01,0x02,0x03,0x04,0x05,0x06, // '`', 'a', 'b', 'c', 'd', 'e', 'f', 'g'
                        0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E, // 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o'
                        0x0F,0x10,0x11,0x12,0x13,0x14,0x15,0x16, // 'p', 'q', 'r', 's', 't', 'u', 'v', 'w'
                        0x17,0x18,0x19,0xFF,0xFF,0xFF,0xFF,0xFF  // 'x', 'y', 'z', '{', '|', '}', '~', 'DEL'
                };

        public static byte[] encode(byte[] data) throws UnsupportedEncodingException {
            String lower = encodeOriginal(data).toLowerCase();
            return lower.getBytes("US-ASCII");
        }

        public static byte[] decodeModified(String data) {
            return decode(data.replace('8', 'L').replace('9', 'O'));
        }


        /**
         * Encodes byte array to Base32 String.
         *
         * @param bytes Bytes to encode.
         * @return Encoded byte array <code>bytes</code> as a String.
         *
         */
        static public String encodeOriginal(final byte[] bytes) {
            int i = 0, index = 0, digit = 0;
            int currByte, nextByte;
            StringBuffer base32 = new StringBuffer((bytes.length + 7) * 8 / 5);

            while (i < bytes.length) {
                currByte = (bytes[i] >= 0) ? bytes[i] : (bytes[i] + 256); // unsign

                /* Is the current digit going to span a byte boundary? */
                if (index > 3) {
                    if ((i + 1) < bytes.length) {
                        nextByte =
                                (bytes[i + 1] >= 0) ? bytes[i + 1] : (bytes[i + 1] + 256);
                    } else {
                        nextByte = 0;
                    }

                    digit = currByte & (0xFF >> index);
                    index = (index + 5) % 8;
                    digit <<= index;
                    digit |= nextByte >> (8 - index);
                    i++;
                } else {
                    digit = (currByte >> (8 - (index + 5))) & 0x1F;
                    index = (index + 5) % 8;
                    if (index == 0)
                        i++;
                }
                base32.append(base32Chars.charAt(digit));
            }

            return base32.toString();
        }

        /**
         * Decodes the given Base32 String to a raw byte array.
         *
         * @param base32
         * @return Decoded <code>base32</code> String as a raw byte array.
         */
        static public byte[] decode(final String base32) {
            int i, index, lookup, offset, digit;
            byte[] bytes = new byte[base32.length() * 5 / 8];

            for (i = 0, index = 0, offset = 0; i < base32.length(); i++) {
                lookup = base32.charAt(i) - '0';

                /* Skip chars outside the lookup table */
                if (lookup < 0 || lookup >= base32Lookup.length) {
                    continue;
                }

                digit = base32Lookup[lookup];

                /* If this digit is not in the table, ignore it */
                if (digit == 0xFF) {
                    continue;
                }

                if (index <= 3) {
                    index = (index + 5) % 8;
                    if (index == 0) {
                        bytes[offset] |= digit;
                        offset++;
                        if (offset >= bytes.length)
                            break;
                    } else {
                        bytes[offset] |= digit << (8 - index);
                    }
                } else {
                    index = (index + 5) % 8;
                    bytes[offset] |= (digit >>> index);
                    offset++;

                    if (offset >= bytes.length) {
                        break;
                    }
                    bytes[offset] |= digit << (8 - index);
                }
            }
            return bytes;
        }
    }
}
