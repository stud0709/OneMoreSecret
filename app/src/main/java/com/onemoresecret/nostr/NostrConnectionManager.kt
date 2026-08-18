package com.onemoresecret.nostr

import android.content.Context
import android.util.Base64
import android.util.Log
import com.onemoresecret.MainActivity
import com.onemoresecret.OmsMessageSender
import com.onemoresecret.OmsPreferences
import com.onemoresecret.crypto.MessageComposer
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicBoolean

class NostrConnectionManager(
    private val context: Context,
    private val topic: String,
    private val relayUrls: List<String>,
    private val ttlSeconds: Int,
    val privateKey: ByteArray,
    private val messageConsumer: (ByteArray) -> Unit,
    private val onStateChanged: (State) -> Unit
) {
    enum class State {
        CONNECTING,
        CONNECTED,
        DISCONNECTED
    }

    private var nostrClient: NostrClient? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pingJob: Job? = null
    private var monitorJob: Job? = null
    private var lastPongTime = 0L
    private val isConnected = AtomicBoolean(false)
    private var sessionExpiryTime = 0L
    private val myPubKeyHex = NostrCrypto.bytesToHex(NostrCrypto.getXOnlyPublicKey(privateKey))

    companion object {
        private const val TAG = "NostrConnectionManager"
        private const val PROP_NOSTR_TOPIC = "nostr_session_topic"
        private const val PROP_NOSTR_TTL = "nostr_session_ttl"
        private const val PROP_NOSTR_RELAYS = "nostr_session_relays"
        private const val PROP_NOSTR_PRIVKEY = "nostr_session_privkey"
        private const val PROP_NOSTR_EXPIRY = "nostr_session_expiry"

        fun saveSession(context: Context, topic: String, ttlSeconds: Int, relays: List<String>, privateKey: ByteArray, expiryTime: Long) {
            val preferences = OmsPreferences.get(context)
            preferences.edit()
                .putString(PROP_NOSTR_TOPIC, topic)
                .putInt(PROP_NOSTR_TTL, ttlSeconds)
                .putString(PROP_NOSTR_RELAYS, relays.joinToString(","))
                .putString(PROP_NOSTR_PRIVKEY, Base64.encodeToString(privateKey, Base64.NO_WRAP))
                .putLong(PROP_NOSTR_EXPIRY, expiryTime)
                .apply()
        }

        fun clearSession(context: Context) {
            val preferences = OmsPreferences.get(context)
            preferences.edit()
                .remove(PROP_NOSTR_TOPIC)
                .remove(PROP_NOSTR_TTL)
                .remove(PROP_NOSTR_RELAYS)
                .remove(PROP_NOSTR_PRIVKEY)
                .remove(PROP_NOSTR_EXPIRY)
                .apply()
        }

        fun hasValidSession(context: Context): Boolean {
            val preferences = OmsPreferences.get(context)
            val expiry = preferences.getLong(PROP_NOSTR_EXPIRY, 0)
            return System.currentTimeMillis() < expiry && preferences.contains(PROP_NOSTR_TOPIC)
        }
    }

    fun start() {
        Log.d(TAG, "Starting NostrConnectionManager on topic: $topic")
        sessionExpiryTime = System.currentTimeMillis() + (ttlSeconds * 1000L)
        onStateChanged(State.CONNECTING)

        nostrClient = NostrClient(
            relayUrls = relayUrls,
            topic = topic,
            privateKey = privateKey,
            onRelayConnected = { relayUrl ->
                Log.d(TAG, "Relay connected ($relayUrl) - publishing instant initial ping")
                publishPing()
            },
            onEventReceived = { event ->
                handleNostrEvent(event)
            }
        )
        nostrClient?.connect()

        // Periodically send ping (every 30 seconds)
        pingJob = scope.launch {
            while (isActive) {
                delay(30000) // Every 30 seconds
                if (System.currentTimeMillis() > sessionExpiryTime) {
                    Log.d(TAG, "Session TTL expired")
                    disconnect()
                    break
                }
                publishPing()
            }
        }

        // Connection monitor
        monitorJob = scope.launch {
            lastPongTime = System.currentTimeMillis()
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val timeoutThreshold = maxOf(90000L, ttlSeconds * 1000L)
                if (isConnected.get() && now - lastPongTime > timeoutThreshold) {
                    Log.w(TAG, "No pong received for ${timeoutThreshold / 1000}s. Suspending/Disconnecting WebSockets.")
                    suspendConnection()
                    break
                }
            }
        }
    }

    fun publishPing() {
        nostrClient?.publish("ping", "{\"type\":\"ping\"}", sessionExpiryTime / 1000L)
    }

    private fun refreshActivity() {
        sessionExpiryTime = System.currentTimeMillis() + (ttlSeconds * 1000L)
        saveSession(context, topic, ttlSeconds, relayUrls, privateKey, sessionExpiryTime)
    }

    private fun handleNostrEvent(event: NostrEvent) {
        if (event.pubkey.equals(myPubKeyHex, ignoreCase = true)) {
            Log.d(TAG, "Ignoring self-event (echoed from relay)")
            return
        }

        refreshActivity()

        val typeTag = event.tags.find { it.size >= 2 && it[0] == "type" }?.get(1) ?: return
        Log.d(TAG, "Received Nostr event type: $typeTag")

        when (typeTag) {
            "ping" -> {
                // Respond with pong
                nostrClient?.publish("pong", "{\"type\":\"pong\"}", sessionExpiryTime / 1000L)
                if (isConnected.compareAndSet(false, true)) {
                    lastPongTime = System.currentTimeMillis()
                    onStateChanged(State.CONNECTED)
                }
            }
            "pong" -> {
                lastPongTime = System.currentTimeMillis()
                if (isConnected.compareAndSet(false, true)) {
                    onStateChanged(State.CONNECTED)
                }
            }
            "request" -> {
                try {
                    val rawPayload = if (event.content.trim().startsWith("{")) {
                        val root = Json.parseToJsonElement(event.content).jsonObject
                        val payloadB64 = root["payload"]?.jsonPrimitive?.content ?: return
                        Base64.decode(payloadB64, Base64.DEFAULT)
                    } else {
                        Base64.decode(event.content, Base64.DEFAULT)
                    }

                    // Register Nostr response sender
                    (context as? MainActivity)?.activeMessageSender = object : OmsMessageSender {
                        override fun sendReply(data: ByteArray, shouldClose: Boolean) {
                            refreshActivity()
                            val replyB64 = Base64.encodeToString(data, Base64.NO_WRAP)
                            nostrClient?.publish("response", replyB64, sessionExpiryTime / 1000L)
                        }
                    }

                    // Feed decrypted request into the message flow
                    messageConsumer(rawPayload)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling request payload: ${e.message}", e)
                }
            }
            "disconnect", "bye" -> {
                Log.d(TAG, "Disconnect command received from remote client")
                disconnect()
            }
        }
    }

    fun suspendConnection() {
        Log.d(TAG, "Suspending NostrConnectionManager (closing WebSockets without sending disconnect event)")
        pingJob?.cancel()
        monitorJob?.cancel()
        scope.cancel()
        nostrClient?.close()
        nostrClient = null
        isConnected.set(false)
        onStateChanged(State.DISCONNECTED)
        (context as? MainActivity)?.activeMessageSender = null
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting NostrConnectionManager permanently")
        pingJob?.cancel()
        monitorJob?.cancel()
        scope.cancel()
        
        // Publish disconnect event to notify the peer
        try {
            nostrClient?.publish("disconnect", "{\"type\":\"disconnect\"}", sessionExpiryTime / 1000L)
        } catch (e: Exception) {
            Log.e(TAG, "Could not send disconnect event: ${e.message}")
        }
        
        nostrClient?.close()
        nostrClient = null
        isConnected.set(false)
        onStateChanged(State.DISCONNECTED)
        (context as? MainActivity)?.activeMessageSender = null

        // Clear saved session parameters from storage
        clearSession(context)
    }
}
