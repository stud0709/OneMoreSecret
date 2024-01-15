package com.onemoresecret.crypto;

import android.util.Log;

import androidx.annotation.NonNull;

import com.onemoresecret.Util;

import org.jetbrains.annotations.Contract;
import org.spongycastle.jce.ECNamedCurveTable;
import org.spongycastle.jce.spec.ECPrivateKeySpec;
import org.spongycastle.jce.spec.ECPublicKeySpec;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.function.Function;

public class BTCAddress {
    private static KeyPairGenerator keyPairGenerator;

    static {
        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
        var parameterSpec = new ECGenParameterSpec("secp256k1");
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("EC");
            keyPairGenerator.initialize(parameterSpec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final String TAG = BTCAddress.class.getSimpleName();

    private static final Function<BigInteger, byte[]> toByte32 = bi -> {
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

    public record ECKeyPair(ECPrivateKey privateKey, ECPublicKey publicKey) {
        @NonNull
        public BTCKeyPair toBTCKeyPair() {
            return BTCAddress.toBTCKeyPair(this);
        }
    }

    @NonNull
    @Contract("_ -> new")
    public static ECKeyPair toKeyPair(byte[] privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
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
    public static ECKeyPair newKeyPair() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        //generate key pair
        var keyPair = keyPairGenerator.generateKeyPair();

        return new ECKeyPair((ECPrivateKey) keyPair.getPrivate(), (ECPublicKey) keyPair.getPublic());
    }

    public record BTCKeyPair(byte[] wif, byte[] btcAddress) {
        @NonNull
        @Contract(" -> new")
        public ECKeyPair toECKeyPair() throws NoSuchAlgorithmException, InvalidKeySpecException {
            return BTCAddress.toKeyPair(toPrivateKey(wif));
        }

        public String getWifBase58() {
            return Base58.encode(wif);
        }

        public String getBtcAddressBase58() {
            return Base58.encode(btcAddress);
        }
    }

    @NonNull
    public static byte[] toBTCAddress(@NonNull ECPublicKey publicKey) {
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

        return btcAddress;
    }

    @NonNull
    public static BTCKeyPair toBTCKeyPair(@NonNull ECKeyPair keyPair) {
        //private key
        var s = keyPair.privateKey.getS();
        var privateKey = toByte32.apply(s);

        return new BTCKeyPair(toWIF(privateKey), toBTCAddress(keyPair.publicKey));
    }

    /**
     * Convert private key to WIF format.
     *
     * @param privateKey Private Key to encode
     * @return WIF byte array. Encrypt it with {@link Base58#encode(byte[])} for the readable representation.
     */
    @NonNull
    public static byte[] toWIF(@NonNull byte[] privateKey) {
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

        return wif;
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

    /**
     * Restore private key from WIF
     *
     * @param wif Wallet Interchange Format as byte array (apply {@link Base58#decode(String)} to WIF string first)
     */

    public static byte[] toPrivateKey(byte[] wif) {
        //drop first one and last 4 bytes
        return Arrays.copyOfRange(wif, 1, wif.length - 4);
    }

}
