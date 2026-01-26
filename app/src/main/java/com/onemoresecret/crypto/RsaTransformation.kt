package com.onemoresecret.crypto

import java.security.spec.MGF1ParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource

/**
 * See [javax.crypto.Cipher Java API](https://docs.oracle.com/javase/7/docs/api/javax/crypto/Cipher.html) for
 * the list of supported transformations
 *
 * See [javax.crypto.Cipher Android API](https://developer.android.com/reference/javax/crypto/Cipher)
 *
 */
enum class RsaTransformation(@JvmField val transformation: String, val spec: OAEPParameterSpec?) {
    RSA_ECB_PKCS1Padding("RSA/ECB/PKCS1Padding", null),
    RSA_ECB_OAEPWithSHA_1AndMGF1Padding("RSA/ECB/OAEPWithSHA-1AndMGF1Padding",
        OAEPParameterSpec(
        "SHA-1",
        "MGF1",
        MGF1ParameterSpec.SHA1,
        PSource.PSpecified.DEFAULT
    )),
    RSA_ECB_OAEPWithSHA_256AndMGF1Padding("RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
        OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT
        ));
}
