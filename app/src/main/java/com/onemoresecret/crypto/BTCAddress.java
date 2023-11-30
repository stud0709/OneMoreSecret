package com.onemoresecret.crypto;

import android.util.Log;

import androidx.annotation.NonNull;

import com.onemoresecret.Util;

import org.jetbrains.annotations.Contract;
import org.spongycastle.crypto.generators.ECKeyPairGenerator;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECKeyGenerationParameters;
import org.spongycastle.jce.ECNamedCurveTable;
import org.spongycastle.jce.spec.ECPrivateKeySpec;
import org.spongycastle.jce.spec.ECPublicKeySpec;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.function.Function;

public class BTCAddress {
    private static final String TAG = BTCAddress.class.getSimpleName();

    private static Function<BigInteger, byte[]> toByte32 = bi -> {
        var src = bi.toByteArray();
        /* The byte representation can be either longer than 32 bytes - because of leading zeros -
         * or shorter than 32 bytes. We must handle both cases */
        var dest = new byte[32];
        System.arraycopy(
                src,
                Math.max(0, src.length - dest.length), //skip leading zeros if longer than 32 bytes
                dest,
                Math.max(0, dest.length - src.length), //prepend with leading zeros if shorter than 32 bytes
                Math.min(src.length, dest.length));
        return dest;
    };

    private static final Function<byte[], byte[]> sha256 = bArr -> {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bArr);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    };

    private static final Function<byte[], byte[]> ripeMD160 = bArr -> {
        try {
            return MessageDigest.getInstance("RipeMD160").digest(bArr);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    };

    public record ECKeyPair(ECPrivateKey privateKey, ECPublicKey publicKey){
        @NonNull
        public  BTCKeyPair toBTCKeyPair() {
            return BTCAddress.toBTCKeyPair(this);
        }
    }

    @NonNull
    @Contract("_ -> new")
    public static ECKeyPair toKeyPair(byte[] privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);

        var ecParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1");
        var s = new BigInteger(1, privateKey);

        var privateKeySpec = new ECPrivateKeySpec(s,
                new org.spongycastle.jce.spec.ECParameterSpec(
                        ecParameterSpec.getCurve(),
                        ecParameterSpec.getG(),
                        ecParameterSpec.getN(),
                        ecParameterSpec.getH()));

        var publicKeySpec = new ECPublicKeySpec(ecParameterSpec.getG().multiply(s), ecParameterSpec);
        var keyFactory = KeyFactory.getInstance("EC");
        return new ECKeyPair((ECPrivateKey) keyFactory.generatePrivate(privateKeySpec), (ECPublicKey) keyFactory.generatePublic(publicKeySpec));
    }

    @NonNull
    @Contract("_ -> new")
    public static ECKeyPair newKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);

        var keyGen = new ECKeyPairGenerator();
        var ecParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1");
        var domainParams = new ECDomainParameters(
                ecParameterSpec.getCurve(),
                ecParameterSpec.getG(),
                ecParameterSpec.getN(),
                ecParameterSpec.getH(),
                ecParameterSpec.getSeed());
        var keyGenParams = new ECKeyGenerationParameters(domainParams, new SecureRandom());
        keyGen.init(keyGenParams);

        //initialize elliptic curve cryptography
        var parameterSpec = new ECGenParameterSpec("secp256k1");
        var keyPairGenerator = KeyPairGenerator.getInstance("EC");
        keyPairGenerator.initialize(parameterSpec);

        //generate key pair
        var keyPair = keyPairGenerator.generateKeyPair();

        return new ECKeyPair((ECPrivateKey) keyPair.getPrivate(), (ECPublicKey) keyPair.getPublic());
    }

    public record BTCKeyPair(String wif, String btcAddressBase58) {
        @NonNull
        @Contract(" -> new")
        public ECKeyPair toECKeyPair() throws NoSuchAlgorithmException, InvalidKeySpecException {
            return BTCAddress.toKeyPair(toPrivateKey(wif));
        }
    }

    @NonNull
    public static String toBTCAddress(@NonNull ECPublicKey publicKey) {
        Log.d(TAG, "Generating bitcoin address, steps as of https://gobittest.appspot.com/Address");

        var ecPoint = publicKey.getW();
        var x = toByte32.apply(ecPoint.getAffineX());
        var y = toByte32.apply(ecPoint.getAffineY());
        var btcPublicKey = new byte[65];
        btcPublicKey[0] = 0x04;
        System.arraycopy(x, 0, btcPublicKey, 1, x.length);
        System.arraycopy(y, 0, btcPublicKey, x.length + 1, y.length);

        Log.d(TAG, "1: " + Util.byteArrayToHex(btcPublicKey, false).toUpperCase());

        //SHA-256 and RIPEMD digest
        var digest = ripeMD160.apply(
                sha256.apply(btcPublicKey));

        Log.d(TAG, "3: " + Util.byteArrayToHex(digest, false).toUpperCase());

        //prepend with version byte (0x00)
        var digestWithVersionByte = new byte[digest.length + 1];
        System.arraycopy(digest, 0, digestWithVersionByte, 1, digest.length);

        Log.d(TAG, "4: " + Util.byteArrayToHex(digestWithVersionByte, false).toUpperCase());

        //calculate checksum
        var checksum = sha256.apply(sha256.apply(digestWithVersionByte));

        Log.d(TAG, "6: " + Util.byteArrayToHex(checksum, false).toUpperCase());

        //BTC address = version byte + digest + first 4 bytes of the checksum
        var btcAddress = new byte[digestWithVersionByte.length + 4];
        System.arraycopy(digestWithVersionByte, 0, btcAddress, 0, digestWithVersionByte.length);
        //append first 4 bytes of digest
        System.arraycopy(checksum, 0, btcAddress, digestWithVersionByte.length, 4);

        Log.d(TAG, "8: " + Util.byteArrayToHex(btcAddress, false).toUpperCase());

        var btcAddressBase58 = Base58.encode(btcAddress);

        Log.d(TAG, "9: " + btcAddressBase58);

        return btcAddressBase58;
    }

    @NonNull
    public static BTCKeyPair toBTCKeyPair(@NonNull ECKeyPair keyPair) {
        //private key
        var s = keyPair.privateKey.getS();
        var privateKey = toByte32.apply(s);

        return new BTCKeyPair(toWIF(privateKey), toBTCAddress(keyPair.publicKey));
    }

    @NonNull
    public static String toWIF(@NonNull byte[] privateKey) {
        //as of https://gobittest.appspot.com/PrivateKey

        //prepend with 0x80
        var withQualifier = new byte[privateKey.length + 1];
        withQualifier[0] = (byte) 0x80;
        System.arraycopy(privateKey, 0, withQualifier, 1, privateKey.length);

        var digest = sha256.apply(sha256.apply(withQualifier));

        var wif = new byte[withQualifier.length + 4];
        System.arraycopy(withQualifier, 0, wif, 0, withQualifier.length);
        //append first 4 bytes of digest
        System.arraycopy(digest, 0, wif, withQualifier.length, 4);

        //encode as Base58
        return Base58.encode(wif);
    }

    public static boolean validateWIF(String wifString) {
        //as of https://gobittest.appspot.com/PrivateKey

        //decode Base58
        var wif = Base58.decode(wifString);

        //drop last 4 bytes
        var withQualifier = Arrays.copyOfRange(wif, 0, wif.length - 4);

        //calculate dual sha256
        var digest = sha256.apply(sha256.apply(withQualifier));

        //compare checksums
        return Arrays.equals(
                Arrays.copyOfRange(digest, 0, 4), //first 4 bytes of the digest
                Arrays.copyOfRange(wif, wif.length - 4, wif.length) //last 4 bytes of WIF byte array
        );
    }

    @NonNull
    public static byte[] toPrivateKey(String wifString) {
        //decode Base58
        var wif = Base58.decode(wifString);

        //drop first and last 4 bytes
        return Arrays.copyOfRange(wif, 1, wif.length - 4);
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
        @NonNull
        public static String encode(@NonNull byte[] input) {
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

        @NonNull
        public static byte[] decode(@NonNull String input) throws IllegalArgumentException {
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
        private static byte divmod(@NonNull byte[] number, int firstDigit, int base, int divisor) {
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
