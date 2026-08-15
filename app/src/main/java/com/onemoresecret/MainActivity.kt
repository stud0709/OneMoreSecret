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

    val isWiFiCommSet: Boolean
        @Synchronized get() = activeNostrConnection != null

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
        destroyWiFiListener()
    }

    override fun onPause() {
        super.onPause()
        suspendWiFiListener()
    }

    override fun onResume() {
        super.onResume()
        resumeWiFiListener()
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
    fun sendReplyViaSocket(data: ByteArray, closeSocket: Boolean) {
        Log.d(TAG, "sendReplyViaSocket: Delegating to activeMessageSender. Data size: ${data.size}, closeSocket: $closeSocket")
        val sender = activeMessageSender
        if (sender != null) {
            sender.sendReply(data, closeSocket)
        } else {
            Log.e(TAG, "sendReplyViaSocket: activeMessageSender is null! Cannot send reply.")
        }
    }

    @Synchronized
    fun suspendWiFiListener() {
        Log.d(TAG, "suspendWiFiListener: suspending active Nostr connection")
        activeNostrConnection?.suspendConnection()
        activeNostrConnection = null
    }

    @Synchronized
    fun resumeWiFiListener() {
        if (com.onemoresecret.nostr.NostrConnectionManager.hasValidSession(this)) {
            Log.d(TAG, "resumeWiFiListener: Resuming persistent Nostr session")
            val pref = OmsPreferences.get(this)
            val topic = pref.getString("nostr_session_topic", "") ?: return
            val ttl = pref.getInt("nostr_session_ttl", 60)
            val relays = pref.getString("nostr_session_relays", "")?.split(",") ?: return
            val privKeyB64 = pref.getString("nostr_session_privkey", "") ?: return
            val privateKey = android.util.Base64.decode(privKeyB64, android.util.Base64.DEFAULT)
            val expiry = pref.getLong("nostr_session_expiry", 0)

            val remainingTtl = ((expiry - System.currentTimeMillis()) / 1000).toInt()
            if (remainingTtl <= 0) {
                com.onemoresecret.nostr.NostrConnectionManager.clearSession(this)
                return
            }

            val connectionManager = com.onemoresecret.nostr.NostrConnectionManager(
                context = this,
                topic = topic,
                relayUrls = relays,
                ttlSeconds = remainingTtl,
                privateKey = privateKey,
                messageConsumer = { rawPayload ->
                    this.mainExecutor.execute {
                        val msgStr = MessageComposer.encodeAsOmsText(rawPayload)
                        val consumer = messageConsumer
                        if (consumer != null) {
                            consumer.accept(msgStr)
                        } else {
                            Log.e("NostrPairing", "MainActivity messageConsumer is null!")
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
            connectionManager.start()
        }
    }

    @Synchronized
    fun destroyWiFiListener() {
        Log.d(TAG, "destroyWiFiListener: closing active Nostr connection permanently")
        activeNostrConnection?.disconnect()
        activeNostrConnection = null
        com.onemoresecret.nostr.NostrConnectionManager.clearSession(this)
    }

    var messageConsumer: Consumer<String>? = null

    fun startWiFiListener(messageConsumer: Consumer<String>, onSuccess: Runnable) {
        this.messageConsumer = messageConsumer
        onSuccess.run()
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}
