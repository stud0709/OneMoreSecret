package com.onemoresecret.msg_fragment_plugins

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.MainActivity
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OmsDataOutputStream
import com.onemoresecret.R
import com.onemoresecret.Util
import com.onemoresecret.composable.HiddenTextScreen
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
    activity: FragmentActivity,
    messageData: ByteArray,
    onNavigateBack: () -> Unit
) : MessageFragmentPlugin(activity, onNavigateBack) {

    protected var msgReference: String? = null
    protected var rsaPublicKeyMaterial: ByteArray? = null
    protected var encryptedAesKey: ByteArray? = null
    protected var applicationId: Int = 0
    protected var rsaTransformationResponse: RsaTransformation? = null

    private var messageText by mutableStateOf("")
    private var outputMessage by mutableStateOf("")
    private var replyState by mutableStateOf<ByteArray?>(null)
    private val v1Request = if (org.onemoresecret.protocol.OmsProtocol.hasMagic(messageData)) org.onemoresecret.protocol.OmsProtocol.readRequest(messageData) else null
    private val responseSender = (activity as? MainActivity)?.activeMessageSender

    init {
        ByteArrayInputStream(v1Request?.legacy ?: messageData).use { bais ->
            OmsDataInputStream(bais).use { dataInputStream ->
                applicationId = dataInputStream.readUnsignedShort()
                when (applicationId) {
                    MessageComposer.APPLICATION_KEY_REQUEST,
                    MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST -> {
                        msgReference = dataInputStream.readString()
                        rsaPublicKeyMaterial = dataInputStream.readByteArray()
                        fingerprint = dataInputStream.readByteArray()
                        rsaTransformation = RsaTransformation.entries[dataInputStream.readUnsignedShort()]
                        rsaTransformationResponse = RsaTransformation.entries[dataInputStream.readUnsignedShort()]
                        encryptedAesKey = dataInputStream.readByteArray()
                    }
                    MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                        msgReference = dataInputStream.readString()
                        fingerprint = dataInputStream.readByteArray()
                        rsaTransformation = RsaTransformation.entries[dataInputStream.readUnsignedShort()]
                        encryptedAesKey = dataInputStream.readByteArray()
                    }
                }
                require(dataInputStream.read() == -1) { "Trailing key request data" }
                if (v1Request != null) {
                    require(v1Request.targetProtocol == 0 || rsaTransformation == RsaTransformation.entries[2]) { "Invalid target RSA suite" }
                    require(applicationId == 11 || rsaTransformationResponse == RsaTransformation.entries[2]) { "Invalid response RSA suite" }
                }
            }
        }
    }

    override val showVisibilityButton: Boolean
        get() = false

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        HiddenTextScreen(text = messageText)
    }

    private val outputViewModel = com.onemoresecret.composable.OutputViewModel(preferences)

    @Composable
    override fun TopBarActions() {
        if (applicationId == MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST && outputMessage.isNotEmpty()) {
            IconButton(onClick = {
                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipData = android.content.ClipData.newPlainText("oneMoreSecret", outputMessage)
                val persistableBundle = android.os.PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
                clipData.description.extras = persistableBundle
                clipboardManager.setPrimaryClip(clipData)
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ContentCopy,
                    contentDescription = "Copy"
                )
            }
        }
    }

    @Composable
    override fun OutputView() {
        if (applicationId == MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST) {
            Oms4WebUnlockScreen(message = outputMessage, onNavigateBack = onNavigateBack)
        } else if (applicationId == MessageComposer.APPLICATION_KEY_REQUEST_PAIRING) {
            KeyRequestPairingScreen(replyState = replyState, onNavigateBack = onNavigateBack, sendReply = { data -> responseSender?.sendReply(data, true) })
        } else {
            com.onemoresecret.composable.OutputScreen(outputViewModel = outputViewModel)
        }
    }

    override fun getReference(): String? {
        return msgReference?.let { String.format(context.getString(R.string.reference), it) }
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        val masterRsaCipher = requireNotNull(result.cryptoObject).cipher!!
        val keyStoreEntry = CryptographyManager().getByFingerprint(fingerprint!!, preferences)!!
        val userRsaCipher = CryptographyManager().getInitializedUserRsaCipher(
            masterRsaCipher, keyStoreEntry, rsaTransformation!!, Cipher.DECRYPT_MODE
        )

        var aesKeyMaterial: ByteArray? = null
        try {
            aesKeyMaterial = userRsaCipher.cipher.doFinal(encryptedAesKey)

            when (applicationId) {
                MessageComposer.APPLICATION_KEY_REQUEST,
                MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST -> {
                    val rsaEncryptedAesKey = if (v1Request != null) org.onemoresecret.protocol.OmsProtocol.rsa(
                        Cipher.ENCRYPT_MODE, RSAUtil.restorePublicKey(rsaPublicKeyMaterial!!), aesKeyMaterial
                    ) else RSAUtil.process(
                        Cipher.ENCRYPT_MODE, rsaPublicKeyMaterial!!, rsaTransformationResponse!!, aesKeyMaterial
                    )
                    Arrays.fill(aesKeyMaterial, 0.toByte())

                    ByteArrayOutputStream().use { baos ->
                        OmsDataOutputStream(baos).use { dataOutputStream ->
                            dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_KEY_RESPONSE)
                            dataOutputStream.writeByteArray(rsaEncryptedAesKey)
                            val bytes = v1Request?.let { org.onemoresecret.protocol.OmsProtocol.reply(it.id, false, rsaEncryptedAesKey) } ?: baos.toByteArray()
                            val base64Message = Base64.getEncoder().encodeToString(bytes)

                            if (applicationId == MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST) {
                                messageText = context.getString(R.string.ready_to_unlock_oms4web)
                                outputMessage = base64Message
                            } else {
                                messageText = String.format(context.getString(R.string.key_response_is_ready), msgReference)
                                outputMessage = base64Message + "\n"
                                outputViewModel.setMessage(outputMessage, context.getString(R.string.key_response))
                            }
                        }
                    }
                }
                MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> {
                    messageText = String.format(context.getString(R.string.key_response_is_ready), msgReference)
                    ByteArrayOutputStream().use { baos ->
                        OmsDataOutputStream(baos).use { dataOutputStream ->
                            dataOutputStream.writeByteArray(aesKeyMaterial)
                            replyState = v1Request?.let { org.onemoresecret.protocol.OmsProtocol.reply(it.id, true, aesKeyMaterial) } ?: baos.toByteArray()
                            Arrays.fill(aesKeyMaterial, 0.toByte())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Util.printStackTrace(e)
            context.mainExecutor.execute {
                Toast.makeText(context, e.message ?: "Auth failed", Toast.LENGTH_SHORT).show()
                onNavigateBack()
            }
        } finally {
            aesKeyMaterial?.fill(0)
            userRsaCipher.wipe.invoke()
        }
    }
}



@Composable
fun KeyRequestPairingScreen(
    replyState: ByteArray?,
    onNavigateBack: () -> Unit,
    sendReply: (ByteArray) -> Unit
) {
    val context = LocalContext.current

    KeyRequestPairing(
        reply = replyState,
        onSendKeyClicked = {
            var ctx = context
            while (ctx is android.content.ContextWrapper && ctx !is MainActivity) {
                ctx = ctx.baseContext
            }
            val activity = ctx as? MainActivity
            replyState?.let { replyData ->
                sendReply(replyData)
            }
            onNavigateBack()
        }
    )
}


@Composable
fun KeyRequestPairing(
    reply: ByteArray?,
    onSendKeyClicked: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onSendKeyClicked,
            // The button is enabled only if the reply data is present
            enabled = reply != null
        ) {
            Text(text = "Send Key")
        }
    }
}

@Composable
fun Oms4WebUnlockScreen(message: String?, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val strOms4webCallbackUrl = androidx.compose.ui.res.stringResource(R.string.oms4web_callback_url)

    Oms4webUnlock(
        message = message,
        onUnlock = {
            if (message != null) {
                val encodedData = Uri.encode(message)
                val url = strOms4webCallbackUrl.format(encodedData)
                val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                onNavigateBack()
                context.startActivity(intent)
            }
        }
    )
}

@Composable
fun Oms4webUnlock(
    message: String?,
    onUnlock: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onUnlock,
            enabled = message != null
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.VpnKey,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(text = androidx.compose.ui.res.stringResource(id = R.string.unlock))
        }
    }
}