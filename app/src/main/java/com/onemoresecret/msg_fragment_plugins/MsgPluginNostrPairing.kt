package com.onemoresecret.msg_fragment_plugins

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.MainActivity
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.R
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.nostr.NostrConnectionManager
import com.onemoresecret.nostr.NostrCrypto
import java.io.ByteArrayInputStream

class MsgPluginNostrPairing(
    activity: FragmentActivity,
    messageData: ByteArray,
    onNavigateBack: () -> Unit
) : MessageFragmentPlugin(activity, onNavigateBack) {

    private var requestId by mutableStateOf("")
    private var onStartClick: (() -> Unit)? by mutableStateOf(null)

    init {
        try {
            OmsDataInputStream(ByteArrayInputStream(messageData)).use { dataInputStream ->
                val topic = dataInputStream.readString()
                val psk = dataInputStream.readByteArray()
                val ttl = dataInputStream.readUnsignedShort()
                val relayCount = dataInputStream.readUnsignedShort()
                val relays = (0 until relayCount).map { dataInputStream.readString() }

                requestId = if (topic.length > 8) topic.take(8) + "..." else topic

                context.mainExecutor.execute {
                    onStartClick = {
                        val privateKey = NostrCrypto.generatePrivateKey()

                        // Save session parameters for durability across suspends
                        NostrConnectionManager.saveSession(context, topic, psk, relays, privateKey)

                        // Instantiate and start connection manager
                        val connectionManager = NostrConnectionManager(
                            context = context,
                            topic = topic,
                            psk = psk,
                            relayUrls = relays,
                            privateKey = privateKey,
                            messageConsumer = { rawPayload ->
                                context.mainExecutor.execute {
                                    val msgStr = MessageComposer.encodeAsOmsText(rawPayload)
                                    val mainActivity = context as MainActivity
                                    val consumer = mainActivity.messageConsumer
                                    if (consumer != null) {
                                        consumer.accept(msgStr)
                                    } else {
                                        Log.e("NostrPairing", "MainActivity messageConsumer is null!")
                                    }
                                }
                            },
                            onStateChanged = { state ->
                                context.mainExecutor.execute {
                                    when (state) {
                                        NostrConnectionManager.State.CONNECTED -> {
                                            Toast.makeText(context, "Nostr Connection Established!", Toast.LENGTH_SHORT).show()
                                        }
                                        NostrConnectionManager.State.DISCONNECTED -> {
                                            // Managed transparently
                                        }
                                        NostrConnectionManager.State.CONNECTING -> {
                                        }
                                    }
                                }
                            }
                        )

                        (context as MainActivity).activeNostrConnection = connectionManager
                        connectionManager.start(isNewPairing = true)

                        // Immediately navigate back to the previous screen
                        onNavigateBack()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NostrPairing", "Error initializing pairing: ${e.message}", e)
            context.mainExecutor.execute {
                Toast.makeText(context, "Failed to parse pairing QR: ${e.message}", Toast.LENGTH_LONG).show()
                onNavigateBack()
            }
        }
    }

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        NostrPairingScreen(
            requestId = requestId,
            onStartClick = onStartClick
        )
    }

    @Composable
    override fun TopBarActions() {
    }

    override fun showBiometricPromptForDecryption() {
    }
}

@Composable
fun NostrPairingScreen(
    requestId: String,
    onStartClick: (() -> Unit)?
) {
    NostrPairing(
        requestId = requestId,
        onConfirm = {
            onStartClick?.invoke()
        }
    )
}

@Composable
fun NostrPairing(
    requestId: String,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "A remote connection is requested via Nostr. Verify the topic prefix matches:",
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = requestId,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onConfirm,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(
                    id = R.string.accept_pairing
                )
            )
        }
    }
}
