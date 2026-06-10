package com.onemoresecret

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricPrompt

import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.OneTimePassword
import java.io.ByteArrayInputStream
import java.util.Arrays
import java.util.NoSuchElementException
import javax.crypto.Cipher

import android.content.Context
import android.content.SharedPreferences
import androidx.navigation.NavController
import java.util.concurrent.atomic.AtomicBoolean
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

interface QrMessageHandlerCallbacks {
    val context: Context
    val activity: MainActivity
    val navController: NavController
    val preferences: SharedPreferences
    val messageReceived: AtomicBoolean
    var nextPinRequestTimestamp: Long
    fun runPinProtected(onSuccess: Runnable, onCancel: Runnable?, evaluateNextPinRequestTimestamp: Boolean)
}

class QrMessageHandler(
    private val callbacks: QrMessageHandlerCallbacks,
    private val cryptographyManager: CryptographyManager
) {
    companion object {
        private val TAG = QrMessageHandler::class.java.simpleName
    }

    fun onMessage(message: String, callSetRecent: Boolean, popBackStack: Boolean = false) {
        if (callbacks.messageReceived.get()) return
        callbacks.messageReceived.set(true)

        val bArr = MessageComposer.decode(message)
        if (bArr == null) {
            Toast.makeText(
                callbacks.context,
                callbacks.context.getString(R.string.could_not_decode),
                Toast.LENGTH_LONG
            ).show()
            callbacks.messageReceived.set(false)
            return
        }

        try {
            ByteArrayInputStream(bArr).use { bais ->
                OmsDataInputStream(bais).use { dataInputStream ->
                    val applicationId = dataInputStream.readUnsignedShort()
                    val bundle = Bundle().apply {
                        putByteArray(QRScreen.ARG_MESSAGE, bArr)
                        putInt(QRScreen.ARG_APPLICATION_ID, applicationId)
                    }


                    if (OneTimePassword(message).valid) {
                        Log.d(TAG, "calling TotpImportScreen")
                        callbacks.navController.currentBackStackEntry?.savedStateHandle?.let { it.set(QRScreen.ARG_MESSAGE, bundle.getByteArray(QRScreen.ARG_MESSAGE)); it.set(QRScreen.ARG_APPLICATION_ID, bundle.getInt(QRScreen.ARG_APPLICATION_ID)) }; callbacks.navController.navigate(com.onemoresecret.navigation.TotpImportRoute) { if (popBackStack) popUpTo<com.onemoresecret.navigation.QrRoute> { inclusive = true } }
                    } else {
                        Log.d(TAG, String.format("Application-ID: %d", applicationId))

                        var closeSocketWaitingForReply = true

                        when (applicationId) {
                            MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER -> {
                                Log.d(TAG, "calling KeyImportScreen")
                                callbacks.navController.currentBackStackEntry?.savedStateHandle?.let { it.set(QRScreen.ARG_MESSAGE, bundle.getByteArray(QRScreen.ARG_MESSAGE)); it.set(QRScreen.ARG_APPLICATION_ID, bundle.getInt(QRScreen.ARG_APPLICATION_ID)) }
                                callbacks.navController.navigate(
                                    com.onemoresecret.navigation.KeyImportRoute
                                ) { if (popBackStack) popUpTo<com.onemoresecret.navigation.QrRoute> { inclusive = true } }
                            }
                            MessageComposer.APPLICATION_KEY_REQUEST,
                            MessageComposer.APPLICATION_KEY_REQUEST_PAIRING,
                            MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST -> {
                                closeSocketWaitingForReply = false
                                callbacks.runPinProtected(
                                    onSuccess = {
                                        Log.d(TAG, "calling " + "MessageScreen")
                                        callbacks.navController.currentBackStackEntry?.savedStateHandle?.let { it.set(QRScreen.ARG_MESSAGE, bundle.getByteArray(QRScreen.ARG_MESSAGE)); it.set(QRScreen.ARG_APPLICATION_ID, bundle.getInt(QRScreen.ARG_APPLICATION_ID)) }
                                        callbacks.navController.navigate(
                                            com.onemoresecret.navigation.MessageRoute()
                                        ) { if (popBackStack) popUpTo<com.onemoresecret.navigation.QrRoute> { inclusive = true } }
                                    },
                                    onCancel = {
                                        callbacks.activity.sendReplyViaSocket(ByteArray(0), true)
                                        callbacks.messageReceived.set(false)
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

                                callbacks.runPinProtected(
                                    onSuccess = {
                                        showBiometricPromptForDecryption(
                                            rsaAesEnvelope,
                                            getAuthenticationCallback(
                                                rsaAesEnvelope,
                                                cipherText,
                                                if (callSetRecent) message else null,
                                                popBackStack
                                            )
                                        )
                                    },
                                    onCancel = {
                                        callbacks.activity.sendReplyViaSocket(ByteArray(0), true)
                                        callbacks.messageReceived.set(false)
                                    },
                                    evaluateNextPinRequestTimestamp = true
                                )
                            }
                            else -> Log.e(TAG, "No processor defined for application ID " + Integer.toHexString(applicationId))
                        }

                        if (closeSocketWaitingForReply) {
                            callbacks.activity.sendReplyViaSocket(ByteArray(0), true)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Util.printStackTrace(ex)
            callbacks.messageReceived.set(false)
            callbacks.activity.sendReplyViaSocket(ByteArray(0), true)
        }
    }

    private fun showBiometricPromptForDecryption(
        rsaAesEnvelope: MessageComposer.RsaAesEnvelope,
        authenticationCallback: BiometricPrompt.AuthenticationCallback
    ) {
        try {
            val keyStoreEntry = cryptographyManager.getByFingerprint(rsaAesEnvelope.fingerprint, callbacks.preferences)
                ?: throw NoSuchElementException(
                    String.format(
                        callbacks.context.getString(R.string.no_key_found),
                        Util.byteArrayToHex(rsaAesEnvelope.fingerprint)
                    )
                )

            val biometricPrompt = BiometricPrompt(callbacks.activity, authenticationCallback)

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(callbacks.context.getString(R.string.prompt_info_title))
                .setSubtitle(
                    String.format(
                        callbacks.context.getString(R.string.prompt_info_subtitle),
                        keyStoreEntry.alias
                    )
                )
                .setDescription(callbacks.context.getString(R.string.prompt_info_description))
                .setNegativeButtonText(callbacks.context.getString(android.R.string.cancel))
                .setConfirmationRequired(false)
                .build()

            val cipher = cryptographyManager.getInitializedMasterRsaCipher(Cipher.DECRYPT_MODE)

            callbacks.context.mainExecutor.execute {
                biometricPrompt.authenticate(
                    promptInfo,
                    BiometricPrompt.CryptoObject(cipher)
                )
            }
        } catch (ex: Exception) {
            callbacks.messageReceived.set(false)
            Util.printStackTrace(ex)
            callbacks.context.mainExecutor.execute {
                Toast.makeText(
                    callbacks.context,
                    ex.message ?: ex.javaClass.name,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getAuthenticationCallback(
        rsaAesEnvelope: MessageComposer.RsaAesEnvelope,
        cipherText: ByteArray,
        optOriginalMessage: String?,
        popBackStack: Boolean
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                callbacks.messageReceived.set(false)
                val activity = callbacks.activity
                activity.lifecycleScope.launch(Dispatchers.IO) { activity.sendReplyViaSocket(ByteArray(0), true) }
                callbacks.nextPinRequestTimestamp = 0
                activity.lifecycleScope.launch(Dispatchers.IO) { OmsFileProvider.purgeTmp(callbacks.context) }
                Log.d(TAG, String.format("Authentication failed: %s (%s)", errString, errorCode))
                callbacks.context.mainExecutor.execute {
                    Toast.makeText(callbacks.context, "$errString ($errorCode)", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val masterRsaCipher = result.cryptoObject?.cipher!!
                val keyStoreEntry = cryptographyManager.getByFingerprint(rsaAesEnvelope.fingerprint, callbacks.preferences)!!

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

                    afterDecrypt(rsaAesEnvelope, payload, optOriginalMessage, popBackStack)
                } catch (ex: Exception) {
                    Util.printStackTrace(ex)
                    callbacks.messageReceived.set(false)
                    Toast.makeText(
                        callbacks.activity,
                        ex.message ?: String.format(
                            callbacks.context.getString(R.string.authentication_failed_s),
                            ex.javaClass.name
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAuthenticationFailed() {
                callbacks.messageReceived.set(false)
                callbacks.nextPinRequestTimestamp = 0
                Log.d(TAG, "User biometrics rejected")
                callbacks.context.mainExecutor.execute {
                    Toast.makeText(
                        callbacks.context,
                        callbacks.context.getString(R.string.auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun afterDecrypt(
        rsaAesEnvelope: MessageComposer.RsaAesEnvelope,
        payload: ByteArray,
        optOriginalMessage: String?,
        popBackStack: Boolean
    ) {
        ByteArrayInputStream(payload).use { bais ->
            OmsDataInputStream(bais).use { dataInputStream ->
                val bundle = Bundle().apply {
                    putByteArray(QRScreen.ARG_MESSAGE, payload)
                }

                when (rsaAesEnvelope.applicationId) {
                    MessageComposer.APPLICATION_RSA_AES_GENERIC -> {
                        val applicationId = dataInputStream.readUnsignedShort()
                        Log.d(TAG, "payload AI $applicationId")
                        bundle.putInt(QRScreen.ARG_APPLICATION_ID, applicationId)

                        when (applicationId) {
                            MessageComposer.APPLICATION_BITCOIN_ADDRESS,
                            MessageComposer.APPLICATION_ENCRYPTED_MESSAGE,
                            MessageComposer.APPLICATION_ENCRYPTED_OTP,
                            MessageComposer.APPLICATION_TOTP_URI -> {
                                bundle.putByteArray(QRScreen.ARG_MESSAGE, dataInputStream.readByteArray())
                                Log.d(TAG, "calling " + "MessageScreen")
                                callbacks.navController.currentBackStackEntry?.savedStateHandle?.let { it.set(QRScreen.ARG_MESSAGE, bundle.getByteArray(QRScreen.ARG_MESSAGE)); it.set(QRScreen.ARG_APPLICATION_ID, bundle.getInt(QRScreen.ARG_APPLICATION_ID)) }; callbacks.navController.navigate(com.onemoresecret.navigation.MessageRoute()) { if (popBackStack) popUpTo<com.onemoresecret.navigation.QrRoute> { inclusive = true } }
                            }
                            MessageComposer.APPLICATION_WIFI_PAIRING -> {
                                val bArr = ByteArray(dataInputStream.available())
                                dataInputStream.read(bArr)
                                bundle.putByteArray(QRScreen.ARG_MESSAGE, bArr)

                                Log.d(TAG, "calling " + "MessageScreen")
                                callbacks.navController.currentBackStackEntry?.savedStateHandle?.let { it.set(QRScreen.ARG_MESSAGE, bundle.getByteArray(QRScreen.ARG_MESSAGE)); it.set(QRScreen.ARG_APPLICATION_ID, bundle.getInt(QRScreen.ARG_APPLICATION_ID)) }; callbacks.navController.navigate(com.onemoresecret.navigation.MessageRoute()) { if (popBackStack) popUpTo<com.onemoresecret.navigation.QrRoute> { inclusive = true } }
                            }
                            else -> throw IllegalArgumentException(
                                "No processor defined for application ID " + Integer.toHexString(rsaAesEnvelope.applicationId)
                            )
                        }
                    }
                    MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED,
                    MessageComposer.APPLICATION_TOTP_URI_DEPRECATED -> {
                        bundle.putInt(QRScreen.ARG_APPLICATION_ID, rsaAesEnvelope.applicationId)
                        Log.d(TAG, "calling " + "MessageScreen")
                        callbacks.navController.currentBackStackEntry?.savedStateHandle?.let { it.set(QRScreen.ARG_MESSAGE, bundle.getByteArray(QRScreen.ARG_MESSAGE)); it.set(QRScreen.ARG_APPLICATION_ID, bundle.getInt(QRScreen.ARG_APPLICATION_ID)) }; callbacks.navController.navigate(com.onemoresecret.navigation.MessageRoute()) { if (popBackStack) popUpTo<com.onemoresecret.navigation.QrRoute> { inclusive = true } }
                    }
                    else -> throw IllegalArgumentException(
                        "No processor defined for application ID " + Integer.toHexString(rsaAesEnvelope.applicationId)
                    )
                }

                if (optOriginalMessage == null) return

                val applicationId = bundle.getInt(QRScreen.ARG_APPLICATION_ID)
                if (!MessageComposer.isApplicationIdStorableInRecent(applicationId)) return

                QrRecentManager.setRecent(
                    callbacks.preferences,
                    optOriginalMessage,
                    applicationId
                )
            }
        }
    }
}

