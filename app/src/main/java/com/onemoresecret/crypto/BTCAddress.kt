package com.onemoresecret.crypto

import com.onemoresecret.crypto.Base58.decode
import org.jetbrains.annotations.Contract
import org.spongycastle.jce.ECNamedCurveTable
import org.spongycastle.jce.provider.BouncyCastleProvider
import org.spongycastle.jce.spec.ECParameterSpec
import org.spongycastle.jce.spec.ECPrivateKeySpec
import org.spongycastle.jce.spec.ECPublicKeySpec
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.Security
import java.security.Signature
import java.security.SignatureException
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.InvalidKeySpecException
import java.util.function.Function
import kotlin.math.max
import kotlin.math.min

object BTCAddress {
    private var keyPairGenerator: KeyPairGenerator? = null

    init {
        Security.insertProviderAt(BouncyCastleProvider(), 1)
        val parameterSpec = ECGenParameterSpec("secp256k1")
        try {
            keyPairGenerator = KeyPairGenerator.getInstance("EC")
            keyPairGenerator?.initialize(parameterSpec)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private val TAG: String = BTCAddress::class.java.simpleName

    private val toByte32 = Function { bi: BigInteger? ->
        val src = bi!!.toByteArray()
        /* The byte representation can be either longer than 32 bytes - because of leading zeros -
         * or shorter than 32 bytes. We must handle both cases */
        val dest = ByteArray(32)
        System.arraycopy(
            src,
            max(0, src.size - dest.size),  //skip leading zeros if longer than 32 bytes
            dest,
            max(0, dest.size - src.size),  //prepend with leading zeros if shorter than 32 bytes
            min(src.size, dest.size)
        )
        dest
    }

    private val sha256 = Function { bArr: ByteArray ->
        try {
            return@Function MessageDigest.getInstance("SHA-256").digest(bArr)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
    }

    private val ripeMD160 = Function { bArr: ByteArray ->
        try {
            return@Function MessageDigest.getInstance("RipeMD160").digest(bArr)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        }
    }

    @JvmStatic
    @Contract("_ -> new")
    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun toKeyPair(privateKey: ByteArray?): ECKeyPair {
        val ecParameterSpec = ECNamedCurveTable.getParameterSpec("secp256k1")
        val s = BigInteger(1, privateKey)

        val privateKeySpec = ECPrivateKeySpec(
            s,
            ECParameterSpec(
                ecParameterSpec.curve,
                ecParameterSpec.g,
                ecParameterSpec.n,
                ecParameterSpec.h
            )
        )

        val publicKeySpec = ECPublicKeySpec(ecParameterSpec.g.multiply(s), ecParameterSpec)
        val keyFactory = KeyFactory.getInstance("EC")
        return ECKeyPair(
            keyFactory.generatePrivate(privateKeySpec) as ECPrivateKey?,
            keyFactory.generatePublic(publicKeySpec) as ECPublicKey?
        )
    }

    @JvmStatic
    @Throws(NoSuchAlgorithmException::class, InvalidAlgorithmParameterException::class)
    fun newKeyPair(): ECKeyPair {
        //generate key pair
        val keyPair = keyPairGenerator!!.generateKeyPair()

        return ECKeyPair(keyPair.private as ECPrivateKey?, keyPair.public as ECPublicKey?)
    }

    fun toBTCAddress(publicKey: ECPublicKey): ByteArray {
        // https://gobittest.appspot.com/Address

        val ecPoint = publicKey.w
        val x = toByte32.apply(ecPoint.affineX)
        val y = toByte32.apply(ecPoint.affineY)
        val btcPublicKey = ByteArray(65)
        btcPublicKey[0] = 0x04
        System.arraycopy(x, 0, btcPublicKey, 1, x.size)
        System.arraycopy(y, 0, btcPublicKey, x.size + 1, y.size)

        //SHA-256 and RIPEMD digest
        val digest = ripeMD160.apply(
            sha256.apply(btcPublicKey)
        )

        //prepend with version byte (0x00)
        val digestWithVersionByte = ByteArray(digest.size + 1)
        System.arraycopy(digest, 0, digestWithVersionByte, 1, digest.size)

        //calculate checksum
        val checksum = sha256.apply(sha256.apply(digestWithVersionByte))

        //BTC address = version byte + digest + first 4 bytes of the checksum
        val btcAddress = ByteArray(digestWithVersionByte.size + 4)
        System.arraycopy(digestWithVersionByte, 0, btcAddress, 0, digestWithVersionByte.size)
        //append first 4 bytes of digest
        System.arraycopy(checksum, 0, btcAddress, digestWithVersionByte.size, 4)

        return btcAddress
    }

    fun toBTCKeyPair(keyPair: ECKeyPair): BTCKeyPair {
        //private key
        val s = keyPair.privateKey!!.s
        val privateKey = toByte32.apply(s)

        return BTCKeyPair(toWIF(privateKey), toBTCAddress(keyPair.publicKey!!))
    }

    /**
     * Convert private key to WIF format.
     *
     * @param privateKey Private Key to encode
     * @return WIF byte array. Encrypt it with [Base58.encode] for the readable representation.
     */
    fun toWIF(privateKey: ByteArray): ByteArray {
        //as of https://gobittest.appspot.com/PrivateKey

        //prepend with 0x80

        val withQualifier = ByteArray(privateKey.size + 1)
        withQualifier[0] = 0x80.toByte()
        System.arraycopy(privateKey, 0, withQualifier, 1, privateKey.size)

        val digest = sha256.apply(sha256.apply(withQualifier))

        val wif = ByteArray(withQualifier.size + 4)
        System.arraycopy(withQualifier, 0, wif, 0, withQualifier.size)
        //append first 4 bytes of digest
        System.arraycopy(digest, 0, wif, withQualifier.size, 4)

        return wif
    }

    fun validateWIF(wifString: String): Boolean {
        //as of https://gobittest.appspot.com/PrivateKey

        //decode Base58

        val wif = decode(wifString)

        //drop last 4 bytes
        val withQualifier = wif.copyOfRange(0, wif.size - 4)

        //calculate dual sha256
        val digest = sha256.apply(sha256.apply(withQualifier))

        //compare checksums
        return digest.copyOfRange(0, 4).contentEquals( //first 4 bytes of the digest
            wif.copyOfRange(wif.size - 4, wif.size) //last 4 bytes of WIF byte array
        )
    }

    /**
     * Restore private key from WIF
     *
     * @param wif Wallet Interchange Format as byte array (apply [Base58.decode] to WIF string first)
     */
    @JvmStatic
    fun toPrivateKey(wif: ByteArray): ByteArray {
        //drop first one and last 4 bytes
        return wif.copyOfRange(1, wif.size - 4)
    }

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchProviderException::class,
        InvalidKeyException::class,
        SignatureException::class
    )
    fun sign(data: ByteArray?, privateKey: ECPrivateKey?): ByteArray? {
        val signer = Signature.getInstance("SHA256withECDSA", "BC")
        signer.initSign(privateKey)
        signer.update(data)

        return signer.sign()
    }

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchProviderException::class,
        InvalidKeyException::class,
        SignatureException::class
    )
    fun validate(publicKey: ECPublicKey?, data: ByteArray?, signature: ByteArray?): Boolean {
        val signer = Signature.getInstance("SHA256withECDSA", "BC")
        signer.initVerify(publicKey)
        signer.update(data)
        return signer.verify(signature)
    }

    @JvmRecord
    data class ECKeyPair(val privateKey: ECPrivateKey?, val publicKey: ECPublicKey?) {
        fun toBTCKeyPair(): BTCKeyPair {
            return toBTCKeyPair(this)
        }
    }

    @JvmRecord
    data class BTCKeyPair(@JvmField val wif: ByteArray, val btcAddress: ByteArray) {
        @Contract(" -> new")
        @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        fun toECKeyPair(): ECKeyPair {
            return toKeyPair(toPrivateKey(wif))
        }

        val wifBase58: String
            get() = Base58.encode(wif)

        val btcAddressBase58: String
            get() = Base58.encode(btcAddress)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BTCKeyPair) return false

            if (!wif.contentEquals(other.wif)) return false
            if (!btcAddress.contentEquals(other.btcAddress)) return false
            if (wifBase58 != other.wifBase58) return false
            if (btcAddressBase58 != other.btcAddressBase58) return false

            return true
        }

        override fun hashCode(): Int {
            var result = wif.contentHashCode()
            result = 31 * result + btcAddress.contentHashCode()
            result = 31 * result + wifBase58.hashCode()
            result = 31 * result + btcAddressBase58.hashCode()
            return result
        }
    }
}
