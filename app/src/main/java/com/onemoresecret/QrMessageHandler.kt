package com.onemoresecret

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.navigation.fragment.NavHostFragment
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.OneTimePassword
import java.io.ByteArrayInputStream
import java.util.Arrays
import java.util.NoSuchElementException
import javax.crypto.Cipher

class QrMessageHandler(
    private val fragment: QRFragment,
    private val cryptographyManager: CryptographyManager
) {
    companion object {
        private val TAG = QrMessageHandler::class.java.simpleName
    }

    fun onMessage(message: String, callSetRecent: Boolean) {
        if (fragment.messageReceived.get()) return
        fragment.messageReceived.set(true)

        val bArr = MessageComposer.decode(message)
        if (bArr == null) {
            Toast.makeText(
                fragment.requireContext(),
                fragment.getString(R.string.could_not_decode),
                Toast.LENGTH_LONG
            ).show()
            fragment.messageReceived.set(false)
            return
        }

        try {
            ByteArrayInputStream(bArr).use { bais ->
                OmsDataInputStream(bais).use { dataInputStream ->
                    val applicationId = dataInputStream.readUnsignedShort()
                    val bundle = Bundle().apply {
                        putByteArray(QRFragment.ARG_MESSAGE, bArr)
                        putInt(QRFragment.ARG_APPLICATION_ID, applicationId)
                    }
                    val navController = NavHostFragment.findNavController(fragment)

                    if (OneTimePassword(message).valid) {
                        Log.d(TAG, "calling " + TotpImportFragment::class.java.simpleName)
                        navController.navigate(R.id.action_QRFragment_to_TotpImportFragment, bundle)
                    } else {
                        Log.d(TAG, String.format("Application-ID: %d", applicationId))

                        var closeSocketWaitingForReply = true

                        when (applicationId) {
                            MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER -> {
                                Log.d(TAG, "calling " + KeyImportFragment::class.java.simpleName)
                                navController.navigate(
                                    R.id.action_QRFragment_to_keyImportFragment,
                                    bundle
                                )
                            }
                            MessageComposer.APPLICATION_KEY_REQUEST,
                            MessageComposer.APPLICATION_KEY_REQUEST_PAIRING,
                            MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST -> {
                                closeSocketWaitingForReply = false
                                fragment.runPinProtected(
                                    onSuccess = {
                                        Log.d(TAG, "calling " + MessageFragment::class.java.simpleName)
                                        navController.navigate(
                                            R.id.action_QRFragment_to_MessageFragment,
                                            bundle
                                        )
                                    },
                                    onCancel = {
                                        (fragment.requireActivity() as MainActivity).sendReplyViaSocket(ByteArray(0), true)
                                        fragment.messageReceived.set(false)
                                    },
                                    evaluateNextPinRequestTimestamp = true
                                )
                            }
                            MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED,
                            MessageComposer.APPLICATION_TOTP_URI_DEPRECATED,
                            MessageComposer.APPLICATION_RSA_AES_GENERIC -> {
                                dataInputStream.reset()
                                val rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream)
                                val cipherText = dataInputStream.readByteArray()

                                fragment.runPinProtected(
                                    onSuccess = {
                                        showBiometricPromptForDecryption(
                                            rsaAesEnvelope,
                                            getAuthenticationCallback(
                                                rsaAesEnvelope,
                                                cipherText,
                                                if (callSetRecent) message else null
                                            )
                                        )
                                    },
                                    onCancel = {
                                        (fragment.requireActivity() as MainActivity).sendReplyViaSocket(ByteArray(0), true)
                                        fragment.messageReceived.set(false)
                                    },
                                    evaluateNextPinRequestTimestamp = true
                                )
                            }
                            else -> Log.e(TAG, "No processor defined for application ID " + Integer.toHexString(applicationId))
                        }

                        if (closeSocketWaitingForReply) {
                            (fragment.requireActivity() as MainActivity).sendReplyViaSocket(ByteArray(0), true)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Util.printStackTrace(ex)
            fragment.messageReceived.set(false)
            (fragment.requireActivity() as MainActivity).sendReplyViaSocket(ByteArray(0), true)
        }
    }

    private fun showBiometricPromptForDecryption(
        rsaAesEnvelope: MessageComposer.RsaAesEnvelope,
        authenticationCallback: BiometricPrompt.AuthenticationCallback
    ) {
        try {
            val keyStoreEntry = cryptographyManager.getByFingerprint(rsaAesEnvelope.fingerprint, fragment.preferences)
                ?: throw NoSuchElementException(
                    String.format(
                        fragment.requireContext().getString(R.string.no_key_found),
                        Util.byteArrayToHex(rsaAesEnvelope.fingerprint)
                    )
                )

            val biometricPrompt = BiometricPrompt(fragment.requireActivity(), authenticationCallback)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(fragment.requireContext().getString(R.string.prompt_info_title))
                .setSubtitle(
                    String.format(
                        fragment.requireContext().getString(R.string.prompt_info_subtitle),
                        keyStoreEntry.alias
                    )
                )
                .setDescription(fragment.requireContext().getString(R.string.prompt_info_description))
                .setNegativeButtonText(fragment.requireContext().getString(android.R.string.cancel))
                .setConfirmationRequired(false)
                .build()

            val cipher = cryptographyManager.getInitializedMasterRsaCipher(Cipher.DECRYPT_MODE)

            fragment.requireContext().mainExecutor.execute {
                biometricPrompt.authenticate(
                    promptInfo,
                    BiometricPrompt.CryptoObject(cipher)
                )
            }
        } catch (ex: Exception) {
            fragment.messageReceived.set(false)
            Util.printStackTrace(ex)
            fragment.requireContext().mainExecutor.execute {
                Toast.makeText(
                    fragment.requireContext(),
                    ex.message ?: ex.javaClass.name,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getAuthenticationCallback(
        rsaAesEnvelope: MessageComposer.RsaAesEnvelope,
        cipherText: ByteArray,
        optOriginalMessage: String?
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                fragment.messageReceived.set(false)
                val activity = fragment.requireActivity() as MainActivity
                Thread { activity.sendReplyViaSocket(ByteArray(0), true) }.start()
                fragment.nextPinRequestTimestamp = 0
                Thread { OmsFileProvider.purgeTmp(fragment.requireContext()) }.start()
                Log.d(TAG, String.format("Authentication failed: %s (%s)", errString, errorCode))
                fragment.requireContext().mainExecutor.execute {
                    Toast.makeText(fragment.requireContext(), "$errString ($errorCode)", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val masterRsaCipher = result.cryptoObject?.cipher!!
                val keyStoreEntry = cryptographyManager.getByFingerprint(rsaAesEnvelope.fingerprint, fragment.preferences)!!

                val userRsaCipherBox = cryptographyManager.getInitializedUserRsaCipher(
                    masterRsaCipher,
                    keyStoreEntry,
                    rsaAesEnvelope.rsaTransformation,
                    Cipher.DECRYPT_MODE
                )

                try {
                    val aesSecretKeyData = userRsaCipherBox.cipher.doFinal(rsaAesEnvelope.encryptedAesSecretKey)
                    userRsaCipherBox.wipe.invoke() // wipe User RSA key data

                    val payload = AESUtil.process(
                        Cipher.DECRYPT_MODE,
                        cipherText,
                        aesSecretKeyData,
                        rsaAesEnvelope.iv,
                        rsaAesEnvelope.aesTransformation
                    )

                    Arrays.fill(aesSecretKeyData, 0.toByte()) // wipe AES key data

                    afterDecrypt(rsaAesEnvelope, payload, optOriginalMessage)
                } catch (ex: Exception) {
                    Util.printStackTrace(ex)
                    fragment.messageReceived.set(false)
                    Toast.makeText(
                        fragment.requireActivity(),
                        ex.message ?: String.format(
                            fragment.requireContext().getString(R.string.authentication_failed_s),
                            ex.javaClass.name
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAuthenticationFailed() {
                fragment.messageReceived.set(false)
                fragment.nextPinRequestTimestamp = 0
                Log.d(TAG, "User biometrics rejected")
                fragment.requireContext().mainExecutor.execute {
                    Toast.makeText(
                        fragment.requireContext(),
                        fragment.requireContext().getString(R.string.auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun afterDecrypt(
        rsaAesEnvelope: MessageComposer.RsaAesEnvelope,
        payload: ByteArray,
        optOriginalMessage: String?
    ) {
        ByteArrayInputStream(payload).use { bais ->
            OmsDataInputStream(bais).use { dataInputStream ->
                val bundle = Bundle().apply {
                    putByteArray(QRFragment.ARG_MESSAGE, payload)
                }
                val navController = NavHostFragment.findNavController(fragment)

                when (rsaAesEnvelope.applicationId) {
                    MessageComposer.APPLICATION_RSA_AES_GENERIC -> {
                        val applicationId = dataInputStream.readUnsignedShort()
                        Log.d(TAG, "payload AI $applicationId")
                        bundle.putInt(QRFragment.ARG_APPLICATION_ID, applicationId)

                        when (applicationId) {
                            MessageComposer.APPLICATION_BITCOIN_ADDRESS,
                            MessageComposer.APPLICATION_ENCRYPTED_MESSAGE,
                            MessageComposer.APPLICATION_ENCRYPTED_OTP,
                            MessageComposer.APPLICATION_TOTP_URI -> {
                                bundle.putByteArray(QRFragment.ARG_MESSAGE, dataInputStream.readByteArray())
                                Log.d(TAG, "calling " + MessageFragment::class.java.simpleName)
                                navController.navigate(R.id.action_QRFragment_to_MessageFragment, bundle)
                            }
                            MessageComposer.APPLICATION_WIFI_PAIRING -> {
                                val bArr = ByteArray(dataInputStream.available())
                                dataInputStream.read(bArr)
                                bundle.putByteArray(QRFragment.ARG_MESSAGE, bArr)

                                Log.d(TAG, "calling " + MessageFragment::class.java.simpleName)
                                navController.navigate(R.id.action_QRFragment_to_MessageFragment, bundle)
                            }
                            else -> throw IllegalArgumentException(
                                "No processor defined for application ID " + Integer.toHexString(rsaAesEnvelope.applicationId)
                            )
                        }
                    }
                    MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED,
                    MessageComposer.APPLICATION_TOTP_URI_DEPRECATED -> {
                        bundle.putInt(QRFragment.ARG_APPLICATION_ID, rsaAesEnvelope.applicationId)
                        Log.d(TAG, "calling " + MessageFragment::class.java.simpleName)
                        navController.navigate(R.id.action_QRFragment_to_MessageFragment, bundle)
                    }
                    else -> throw IllegalArgumentException(
                        "No processor defined for application ID " + Integer.toHexString(rsaAesEnvelope.applicationId)
                    )
                }

                if (optOriginalMessage == null) return

                val optDrawableId = MessageComposer.getDrawableIdForApplicationId(
                    bundle.getInt(QRFragment.ARG_APPLICATION_ID)
                )

                if (optDrawableId.isEmpty) return

                QrRecentManager.setRecent(
                    fragment.preferences,
                    optOriginalMessage,
                    optDrawableId.get()
                )
            }
        }
    }
}
