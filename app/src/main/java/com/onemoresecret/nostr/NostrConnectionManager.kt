package com.onemoresecret.nostr

import android.content.Context
import android.util.Base64
import android.util.Log
import com.onemoresecret.MainActivity
import com.onemoresecret.OmsMessageSender
import com.onemoresecret.OmsPreferences
import com.onemoresecret.crypto.MessageComposer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicBoolean

class NostrConnectionManager(
    private val context: Context,
    private val topic: String,
    private val relayUrls: List<String>,
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
    private val isConnected = AtomicBoolean(false)
    private val myPubKeyHex = NostrCrypto.bytesToHex(NostrCrypto.getXOnlyPublicKey(privateKey))

    companion object {
        private const val TAG = "NostrConnectionManager"
        private const val PROP_NOSTR_TOPIC = "nostr_session_topic"
        private const val PROP_NOSTR_RELAYS = "nostr_session_relays"
        private const val PROP_NOSTR_PRIVKEY = "nostr_session_privkey"
        private const val PROP_NOSTR_LAST_TIMESTAMP = "nostr_session_last_timestamp"

        fun saveSession(context: Context, topic: String, relays: List<String>, privateKey: ByteArray) {
            val preferences = OmsPreferences.get(context)
            preferences.edit()
                .putString(PROP_NOSTR_TOPIC, topic)
                .putString(PROP_NOSTR_RELAYS, relays.joinToString(","))
                .putString(PROP_NOSTR_PRIVKEY, Base64.encodeToString(privateKey, Base64.NO_WRAP))
                .apply()
        }

        fun clearSession(context: Context) {
            val preferences = OmsPreferences.get(context)
            preferences.edit()
                .remove(PROP_NOSTR_TOPIC)
                .remove(PROP_NOSTR_RELAYS)
                .remove(PROP_NOSTR_PRIVKEY)
                .remove(PROP_NOSTR_LAST_TIMESTAMP)
                .apply()
        }

        fun hasValidSession(context: Context): Boolean {
            val preferences = OmsPreferences.get(context)
            val topic = preferences.getString(PROP_NOSTR_TOPIC, "")
            return !topic.isNullOrEmpty()
        }
    }

    fun start() {
        Log.d(TAG, "Starting NostrConnectionManager on topic: $topic")
        onStateChanged(State.CONNECTING)

        val pref = OmsPreferences.get(context)
        val lastTimestamp = pref.getLong(PROP_NOSTR_LAST_TIMESTAMP, 0L)

        nostrClient = NostrClient(
            relayUrls = relayUrls,
            topic = topic,
            privateKey = privateKey,
            minCreatedAt = lastTimestamp,
            onRelayConnected = { relayUrl ->
                Log.d(TAG, "Relay connected ($relayUrl) - publishing initial greeting ping")
                publishPing()
            },
            onEventReceived = { event ->
                handleNostrEvent(event)
            }
        )
        nostrClient?.connect()
    }

    fun publishPing() {
        nostrClient?.publish("ping", "{\"type\":\"ping\"}")
    }

    private fun handleNostrEvent(event: NostrEvent) {
        if (event.pubkey.equals(myPubKeyHex, ignoreCase = true)) {
            Log.d(TAG, "Ignoring self-event (echoed from relay)")
            return
        }

        val pref = OmsPreferences.get(context)
        val currentLast = pref.getLong(PROP_NOSTR_LAST_TIMESTAMP, 0L)
        if (event.createdAt > currentLast) {
            pref.edit().putLong(PROP_NOSTR_LAST_TIMESTAMP, event.createdAt).apply()
        }

        val typeTag = event.tags.find { it.size >= 2 && it[0] == "type" }?.get(1) ?: return
        Log.d(TAG, "Received Nostr event type: $typeTag")

        when (typeTag) {
            "ping" -> {
                // Respond with pong
                nostrClient?.publish("pong", "{\"type\":\"pong\"}")
                if (isConnected.compareAndSet(false, true)) {
                    onStateChanged(State.CONNECTED)
                }
            }
            "pong" -> {
                if (isConnected.compareAndSet(false, true)) {
                    onStateChanged(State.CONNECTED)
                }
            }
            "request" -> {
                try {
                    if (isConnected.compareAndSet(false, true)) {
                        onStateChanged(State.CONNECTED)
                    }
                    val reqId = event.tags.find { it.size >= 2 && it[0] == "req_id" }?.get(1)
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
                            val replyB64 = Base64.encodeToString(data, Base64.NO_WRAP)
                            val extraTags = if (reqId != null) listOf(listOf("reply_to", reqId)) else emptyList()
                            nostrClient?.publish("response", replyB64, extraTags = extraTags)
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
        nostrClient?.close()
        nostrClient = null
        isConnected.set(false)
        onStateChanged(State.DISCONNECTED)
        (context as? MainActivity)?.activeMessageSender = null
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting NostrConnectionManager permanently")
        
        // Publish disconnect event to notify the peer
        try {
            nostrClient?.publish("disconnect", "{\"type\":\"disconnect\"}")
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
