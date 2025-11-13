package com.onemoresecret.crypto

import org.spongycastle.asn1.oiw.OIWObjectIdentifiers
import org.spongycastle.asn1.x500.X500Name
import org.spongycastle.asn1.x509.AlgorithmIdentifier
import org.spongycastle.asn1.x509.AuthorityKeyIdentifier
import org.spongycastle.asn1.x509.BasicConstraints
import org.spongycastle.asn1.x509.Extension
import org.spongycastle.asn1.x509.SubjectKeyIdentifier
import org.spongycastle.asn1.x509.SubjectPublicKeyInfo
import org.spongycastle.cert.CertIOException
import org.spongycastle.cert.X509ExtensionUtils
import org.spongycastle.cert.jcajce.JcaX509CertificateConverter
import org.spongycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.spongycastle.jce.provider.BouncyCastleProvider
import org.spongycastle.operator.OperatorCreationException
import org.spongycastle.operator.bc.BcDigestCalculatorProvider
import org.spongycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant
import java.util.Date

object SelfSignedCertGenerator {
    /**
     * Generates a self signed certificate using the BouncyCastle lib.
     *
     * @param keyPair       used for signing the certificate with PrivateKey
     * @param hashAlgorithm Hash function
     * @param cn            Common Name to be used in the subject dn
     * @param days          validity period in days of the certificate
     * @return self-signed X509Certificate
     * @throws OperatorCreationException on creating a key id
     * @throws CertIOException           on building JcaContentSignerBuilder
     * @throws CertificateException      on getting certificate from provider
     */
    @Throws(OperatorCreationException::class, CertIOException::class, CertificateException::class)
    fun generate(
        keyPair: KeyPair,
        hashAlgorithm: String?,
        cn: String?,
        days: Int
    ): X509Certificate? {
        val now = Instant.now()
        val notBefore = Date.from(now)
        val notAfter = Date.from(now.plus(Duration.ofDays(days.toLong())))

        val contentSigner = JcaContentSignerBuilder(hashAlgorithm).build(keyPair.private)
        val x500Name = X500Name("CN=$cn")
        val certificateBuilder = JcaX509v3CertificateBuilder(
            x500Name,
            BigInteger.valueOf(now.toEpochMilli()),
            notBefore,
            notAfter,
            x500Name,
            keyPair.public
        )
            .addExtension(
                Extension.subjectKeyIdentifier,
                false,
                createSubjectKeyId(keyPair.public)
            )
            .addExtension(
                Extension.authorityKeyIdentifier,
                false,
                createAuthorityKeyId(keyPair.public)
            )
            .addExtension(Extension.basicConstraints, true, BasicConstraints(true))

        return JcaX509CertificateConverter().setProvider(BouncyCastleProvider())
            .getCertificate(certificateBuilder.build(contentSigner))
    }

    /**
     * Creates the hash value of the public key.
     *
     * @param publicKey of the certificate
     * @return SubjectKeyIdentifier hash
     */
    @Throws(OperatorCreationException::class)
    private fun createSubjectKeyId(publicKey: PublicKey): SubjectKeyIdentifier? {
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.encoded)
        val digCalc = BcDigestCalculatorProvider()
            .get(AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1))

        return X509ExtensionUtils(digCalc).createSubjectKeyIdentifier(publicKeyInfo)
    }

    /**
     * Creates the hash value of the authority public key.
     *
     * @param publicKey of the authority certificate
     * @return AuthorityKeyIdentifier hash
     */
    @Throws(OperatorCreationException::class)
    private fun createAuthorityKeyId(publicKey: PublicKey): AuthorityKeyIdentifier? {
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.encoded)
        val digCalc = BcDigestCalculatorProvider()
            .get(AlgorithmIdentifier(OIWObjectIdentifiers.idSHA1))

        return X509ExtensionUtils(digCalc).createAuthorityKeyIdentifier(publicKeyInfo)
    }
}
