package com.onemoresecret.crypto;

import android.util.Log;

import com.onemoresecret.Util;

import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.jce.ECNamedCurveTable;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.function.Function;

public class BTCAddress {
    private static final String TAG = BTCAddress.class.getSimpleName();
    private static final Function<BigInteger, String> toHex64 = bi -> {
        var s = bi.toString(16);
        while (s.length() < 64) s = "0" + s;
        return s;
    };

    private static final Function<byte[], byte[]> sha256 = bArr -> {
        MessageDigest sha256 = null;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return sha256.digest(bArr);
    };

    private static final Function<byte[], byte[]> ripeMD160 = bArr -> {
        MessageDigest sha256 = null;
        try {
            sha256 = MessageDigest.getInstance("RipeMD160");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return sha256.digest(bArr);
    };

    public record ECDSAKeyPair(String privateKey, String btcAddressBase58) {
    }

    public static ECDSAKeyPair newECDSAKeyPair() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException {
        Log.d(TAG, "Generating bitcoin address, steps as of https://gobittest.appspot.com/Address");

        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
        var keyGen = new ECKeyPairGenerator();
        var curve = ECNamedCurveTable.getParameterSpec("secp256k1");
        var domainParams = new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH(), curve.getSeed());
        var keyGenParams = new ECKeyGenerationParameters(domainParams, new SecureRandom());
        keyGen.init(keyGenParams);

        //initialize elliptic curve cryptography
        var parameterSpec = new ECGenParameterSpec("secp256k1");
        var keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(parameterSpec);

        //generate key pair
        var keyPair = keyPairGenerator.generateKeyPair();

        //private key
        var s = ((ECPrivateKey) keyPair.getPrivate()).getS();
        var privateKey = toHex64.apply(s);

//        Log.d(TAG, "0: " + privateKey);

        //public key
        var publicKey = (ECPublicKey) keyPair.getPublic();
        var ecPoint = publicKey.getW();
        var x = toHex64.apply(ecPoint.getAffineX());
        var y = toHex64.apply(ecPoint.getAffineY());
        var btcPublicKey = "04" + x + y;

        Log.d(TAG, "1: " + btcPublicKey);

        //SHA-256 and RIPEMD digest
        var digest = ripeMD160.apply(
                sha256.apply(Util.hexToByteArray(btcPublicKey)));

        Log.d(TAG, "3: " + Util.byteArrayToHex(digest, false));

        //prepend with version byte (0x00)
        var digestWithVersionByte = new byte[digest.length + 1];
        System.arraycopy(digest, 0, digestWithVersionByte, 1, digest.length);

        Log.d(TAG, "4: " + Util.byteArrayToHex(digestWithVersionByte, false));

        //calculate checksum
        var checksum = sha256.apply(sha256.apply(digestWithVersionByte));

        Log.d(TAG, "6: " + Util.byteArrayToHex(checksum, false));

        //BTC address = version byte + digest + first 4 bytes of the checksum
        var btcAddress = new byte[digestWithVersionByte.length + 4];
        System.arraycopy(digestWithVersionByte, 0, btcAddress, 0, digestWithVersionByte.length);
        System.arraycopy(checksum, 0, btcAddress, digestWithVersionByte.length, 4);

        Log.d(TAG, "8: " + Util.byteArrayToHex(btcAddress, false));

        var btcAddressBase58 = Base58.encode(btcAddress);

        Log.d(TAG, "9: " + btcAddressBase58);

        return new ECDSAKeyPair(privateKey, btcAddressBase58);
    }

    class Base58 {
        //as of https://github.com/bitcoinj/bitcoinj/blob/master/core/src/main/java/org/bitcoinj/base/Base58.java
        public static final char[] ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz".toCharArray();
        private static final char ENCODED_ZERO = ALPHABET[0];
        private static final int[] INDEXES = new int[128];

        static {
            Arrays.fill(INDEXES, -1);
            for (int i = 0; i < ALPHABET.length; i++) {
                INDEXES[ALPHABET[i]] = i;
            }
        }

        /**
         * Encodes the given bytes as a base58 string (no checksum is appended).
         *
         * @param input the bytes to encode
         * @return the base58-encoded string
         */
        public static String encode(byte[] input) {
            if (input.length == 0) {
                return "";
            }
            // Count leading zeros.
            int zeros = 0;
            while (zeros < input.length && input[zeros] == 0) {
                ++zeros;
            }
            // Convert base-256 digits to base-58 digits (plus conversion to ASCII characters)
            input = Arrays.copyOf(input, input.length); // since we modify it in-place
            char[] encoded = new char[input.length * 2]; // upper bound
            int outputStart = encoded.length;
            for (int inputStart = zeros; inputStart < input.length; ) {
                encoded[--outputStart] = ALPHABET[divmod(input, inputStart, 256, 58)];
                if (input[inputStart] == 0) {
                    ++inputStart; // optimization - skip leading zeros
                }
            }
            // Preserve exactly as many leading encoded zeros in output as there were leading zeros in input.
            while (outputStart < encoded.length && encoded[outputStart] == ENCODED_ZERO) {
                ++outputStart;
            }
            while (--zeros >= 0) {
                encoded[--outputStart] = ENCODED_ZERO;
            }
            // Return encoded string (including encoded leading zeros).
            return new String(encoded, outputStart, encoded.length - outputStart);
        }

        public static byte[] decode(String input) throws IllegalArgumentException {
            if (input.length() == 0) {
                return new byte[0];
            }
            // Convert the base58-encoded ASCII chars to a base58 byte sequence (base58 digits).
            byte[] input58 = new byte[input.length()];
            for (int i = 0; i < input.length(); ++i) {
                char c = input.charAt(i);
                int digit = c < 128 ? INDEXES[c] : -1;
                if (digit < 0) {
                    throw new IllegalArgumentException("Invalid character");
                }
                input58[i] = (byte) digit;
            }
            // Count leading zeros.
            int zeros = 0;
            while (zeros < input58.length && input58[zeros] == 0) {
                ++zeros;
            }
            // Convert base-58 digits to base-256 digits.
            byte[] decoded = new byte[input.length()];
            int outputStart = decoded.length;
            for (int inputStart = zeros; inputStart < input58.length; ) {
                decoded[--outputStart] = divmod(input58, inputStart, 58, 256);
                if (input58[inputStart] == 0) {
                    ++inputStart; // optimization - skip leading zeros
                }
            }
            // Ignore extra leading zeroes that were added during the calculation.
            while (outputStart < decoded.length && decoded[outputStart] == 0) {
                ++outputStart;
            }
            // Return decoded data (including original number of leading zeros).
            return Arrays.copyOfRange(decoded, outputStart - zeros, decoded.length);
        }

        /**
         * Divides a number, represented as an array of bytes each containing a single digit
         * in the specified base, by the given divisor. The given number is modified in-place
         * to contain the quotient, and the return value is the remainder.
         *
         * @param number     the number to divide
         * @param firstDigit the index within the array of the first non-zero digit
         *                   (this is used for optimization by skipping the leading zeros)
         * @param base       the base in which the number's digits are represented (up to 256)
         * @param divisor    the number to divide by (up to 256)
         * @return the remainder of the division operation
         */
        private static byte divmod(byte[] number, int firstDigit, int base, int divisor) {
            // this is just long division which accounts for the base of the input digits
            int remainder = 0;
            for (int i = firstDigit; i < number.length; i++) {
                int digit = (int) number[i] & 0xFF;
                int temp = remainder * base + digit;
                number[i] = (byte) (temp / divisor);
                remainder = temp % divisor;
            }
            return (byte) remainder;
        }
    }
}
