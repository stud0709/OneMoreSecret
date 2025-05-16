package com.onemoresecret.compose

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.onemoresecret.BuildConfig
import com.onemoresecret.MainActivity
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OmsFileProvider
import com.onemoresecret.R
import com.onemoresecret.Util
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.getFileInfo
import com.onemoresecret.crypto.AESUtil.process
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.MessageComposer.Companion.decode
import com.onemoresecret.crypto.MessageComposer.Companion.getDrawableIdForApplicationId
import com.onemoresecret.crypto.MessageComposer.Companion.readRsaAesEnvelope
import com.onemoresecret.crypto.MessageComposer.RsaAesEnvelope
import com.onemoresecret.crypto.OneTimePassword
import com.onemoresecret.qr.MessageParser
import com.onemoresecret.qr.QRCodeAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.util.BitSet
import java.util.Objects
import java.util.Optional
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import java.util.stream.IntStream
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class QrViewModel(application: Application, val resourceProvider: ResourceProvider) :
    AndroidViewModel(application) {
    @Serializable
    data class RecentEntry(
        val message: String,
        val drawableId: Int,
        val ttl: Long
    )

    val recentEntries = mutableStateListOf<RecentEntry>()

    //PIN entry dialog
    data class PinEntryParams(val onSuccess: () -> Unit, val onCancel: (() -> Unit)?)

    internal val pinEntryParams = mutableStateOf<PinEntryParams?>(null)
    internal val remainingCodes = mutableStateOf("")
    internal val snackbarMessage = mutableStateOf<String?>(null)
    internal val navigateTo = mutableStateOf<String?>(null)
    internal val wiFiPairing = mutableStateOf(false) //TODO

    private var lastReceivedChunks: BitSet? = null
    private val messageReceived = AtomicBoolean(false)

    internal data class BioPromptParams(val callback: BiometricPrompt.AuthenticationCallback, val promptInfo: PromptInfo, val cipher: Cipher)
    internal val bioPromptParams = mutableStateOf<BioPromptParams?>(null)

    private val preferences: SharedPreferences = application.applicationContext.getSharedPreferences(
        MainActivity.SPARED_PREF_NAME,
        Context.MODE_PRIVATE
    )

    val clipboardManager =
        application.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private var nextPinRequestTimestamp: Long = 0

    private val isZxingEnabled: Boolean
        get() = BuildConfig.FLAVOR == Util.FLAVOR_FOSS /*in the FOSS version, ZXing is the only QR engine */
                || preferences!!.getBoolean(PROP_USE_ZXING, false)

    //TODO: WiFi Pairing
    //TODO: Context Menu

    private val parser = object : MessageParser() {
        override fun onMessage(message: String) {
            this@QrViewModel.onMessage(message, true)
        }

        override fun onChunkReceived(
            receivedChunks: BitSet,
            cntReceived: Int,
            totalChunks: Int
        ) {
            if (messageReceived.get()) return
            if (receivedChunks == lastReceivedChunks) return
            lastReceivedChunks = receivedChunks

            remainingCodes.value = IntStream.range(0, totalChunks)
                .filter { i: Int -> !receivedChunks[i] }
                .mapToObj { i: Int -> (i + 1).toString() }.collect(Collectors.joining(", "))
        }
    }

    private fun runPinProtected(
        onSuccess: () -> Unit,
        onCancel: (() -> Unit)?,
        evaluateNextPinRequestTimestamp: Boolean
    ) {
        if (preferences.getBoolean(PinSetupViewModel.PROP_PIN_ENABLED, false) &&
            (System.currentTimeMillis() > nextPinRequestTimestamp || !evaluateNextPinRequestTimestamp)
        ) {
            pinEntryParams.value = PinEntryParams(
                {
                    if (evaluateNextPinRequestTimestamp) {
                        //calculate next pin request time
                        val intervalMs = TimeUnit.MINUTES.toMillis(
                            preferences.getLong(PinSetupViewModel.PROP_REQUEST_INTERVAL_MINUTES, 0)
                        )
                        nextPinRequestTimestamp =
                            if (intervalMs == 0L) Long.MAX_VALUE else System.currentTimeMillis() + intervalMs
                    }
                    onSuccess()
                }, onCancel
            )
        } else {
            viewModelScope.launch(Dispatchers.Main) {
                onSuccess()
            }
        }
    }

    fun loadRecentEntries() {
        var tmpRecentEntries: List<RecentEntry> =
            Json.decodeFromString(preferences.getString(PROP_RECENT_ENTRIES, "[]")!!)
        tmpRecentEntries =
            tmpRecentEntries.filter { it.ttl > System.currentTimeMillis() } //remove expired

        val maxSize = preferences.getInt(PROP_RECENT_SIZE, DEF_RECENT_SIZE)

        recentEntries.clear()
        recentEntries.addAll(tmpRecentEntries.take(maxSize))
    }

    /**
     * Try to process inbound message
     *
     * @param message       Message in OMS format
     * @param callSetRecent add this message to recent messages
     * @see MessageComposer
     */
    internal fun onMessage(message: String, callSetRecent: Boolean) {
        if (messageReceived.get()) return
        messageReceived.set(true)

        val bArr = decode(message)
        if (bArr == null) {
            snackbarMessage.value = resourceProvider.getString(R.string.could_not_decode)
            messageReceived.set(false)
            return
        }

        try {
            ByteArrayInputStream(bArr).use { bais ->
                OmsDataInputStream(bais).use { dataInputStream ->
                    val applicationId = dataInputStream.readUnsignedShort()
                    val bundle = Bundle()
                    bundle.putByteArray(ARG_MESSAGE, bArr)
                    bundle.putInt(ARG_APPLICATION_ID, applicationId)

                    //other supported formats?
                    if (OneTimePassword(message).isValid) {
                        //time based OTP
                        navigateTo.value = "TotpImport"
                        Log.d(TAG, "calling ${navigateTo.value}")
                    } else {
                        Log.d(
                            TAG,
                            "Application-ID: " + Integer.toHexString(applicationId)
                        )

                        var closeSocketWaitingForReply =
                            true //socket not used for reply, close immediately

                        when (applicationId) {
                            MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER -> {
                                //key import is not PIN protected
                                navigateTo.value = "KeyImport"
                                Log.d(TAG, "calling ${navigateTo.value}")
                            }

                            MessageComposer.APPLICATION_KEY_REQUEST, MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                                closeSocketWaitingForReply = false

                                runPinProtected(
                                    {
                                        navigateTo.value = "Message"
                                        Log.d(TAG, "calling ${navigateTo.value}")
                                    }, {
                                        //close socket if WiFiPairing active
                                        (requireActivity() as MainActivity).sendReplyViaSocket(
                                            byteArrayOf(),
                                            true
                                        )
                                        //enable message processing again
                                        messageReceived.set(false)
                                    },
                                    true
                                )
                            }

                            MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED,
                            MessageComposer.APPLICATION_TOTP_URI_DEPRECATED,
                            MessageComposer.APPLICATION_RSA_AES_GENERIC -> {
                                dataInputStream.reset()
                                val rsaAesEnvelope = readRsaAesEnvelope(dataInputStream)
                                //(7) - cipher text
                                val cipherText = dataInputStream.readByteArray()
                                runPinProtected(
                                    {
                                        showBiometricPromptForDecryption(
                                            rsaAesEnvelope.fingerprint,
                                            rsaAesEnvelope.rsaTransormation,
                                            getAuthenticationCallback(
                                                rsaAesEnvelope,
                                                cipherText,
                                                if (callSetRecent) Optional.of(
                                                    message
                                                ) else Optional.empty()
                                            )
                                        )
                                    }, {
                                        //close socket if WiFiPairing active
                                        (requireActivity() as MainActivity).sendReplyViaSocket(
                                            byteArrayOf(),
                                            true
                                        )
                                        //enable message processing again
                                        messageReceived.set(false)
                                    },
                                    true
                                )
                            }

                            else -> Log.e(
                                TAG,
                                "No processor defined for application ID " +
                                        Integer.toHexString(applicationId)
                            )
                        }

                        if (closeSocketWaitingForReply) {
                            //close socket if WiFiPairing active
                            (requireActivity() as MainActivity).sendReplyViaSocket(
                                byteArrayOf(),
                                true
                            )
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            messageReceived.set(false)
            //close socket if WiFiPairing active
            (requireActivity() as MainActivity).sendReplyViaSocket(byteArrayOf(), true)
        }
    }

    private fun onUri(uri: Uri) {
        val bundle = Bundle()
        bundle.putParcelable(ARG_URI, uri)

        val fileInfo = getFileInfo(getApplication<Application>().applicationContext, uri)

        if (fileInfo.filename.endsWith("." + MessageComposer.OMS_FILE_TYPE)) {
            navigateTo.value = "Message"
        } else {
            //pass URI to file encoder
            navigateTo.value = "FileEncryption"
        }

        Log.d(TAG, "calling ${navigateTo.value}")
    }


    /**
     * return composable alias to load
     */
    fun processIntent(intent: Intent, context: Context) {
        try {
            val action = intent.action
            val type = intent.type
            Log.d(TAG, "Intent action: $action, type: $type")

            when (Objects.requireNonNull(intent.action)) {
                Intent.ACTION_VIEW -> {
                    val uri = intent.data
                    if (uri == null) {
                        snackbarMessage.value = context.getString(R.string.malformed_intent)
                    } else {
                        onUri(uri)
                    }
                }

                Intent.ACTION_SEND -> {
                    //a piece of text has been sent to the app using Android "send to" functionality
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)

                    if (text.isNullOrEmpty()) {
                        val uri =
                            intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri?
                        if (uri != null) {
                            Log.d(TAG, "URI: $uri")
                            onUri(uri)
                        }
                    } else {
                        if (decode(text) == null) {
                            //this is not an OMS message, forward it to the text encryption fragment
                            val bundle = Bundle()
                            bundle.putString(ARG_TEXT, text)

                            navigateTo.value = "EncryptTextScreen"
                        } else {
                            onMessage(text, true)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(
                context,
                Objects.requireNonNullElse(ex.message, ex.javaClass.name),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun getAuthenticationCallback(
        rsaAesEnvelope: RsaAesEnvelope,
        cipherText: ByteArray,
        optOriginalMessage: Optional<String>
    ): BiometricPrompt.AuthenticationCallback {
        return object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                messageReceived.set(false)
                val activity = requireActivity() as MainActivity
                Thread { activity.sendReplyViaSocket(byteArrayOf(), true) }.start()
                nextPinRequestTimestamp = 0
                val context = getApplication<Application>().applicationContext
                Thread { OmsFileProvider.purgeTmp(context) }.start()
                Log.d(
                    TAG,
                    String.format("Authentication failed: %s (%s)", errString, errorCode)
                )
                snackbarMessage.value = "$errString ($errorCode)"
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val cipher = result.cryptoObject!!.cipher

                try {
                    checkNotNull(cipher)
                    val aesSecretKeyData = cipher.doFinal(rsaAesEnvelope.encryptedAesSecretKey)
                    val aesSecretKey = SecretKeySpec(aesSecretKeyData, "AES")

                    val payload = process(
                        Cipher.DECRYPT_MODE, cipherText,
                        aesSecretKey,
                        IvParameterSpec(rsaAesEnvelope.iv),
                        rsaAesEnvelope.aesTransformation
                    )

                    //payload starts with its own application identifier.
                    afterDecrypt(rsaAesEnvelope, payload, optOriginalMessage)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    messageReceived.set(false)
                    snackbarMessage.value = Objects.requireNonNullElse(
                        ex.message, String.format(
                            resourceProvider.getString(R.string.authentication_failed_s),
                            ex.javaClass.name
                        )
                    )
                }
            }

            override fun onAuthenticationFailed() {
                messageReceived.set(false)
                nextPinRequestTimestamp = 0
                Log.d(
                    TAG,
                    "User biometrics rejected"
                )
                snackbarMessage.value = resourceProvider.getString(R.string.auth_failed)
            }
        }
    }

    fun showBiometricPromptForDecryption(
        fingerprint: ByteArray,
        rsaTransformation: String,
        authenticationCallback: BiometricPrompt.AuthenticationCallback
    ) {
        val cryptographyManager = CryptographyManager()
        val aliases: List<String>
        try {
            aliases = cryptographyManager.getByFingerprint(fingerprint)

            require(aliases.isEmpty()) {
                String.format(
                    resourceProvider.getString(
                        R.string.no_key_found
                    ), byteArrayToHex(fingerprint)
                )
            }

            check(aliases.size <= 1) { resourceProvider.getString(R.string.multiple_keys_found) }

            val alias = aliases[0]

            val promptInfo = PromptInfo.Builder()
                .setTitle(resourceProvider.getString(R.string.prompt_info_title))
                .setSubtitle(
                    String.format(
                        resourceProvider.getString(R.string.prompt_info_subtitle),
                        alias
                    )
                )
                .setDescription(resourceProvider.getString(R.string.prompt_info_description))
                .setNegativeButtonText(resourceProvider.getString(android.R.string.cancel))
                .setConfirmationRequired(false)
                .build()

            val cipher = CryptographyManager().getInitializedCipherForDecryption(
                alias, rsaTransformation
            )

            bioPromptParams.value = BioPromptParams(authenticationCallback, promptInfo, cipher)
        } catch (ex: Exception) {
            messageReceived.set(false)
            ex.printStackTrace()
            snackbarMessage.value = Objects.requireNonNullElse(
                ex.message,
                ex.javaClass.name
            )
        }
    }

    private fun afterDecrypt(
        rsaAesEnvelope: RsaAesEnvelope,
        payload: ByteArray,
        optOriginalMessage: Optional<String>
    ) {
        ByteArrayInputStream(payload).use { bais ->
            OmsDataInputStream(bais).use { dataInputStream ->
                val bundle = Bundle()
                bundle.putByteArray(ARG_MESSAGE, payload)

                when (rsaAesEnvelope.applicationId) {
                    MessageComposer.APPLICATION_RSA_AES_GENERIC -> {
                        //(1) - application identifier Payload
                        val applicationId = dataInputStream.readUnsignedShort()

                        Log.d(TAG, "payload AI $applicationId")

                        bundle.putInt(ARG_APPLICATION_ID, applicationId)

                        when (applicationId) {
                            MessageComposer.APPLICATION_BITCOIN_ADDRESS, MessageComposer.APPLICATION_ENCRYPTED_MESSAGE, MessageComposer.APPLICATION_TOTP_URI -> {
                                //(2) message
                                bundle.putByteArray(ARG_MESSAGE, dataInputStream.readByteArray())
                                navigateTo.value = "Message"
                                Log.d(TAG, "calling ${navigateTo.value}")
                            }

                            MessageComposer.APPLICATION_WIFI_PAIRING -> {
                                //(2)...(n) structured data to be read from the remaining bytes
                                val bArr = ByteArray(dataInputStream.available())
                                dataInputStream.read(bArr)
                                bundle.putByteArray(ARG_MESSAGE, bArr)

                                navigateTo.value = "Message"
                                Log.d(TAG, "calling ${navigateTo.value}")
                            }

                            else -> throw IllegalArgumentException(
                                "No processor defined for application ID " +
                                        Integer.toHexString(rsaAesEnvelope.applicationId)
                            )
                        }
                    }

                    MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED, MessageComposer.APPLICATION_TOTP_URI_DEPRECATED -> {
                        //support for legacy formats
                        bundle.putInt(ARG_APPLICATION_ID, rsaAesEnvelope.applicationId)
                        navigateTo.value = "Message"
                        Log.d(TAG, "calling ${navigateTo.value}")
                    }

                    else -> throw IllegalArgumentException(
                        "No processor defined for application ID " +
                                Integer.toHexString(rsaAesEnvelope.applicationId)
                    )
                }

                if (!optOriginalMessage.isPresent) return  //do not update history


                val optDrawableId = getDrawableIdForApplicationId(bundle.getInt(ARG_APPLICATION_ID))

                if (!optDrawableId.isPresent) return
                setRecent(optOriginalMessage.get(), optDrawableId.get())
            }
        }
    }

    private fun setRecent(message: String, drawableId: Int) {
        try {
            var tmpRecentEntries: MutableList<RecentEntry> =
                Json.decodeFromString(preferences.getString(PROP_RECENT_ENTRIES, "[]")!!)

            if (tmpRecentEntries.isNotEmpty() &&
                tmpRecentEntries[0].message == message
            ) return  //do not store duplicates in history


            val recentSize = preferences.getInt(
                PROP_RECENT_SIZE,
                DEF_RECENT_SIZE
            )

            //add latest recent values
            val newEntry = RecentEntry(
                message,
                drawableId,
                System.currentTimeMillis() + RECENT_TTL
            )

            tmpRecentEntries.add(0, newEntry)

            //crop to maximal size
            while (tmpRecentEntries.size > recentSize) {
                tmpRecentEntries.removeAt(tmpRecentEntries.size - 1)
            }

            preferences.edit() {
                putString(
                    PROP_RECENT_ENTRIES,
                    Json.encodeToString(tmpRecentEntries)
                )
            }

            loadRecentEntries()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    internal fun startCamera(
        context: Context,
        viewModel: QrViewModel,
        cameraPreview: PreviewView,
        lifecycleOwner: LifecycleOwner
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview =
                    Preview.Builder().build()
                preview.surfaceProvider = cameraPreview.surfaceProvider

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()

                val imageAnalysis =
                    ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                imageAnalysis.setAnalyzer(
                    context.mainExecutor,
                    object : QRCodeAnalyzer(viewModel.isZxingEnabled) {
                        override fun onQRCodeFound(barcodeValue: String?) {
                            try {
                                barcodeValue?.let {
                                    viewModel.parser.consume(it)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    })

                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    context,
                    String.format(context.getString(R.string.error_starting_camera), e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, context.mainExecutor)
    }

    companion object {
        val TAG: String = QrViewModel::class.simpleName.toString()
        private const val PROP_USE_ZXING = "use_zxing"
        private const val PROP_RECENT_SIZE = "recent_size"
        const val PROP_RECENT_ENTRIES: String = "recent_entries"
        private const val DEF_RECENT_SIZE = 3
        private val RECENT_TTL = TimeUnit.HOURS.toMillis(12)
        const val ARG_URI: String = "URI"
        const val ARG_MESSAGE: String = "MESSAGE"
        const val ARG_TEXT: String = "TEXT"
        const val ARG_APPLICATION_ID: String = "AI"
    }
}