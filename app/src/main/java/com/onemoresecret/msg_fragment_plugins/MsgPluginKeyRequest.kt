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
import androidx.compose.runtime.rememberCoroutineScope
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

    init {
        ByteArrayInputStream(messageData).use { bais ->
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
            }
        }
    }

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        HiddenTextScreen(text = if (hiddenState) context.getString(R.string.hidden_text) else messageText)
    }

    private val outputViewModel = com.onemoresecret.composable.OutputViewModel(preferences)

    @Composable
    override fun TopBarActions() {
        if (applicationId == MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST && outputMessage.isNotEmpty()) {
            androidx.compose.material3.IconButton(onClick = {
                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clipData = android.content.ClipData.newPlainText("oneMoreSecret", outputMessage)
                val persistableBundle = android.os.PersistableBundle().apply {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
                clipData.description.extras = persistableBundle
                clipboardManager.setPrimaryClip(clipData)
                Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
            }) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ContentCopy,
                    contentDescription = "Copy"
                )
            }
        }
    }

    @Composable
    override fun OutputView() {
        if (applicationId == MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST) {
            Oms4WebUnlockScreen(message = outputMessage)
        } else if (applicationId == MessageComposer.APPLICATION_KEY_REQUEST_PAIRING) {
            KeyRequestPairingScreen(replyState = replyState, onNavigateBack = onNavigateBack)
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

        try {
            val aesKeyMaterial = userRsaCipher.cipher.doFinal(encryptedAesKey)
            userRsaCipher.wipe.invoke()

            when (applicationId) {
                MessageComposer.APPLICATION_KEY_REQUEST,
                MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST -> {
                    val rsaEncryptedAesKey = RSAUtil.process(
                        Cipher.ENCRYPT_MODE, rsaPublicKeyMaterial!!, rsaTransformationResponse!!, aesKeyMaterial
                    )
                    Arrays.fill(aesKeyMaterial, 0.toByte())

                    ByteArrayOutputStream().use { baos ->
                        OmsDataOutputStream(baos).use { dataOutputStream ->
                            dataOutputStream.writeUnsignedShort(MessageComposer.APPLICATION_KEY_RESPONSE)
                            dataOutputStream.writeByteArray(rsaEncryptedAesKey)
                            val base64Message = Base64.getEncoder().encodeToString(baos.toByteArray())

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
                            Arrays.fill(aesKeyMaterial, 0.toByte())
                            replyState = baos.toByteArray()
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
        }
    }
}



@Composable
fun KeyRequestPairingScreen(
    replyState: ByteArray?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    KeyRequestPairing(
        reply = replyState,
        onSendKeyClicked = {
            var ctx = context
            while (ctx is android.content.ContextWrapper && ctx !is MainActivity) {
                ctx = ctx.baseContext
            }
            val activity = ctx as? MainActivity
            replyState?.let { replyData ->
                Thread {
                    activity?.sendReplyViaSocket(replyData, true)
                }.start()
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
            .fillMaxSize()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter
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
fun Oms4WebUnlockScreen(message: String?) {
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
                context.startActivity(intent)
                (context as? ComponentActivity)?.finish()
            }
        }
    )
}

@Composable
fun Oms4webUnlock(
    message: String?,
    onUnlock: () -> Unit
) {
    ConstraintLayout(
        modifier = Modifier.fillMaxSize()
    ) {
        val btnUnlock = createRef()

        Button(
            onClick = onUnlock,
            enabled = message != null,
            modifier = Modifier.constrainAs(btnUnlock) {
                bottom.linkTo(parent.bottom, margin = 32.dp)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Default.VpnKey,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(id = R.string.unlock))
        }
    }
}