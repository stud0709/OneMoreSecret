package com.onemoresecret.crypto;

import android.net.Uri;
import android.util.Log;

import org.bouncycastle.util.encoders.Base32;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class OneTimePassword {
    public static final String OTP_SCHEME = "otpauth";
    public static final String TOTP = "totp"; // time-based
    private static final String ISSUER_PARAM = "issuer";
    private static final String SECRET_PARAM = "secret";

    private static final int DEFAULT_PERIOD = 30;
    private static final String PERIOD_PARAM = "period";
    private static final int DEFAULT_DIGITS = 6;
    private static final String DIGITS_PARAM = "digits";
    private static final String ALGORITHM_PARAM = "algorithm";
    private static final String DEFAULT_ALGORITHM = "SHA1";

    private static final String TAG = OneTimePassword.class.getSimpleName();

    private final Uri uri;

    public OneTimePassword(String s) {
        this.uri = Uri.parse(s);
//        Log.d(TAG, s);
    }

    public Uri getUri() {
        return uri;
    }

    public boolean looksValid() {
        return OneTimePassword.OTP_SCHEME.equals(uri.getScheme())
                && OneTimePassword.TOTP.equals(uri.getAuthority());
    }

    public String getName() {
        String path = uri.getPath();
        if (path == null || !path.startsWith("/")) {
            return null;
        }
        // path is "/name", so remove leading "/", and trailing white spaces
        String name = path.substring(1).trim();
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
        String s = uri.getQueryParameter(PERIOD_PARAM);
        return (s == null || s.isEmpty()) ? DEFAULT_PERIOD : Integer.parseInt(s);
    }

    public int getDigits() {
        String s = uri.getQueryParameter(DIGITS_PARAM);
        int i = (s == null || s.isEmpty()) ? DEFAULT_DIGITS : Integer.parseInt(s);
        if (i > 8) throw new IllegalArgumentException("Too many digits (max. 8): " + i);
        return i;
    }

    public String getAlgorithm() {
        String s = uri.getQueryParameter(ALGORITHM_PARAM);
        String alg = (s == null || s.isEmpty()) ? DEFAULT_ALGORITHM : s;
        if (!alg.equals("SHA1") && !alg.equals("SHA256") && !alg.equals("SHA512")) {
            throw new IllegalArgumentException("invalid algorithm: " + alg);
        }
        return alg;
    }

    public byte[] sign(byte[] data) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("Hmac" + getAlgorithm());
        mac.init(new SecretKeySpec(Base32.decode(getSecret()), ""));
        return mac.doFinal(data);
    }

    public String generateResponseCode(long state)
            throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        byte[] value = ByteBuffer.allocate(8).putLong(state).array();
        return generateResponseCode(value);
    }

    public String generateResponseCode(long state, byte[] challenge)
            throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        if (challenge == null) {
            return generateResponseCode(state);
        } else {
            // Allocate space for combination and store.
            byte[] value = ByteBuffer.allocate(8 + challenge.length)
                    .putLong(state) // Write out OTP state
                    .put(challenge, 0, challenge.length) // Concatenate with challenge.
                    .array();
            return generateResponseCode(value);
        }
    }

    public String generateResponseCode(byte[] challenge)
            throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        byte[] hash = sign(challenge);

        // Dynamically truncate the hash
        // OffsetBits are the low order bits of the last byte of the hash
        int offset = hash[hash.length - 1] & 0xF;
        // Grab a positive integer value starting at the given offset.
        int truncatedHash = hashToInt(hash, offset) & 0x7FFFFFFF;
        int pinValue = truncatedHash % BigDecimal.TEN.pow(getDigits()).intValue();
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
        String result = Integer.toString(value);
        while (result.length() < getDigits()) {
            result = "0" + result;
        }
        return result;
    }

    /**
     * @return {current state, remaining seconds until next state}
     */
    public long[] getState() {
        long now_s = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        return new long[]{now_s / getPeriod(), now_s % getPeriod()};
    }
}
