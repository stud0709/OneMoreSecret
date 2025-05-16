package com.onemoresecret.msg_fragment_plugins

import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import com.onemoresecret.HiddenTextFragment
import com.onemoresecret.KeyRequestPairingFragment
import com.onemoresecret.MessageFragment
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OmsDataOutputStream
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtils.getRsaTransformation
import com.onemoresecret.crypto.RSAUtils.getRsaTransformationIdx
import com.onemoresecret.crypto.RSAUtils.process
import com.onemoresecret.crypto.RSAUtils.restorePublicKey
import com.onemoresecret.crypto.RsaTransformation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.spec.InvalidKeySpecException
import java.util.Arrays
import java.util.Base64
import java.util.Objects
import javax.crypto.Cipher

class MsgPluginKeyRequest(messageFragment: MessageFragment, messageData: ByteArray?) :
    MessageFragmentPlugin(messageFragment) {
    override var reference: String? = null
        get() = String.format(context.getString(R.string.reference), field)

    protected var rsaPublicKey: PublicKey? = null
    protected var cipherText: ByteArray? = null
    protected var applicationId: Int = 0

    init {
        try {
            ByteArrayInputStream(messageData).use { bais ->
                OmsDataInputStream(bais).use { dataInputStream ->

                    //(1) Application ID
                    applicationId = dataInputStream.readUnsignedShort()

                    assert(
                        listOf(
                            MessageComposer.APPLICATION_KEY_REQUEST,
                            MessageComposer.APPLICATION_KEY_REQUEST_PAIRING
                        ).contains(applicationId)
                    )
                    when (applicationId) {
                        MessageComposer.APPLICATION_KEY_REQUEST -> {
                            //(2) reference (e.g. file name)
                            reference = dataInputStream.readString()

                            //(3) RSA public key
                            rsaPublicKey = restorePublicKey(dataInputStream.readByteArray())

                            //(4) fingerprint of the requested key
                            fingerprint = dataInputStream.readByteArray()

                            //(5) transformation index for decryption
                            rsaTransformation =
                                RsaTransformation.entries[dataInputStream.readUnsignedShort()].transformation

                            //(6) AES key subject to decryption with RSA key specified by fingerprint at (4)
                            cipherText = dataInputStream.readByteArray()
                        }

                        MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                            // (2) reference (file name)
                            reference = dataInputStream.readString()

                            // (3) fingerprint of the requested RSA key (from the file header)
                            fingerprint = dataInputStream.readByteArray()

                            // (5) RSA transformation index for decryption
                            rsaTransformation =
                                RsaTransformation.entries[dataInputStream.readUnsignedShort()].transformation

                            // (6) encrypted AES key from the file header
                            cipherText = dataInputStream.readByteArray()
                        }
                    }
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: InvalidKeySpecException) {
            throw RuntimeException(e)
        }
    }

    override fun getMessageView(): Fragment? {
        if (messageView == null) {
            messageView = HiddenTextFragment()
            context.mainExecutor.execute { (messageView as HiddenTextFragment).setText("") }
        }
        return messageView
    }

    override fun getOutputView(): FragmentWithNotificationBeforePause? {
        if (applicationId == MessageComposer.APPLICATION_KEY_REQUEST) return super.getOutputView()

        if (outputView == null) {
            outputView = KeyRequestPairingFragment()
        }
        return outputView
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        val cipher = result.cryptoObject!!.cipher

        try {
            checkNotNull(cipher)

            //decrypt AES key
            val aesKeyMaterial = cipher.doFinal(cipherText)

            when (applicationId) {
                MessageComposer.APPLICATION_KEY_REQUEST -> { //encrypt AES key with the provided public key
                    val rsaEncryptedAesKey = process(
                        Cipher.ENCRYPT_MODE,
                        rsaPublicKey,
                        getRsaTransformation(preferences).transformation,
                        aesKeyMaterial
                    )

                    ByteArrayOutputStream().use { baos ->
                        OmsDataOutputStream(baos).use { dataOutputStream ->

                            // (1) Application identifier
                            dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_KEY_RESPONSE)

                            // (2) RSA transformation
                            dataOutputStream.writeUnsignedShort(getRsaTransformationIdx(preferences))

                            // (3) RSA encrypted AES key
                            dataOutputStream.writeByteArray(rsaEncryptedAesKey)

                            val base64Message =
                                Base64.getEncoder().encodeToString(baos.toByteArray())

                            val hiddenTextFragment = messageView as HiddenTextFragment?
                            hiddenTextFragment!!.setText(
                                String.format(
                                    context.getString(R.string.key_response_is_ready),
                                    reference
                                )
                            )

                            (outputView as OutputFragment).setMessage(
                                base64Message + "\n",  /* hit ENTER at the end signalling omsCompanion to resume */
                                context.getString(R.string.key_response)
                            )
                            activity.invalidateOptionsMenu()
                        }
                    }
                }

                MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                    val hiddenTextFragment = messageView as HiddenTextFragment?
                    hiddenTextFragment!!.setText(
                        String.format(
                            context.getString(R.string.key_response_is_ready),
                            reference
                        )
                    )
                    ByteArrayOutputStream().use { baos ->
                        OmsDataOutputStream(baos).use { dataOutputStream ->
                            dataOutputStream.writeByteArray(aesKeyMaterial)
                            (getOutputView() as KeyRequestPairingFragment).setReply(baos.toByteArray())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            context.mainExecutor.execute {
                Toast.makeText(
                    context,
                    if (e.message == null) String.format(
                        context.getString(R.string.authentication_failed_s),
                        e.javaClass.name
                    ) else e.message,
                    Toast.LENGTH_SHORT
                ).show()
                discardBackStack(messageFragment)
            }
        }
    }
}
