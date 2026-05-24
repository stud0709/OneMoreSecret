package com.onemoresecret.msg_fragment_plugins

import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import com.onemoresecret.HiddenTextFragment
import com.onemoresecret.KeyRequestPairingFragment
import com.onemoresecret.MessageFragment
import com.onemoresecret.Oms4webUnlock
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OmsDataOutputStream
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import com.onemoresecret.Util
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil
import com.onemoresecret.crypto.RsaTransformation
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Arrays
import java.util.Base64
import javax.crypto.Cipher

open class MsgPluginKeyRequest(
    messageFragment: MessageFragment,
    messageData: ByteArray
) : MessageFragmentPlugin(messageFragment) {

    protected var msgReference: String? = null
    protected var rsaPublicKeyMaterial: ByteArray? = null
    protected var encryptedAesKey: ByteArray? = null
    protected var applicationId: Int = 0
    protected var rsaTransformationResponse: RsaTransformation? = null

    init {
        ByteArrayInputStream(messageData).use { bais ->
            OmsDataInputStream(bais).use { dataInputStream ->
                // (1) Application ID
                applicationId = dataInputStream.readUnsignedShort()
                Log.d(TAG, "Application ID: $applicationId")

                assert(
                    listOf(
                        MessageComposer.APPLICATION_KEY_REQUEST,
                        MessageComposer.APPLICATION_KEY_REQUEST_PAIRING,
                        MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST
                    ).contains(applicationId)
                )

                when (applicationId) {
                    MessageComposer.APPLICATION_KEY_REQUEST,
                    MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST -> {
                        // (2) reference (e.g. file name)
                        msgReference = dataInputStream.readString()
                        Log.d(TAG, "Reference: $msgReference")

                        // (3) RSA public key
                        rsaPublicKeyMaterial = dataInputStream.readByteArray()
                        Log.d(TAG, "RSA public key: ${Util.byteArrayToHex(rsaPublicKeyMaterial!!)}")

                        // (4) fingerprint of the requested key
                        fingerprint = dataInputStream.readByteArray()
                        Log.d(TAG, "Fingerprint: ${Util.byteArrayToHex(fingerprint!!)}")

                        // (5) transformation for decryption (as specified by the encrypted file)
                        rsaTransformation = RsaTransformation.entries[dataInputStream.readUnsignedShort()]
                        Log.d(
                            TAG,
                            "Transformation (file): ${rsaTransformation.ordinal} = ${rsaTransformation.transformation}"
                        )

                        // (6) transformation index for the key response - the requester specifies the transformation it supports
                        rsaTransformationResponse = RsaTransformation.entries[dataInputStream.readUnsignedShort()]
                        Log.d(
                            TAG,
                            "Transformation response: ${rsaTransformationResponse!!.ordinal} = ${rsaTransformationResponse!!.transformation}"
                        )

                        // (7) AES key subject to decryption with RSA key specified by fingerprint at (4)
                        encryptedAesKey = dataInputStream.readByteArray()
                        Log.d(TAG, "encrypted AES key: ${Util.byteArrayToHex(encryptedAesKey!!)}")
                    }

                    MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                        // (2) reference (file name)
                        msgReference = dataInputStream.readString()
                        Log.d(TAG, "Reference: $msgReference")

                        // (3) fingerprint of the requested RSA key (from the file header)
                        fingerprint = dataInputStream.readByteArray()
                        Log.d(TAG, "Fingerprint: ${Util.byteArrayToHex(fingerprint!!)}")

                        // (5) RSA transformation index for decryption
                        rsaTransformation = RsaTransformation.entries[dataInputStream.readUnsignedShort()]
                        Log.d(
                            TAG,
                            "Transformation (file): ${rsaTransformation.ordinal} = ${rsaTransformation.transformation}"
                        )

                        // (6) encrypted AES key from the file header
                        encryptedAesKey = dataInputStream.readByteArray()
                        Log.d(TAG, "encrypted AES key: ${Util.byteArrayToHex(encryptedAesKey!!)}")
                    }
                }
            }
        }
    }

    override fun getMessageView(): Fragment {
        if (messageView == null) {
            messageView = HiddenTextFragment()
            context.mainExecutor.execute {
                (messageView as HiddenTextFragment).text = ""
            }
        }
        return messageView!!
    }

    override fun getOutputView(): FragmentWithNotificationBeforePause {
        if (applicationId == MessageComposer.APPLICATION_KEY_REQUEST)
            return super.getOutputView()

        if (applicationId == MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST) {
            outputView = Oms4webUnlock()
        }

        if (outputView == null) {
            outputView = KeyRequestPairingFragment()
        }
        return outputView!!
    }

    override fun getReference(): String? {
        return msgReference?.let { String.format(context.getString(R.string.reference), it) }
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        val masterRsaCipher = requireNotNull(result.cryptoObject).cipher
        assert(masterRsaCipher != null)

        val keyStoreEntry = CryptographyManager().getByFingerprint(fingerprint, preferences)
        assert(keyStoreEntry != null)

        val userRsaCipher = CryptographyManager().getInitializedUserRsaCipher(
            masterRsaCipher!!,
            keyStoreEntry!!,
            rsaTransformation,
            Cipher.DECRYPT_MODE
        )

        try {
            // decrypt AES key
            val aesKeyMaterial = userRsaCipher.cipher.doFinal(encryptedAesKey)
            userRsaCipher.wipe.invoke() // wipe User RSA key data

            when (applicationId) {
                MessageComposer.APPLICATION_KEY_REQUEST,
                MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST -> {
                    // encrypt AES key with the provided public key
                    val rsaEncryptedAesKey = RSAUtil.process(
                        Cipher.ENCRYPT_MODE,
                        rsaPublicKeyMaterial!!,
                        rsaTransformationResponse!!,
                        aesKeyMaterial
                    )

                    Arrays.fill(aesKeyMaterial, 0.toByte()) // wipe AES key data

                    ByteArrayOutputStream().use { baos ->
                        OmsDataOutputStream(baos).use { dataOutputStream ->

                            Log.d(TAG, "Creating key response")
                            // (1) Application identifier
                            dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_KEY_RESPONSE)
                            Log.d(TAG, "Application-ID: ${MessageComposer.APPLICATION_KEY_RESPONSE}")

                            // (2) RSA encrypted AES key
                            dataOutputStream.writeByteArray(rsaEncryptedAesKey)
                            Log.d(TAG, "encrypted AES key: ${Util.byteArrayToHex(rsaEncryptedAesKey)}")

                            val base64Message = Base64.getEncoder().encodeToString(baos.toByteArray())

                            val hiddenTextFragment = messageView as HiddenTextFragment

                            if (applicationId == MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST) {
                                hiddenTextFragment.text = context.getString(R.string.ready_to_unlock_oms4web)
                                (outputView as Oms4webUnlock).setMessage(base64Message)
                            } else {
                                hiddenTextFragment.text = String.format(context.getString(R.string.key_response_is_ready), msgReference)

                                (outputView as OutputFragment).setMessage(
                                    base64Message + "\n", /* hit ENTER at the end signalling omsCompanion to resume */
                                    context.getString(R.string.key_response)
                                )
                            }
                            activity.invalidateOptionsMenu()
                        }
                    }
                }

                MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                    val hiddenTextFragment = messageView as HiddenTextFragment
                    hiddenTextFragment.text = String.format(context.getString(R.string.key_response_is_ready), msgReference)
                    ByteArrayOutputStream().use { baos ->
                        OmsDataOutputStream(baos).use { dataOutputStream ->
                            dataOutputStream.writeByteArray(aesKeyMaterial)
                            Arrays.fill(aesKeyMaterial, 0.toByte())
                            (getOutputView() as KeyRequestPairingFragment).replyState = baos.toByteArray()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Util.printStackTrace(e)
            context.mainExecutor.execute {
                Toast.makeText(
                    context,
                    e.message ?: String.format(context.getString(R.string.authentication_failed_s), e.javaClass.name),
                    Toast.LENGTH_SHORT
                ).show()
                Util.discardBackStack(messageFragment)
            }
        }
    }
}
