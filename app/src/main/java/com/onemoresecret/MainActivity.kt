package com.onemoresecret

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.onemoresecret.crypto.MessageComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.function.Consumer

class MainActivity : FragmentActivity() {

    private lateinit var preferences: SharedPreferences

    var activeMessageSender: OmsMessageSender? = null
    var activeNostrConnection: com.onemoresecret.nostr.NostrConnectionManager? = null

    val isNostrCommSet: Boolean
        @Synchronized get() = activeNostrConnection != null || com.onemoresecret.nostr.NostrConnectionManager.hasValidSession(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            intent = null
        }
        Thread.setDefaultUncaughtExceptionHandler(OmsUncaughtExceptionHandler(this))
        preferences = OmsPreferences.get(this)

        //prohibit screenshots
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch(Dispatchers.IO) { OmsFileProvider.purgeTmp(this@MainActivity) }

        setContent {
            com.onemoresecret.composable.OneMoreSecretTheme {
                OmsApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        OmsFileProvider.purgeTmp(this)
        destroyNostrListener()
    }

    override fun onPause() {
        super.onPause()
        suspendNostrListener()
    }

    override fun onResume() {
        super.onResume()
        resumeNostrListener()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        
        // Construct a safe, explicit intent to prevent Intent Redirection vulnerabilities
        val safeIntent = Intent(this, MainActivity::class.java).apply {
            action = intent.action
            data = intent.data
            intent.extras?.let { putExtras(it) }
        }
        startActivity(safeIntent)
    }

    @Synchronized
    fun sendReply(data: ByteArray, shouldClose: Boolean) {
        Log.d(TAG, "sendReply: Delegating to activeMessageSender. Data size: ${data.size}, shouldClose: $shouldClose")
        val sender = activeMessageSender
        if (sender != null) {
            sender.sendReply(data, shouldClose)
        } else {
            Log.e(TAG, "sendReply: activeMessageSender is null! Cannot send reply.")
        }
    }

    @Synchronized
    fun suspendNostrListener() {
        Log.d(TAG, "suspendNostrListener: suspending active Nostr connection")
        activeNostrConnection?.suspendConnection()
        activeNostrConnection = null
    }

    @Synchronized
    fun resumeNostrListener() {
        if (activeNostrConnection != null) {
            Log.d(TAG, "resumeNostrListener: Nostr connection is already active")
            return
        }
        if (com.onemoresecret.nostr.NostrConnectionManager.hasValidSession(this)) {
            Log.d(TAG, "resumeNostrListener: Resuming persistent Nostr session")
            val pref = OmsPreferences.get(this)
            val topic = pref.getString("nostr_session_topic", "") ?: return
            val pskB64 = pref.getString("nostr_session_psk", "") ?: return
            val psk = android.util.Base64.decode(pskB64, android.util.Base64.DEFAULT)
            val relays = pref.getString("nostr_session_relays", "")?.split(",") ?: return
            val privKeyB64 = pref.getString("nostr_session_privkey", "") ?: return
            val privateKey = android.util.Base64.decode(privKeyB64, android.util.Base64.DEFAULT)

            val connectionManager = com.onemoresecret.nostr.NostrConnectionManager(
                context = this,
                topic = topic,
                psk = psk,
                relayUrls = relays,
                privateKey = privateKey,
                messageConsumer = { rawPayload ->
                    this.mainExecutor.execute {
                        val msgStr = MessageComposer.encodeAsOmsText(rawPayload)
                        if (isQrScreenVisible) {
                            val consumer = messageConsumer
                            if (consumer != null) {
                                consumer.accept(msgStr)
                            } else {
                                Log.e(TAG, "MainActivity messageConsumer is null!")
                            }
                        } else {
                            Log.d(TAG, "QRScreen is not visible. Queuing latest Nostr message with 60s TTL.")
                            pendingNostrMessage = QueuedNostrMessage(msgStr, System.currentTimeMillis())
                        }
                    }
                },
                onStateChanged = { state ->
                    this.mainExecutor.execute {
                        when (state) {
                            com.onemoresecret.nostr.NostrConnectionManager.State.CONNECTED -> {
                                Toast.makeText(this, "Nostr Connection Re-established!", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                // Transparently handled
                            }
                        }
                    }
                }
            )

            activeNostrConnection = connectionManager
            connectionManager.start(isNewPairing = false)
        }
    }

    @Synchronized
    fun destroyNostrListener() {
        Log.d(TAG, "destroyNostrListener: closing active Nostr connection permanently")
        pendingNostrMessage = null
        activeNostrConnection?.disconnect()
        activeNostrConnection = null
        com.onemoresecret.nostr.NostrConnectionManager.clearSession(this)
    }

    data class QueuedNostrMessage(
        val text: String,
        val timestampMs: Long = System.currentTimeMillis()
    )

    var isQrScreenVisible: Boolean = false
    var pendingNostrMessage: QueuedNostrMessage? = null

    fun consumePendingNostrMessage(maxAgeMs: Long = 60_000L): String? {
        val queued = pendingNostrMessage
        pendingNostrMessage = null
        if (queued == null) return null
        val ageMs = System.currentTimeMillis() - queued.timestampMs
        if (ageMs > maxAgeMs) {
            Log.d(TAG, "Queued Nostr message expired (age: ${ageMs / 1000}s > ${maxAgeMs / 1000}s). Discarding.")
            return null
        }
        return queued.text
    }

    var messageConsumer: Consumer<String>? = null

    fun startNostrListener(messageConsumer: Consumer<String>, onSuccess: Runnable) {
        this.messageConsumer = messageConsumer
        if (activeNostrConnection == null && com.onemoresecret.nostr.NostrConnectionManager.hasValidSession(this)) {
            resumeNostrListener()
        }
        onSuccess.run()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
