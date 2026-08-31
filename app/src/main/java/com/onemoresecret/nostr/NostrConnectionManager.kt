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
    private val psk: ByteArray,
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
        private const val PROP_NOSTR_PSK = "nostr_session_psk"
        private const val PROP_NOSTR_RELAYS = "nostr_session_relays"
        private const val PROP_NOSTR_PRIVKEY = "nostr_session_privkey"
        private const val PROP_NOSTR_LAST_TIMESTAMP = "nostr_session_last_timestamp"
        private const val PROP_NOSTR_EXPIRES_AT = "nostr_session_expires_at"
        const val DEFAULT_SESSION_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

        fun saveSession(
            context: Context,
            topic: String,
            psk: ByteArray,
            relays: List<String>,
            privateKey: ByteArray,
            ttlMs: Long = DEFAULT_SESSION_TTL_MS
        ) {
            val preferences = OmsPreferences.get(context)
            val expiresAt = System.currentTimeMillis() + ttlMs
            preferences.edit()
                .putString(PROP_NOSTR_TOPIC, topic)
                .putString(PROP_NOSTR_PSK, Base64.encodeToString(psk, Base64.NO_WRAP))
                .putString(PROP_NOSTR_RELAYS, relays.joinToString(","))
                .putString(PROP_NOSTR_PRIVKEY, Base64.encodeToString(privateKey, Base64.NO_WRAP))
                .putLong(PROP_NOSTR_EXPIRES_AT, expiresAt)
                .apply()
        }

        fun clearSession(context: Context) {
            val preferences = OmsPreferences.get(context)
            preferences.edit()
                .remove(PROP_NOSTR_TOPIC)
                .remove(PROP_NOSTR_PSK)
                .remove(PROP_NOSTR_RELAYS)
                .remove(PROP_NOSTR_PRIVKEY)
                .remove(PROP_NOSTR_LAST_TIMESTAMP)
                .remove(PROP_NOSTR_EXPIRES_AT)
                .apply()
            NostrClient.clearProcessedEventCache()
        }

        fun hasValidSession(context: Context): Boolean {
            val preferences = OmsPreferences.get(context)
            val topic = preferences.getString(PROP_NOSTR_TOPIC, "")
            val psk = preferences.getString(PROP_NOSTR_PSK, "")
            val expiresAt = preferences.getLong(PROP_NOSTR_EXPIRES_AT, 0L)
            
            if (topic.isNullOrEmpty() || psk.isNullOrEmpty()) return false
            
            if (expiresAt > 0L && System.currentTimeMillis() > expiresAt) {
                Log.d(TAG, "Nostr pairing session expired after 24h. Clearing session.")
                clearSession(context)
                return false
            }
            return true
        }
    }

    private val hasSentPairedAck = AtomicBoolean(false)

    fun start(isNewPairing: Boolean = false) {
        Log.d(TAG, "Starting NostrConnectionManager on topic: $topic (isNewPairing=$isNewPairing)")
        onStateChanged(State.CONNECTING)

        val pref = OmsPreferences.get(context)
        val lastTimestamp = pref.getLong(PROP_NOSTR_LAST_TIMESTAMP, 0L)

        nostrClient = NostrClient(
            relayUrls = relayUrls,
            topic = topic,
            privateKey = privateKey,
            minCreatedAt = lastTimestamp,
            onRelayConnected = { relayUrl ->
                if (isNewPairing && hasSentPairedAck.compareAndSet(false, true)) {
                    Log.d(TAG, "Relay connected ($relayUrl) - publishing initial one-shot paired acknowledgment")
                    publishPairedAck()
                }
                if (isConnected.compareAndSet(false, true)) {
                    onStateChanged(State.CONNECTED)
                }
            },
            onEventReceived = { event ->
                handleNostrEvent(event)
            }
        )
        nostrClient?.connect()
    }

    fun publishEncrypted(innerJson: String, extraTags: List<List<String>> = emptyList()) {
        try {
            val encryptedBytes = NostrPskCrypto.encrypt(innerJson.toByteArray(Charsets.UTF_8), psk)
            val encryptedB64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            nostrClient?.publish(encryptedB64, extraTags)
        } catch (e: Exception) {
            Log.e(TAG, "Error encrypting Nostr payload with PSK: ${e.message}", e)
        }
    }

    fun publishPairedAck() {
        publishEncrypted("{\"type\":\"paired\"}")
    }

    private fun handleNostrEvent(event: NostrEvent) {
        if (event.pubkey.equals(myPubKeyHex, ignoreCase = true)) {
            Log.d(TAG, "Ignoring self-event (echoed from relay)")
            return
        }

        // Authenticate & Decrypt payload with PSK
        val decryptedJsonStr = try {
            val encryptedBytes = Base64.decode(event.content, Base64.DEFAULT)
            val plaintextBytes = NostrPskCrypto.decrypt(encryptedBytes, psk)
            String(plaintextBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Dropped unauthenticated Nostr event (wrong PSK or corrupt): ${e.message}")
            return
        }

        val pref = OmsPreferences.get(context)
        val currentLast = pref.getLong(PROP_NOSTR_LAST_TIMESTAMP, 0L)
        if (event.createdAt > currentLast) {
            pref.edit().putLong(PROP_NOSTR_LAST_TIMESTAMP, event.createdAt).apply()
        }

        val root = try {
            Json.parseToJsonElement(decryptedJsonStr).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "Invalid JSON in decrypted Nostr event: $decryptedJsonStr", e)
            return
        }

        val type = root["type"]?.jsonPrimitive?.content ?: return
        Log.d(TAG, "Received authenticated Nostr event type: $type")

        when (type) {
            "request" -> {
                try {
                    if (isConnected.compareAndSet(false, true)) {
                        onStateChanged(State.CONNECTED)
                    }
                    val reqId = root["req_id"]?.jsonPrimitive?.content
                    val payloadB64 = root["payload"]?.jsonPrimitive?.content ?: return
                    val rawPayload = Base64.decode(payloadB64, Base64.DEFAULT)

                    // Register Nostr response sender
                    (context as? MainActivity)?.activeMessageSender = object : OmsMessageSender {
                        override fun sendReply(data: ByteArray, shouldClose: Boolean) {
                            val replyB64 = Base64.encodeToString(data, Base64.NO_WRAP)
                            val responseJson = if (reqId != null) {
                                "{\"type\":\"response\",\"reply_to\":\"$reqId\",\"payload\":\"$replyB64\"}"
                            } else {
                                "{\"type\":\"response\",\"payload\":\"$replyB64\"}"
                            }
                            publishEncrypted(responseJson)
                        }
                    }

                    // Feed decrypted request into the message flow
                    messageConsumer(rawPayload)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling request payload: ${e.message}", e)
                }
            }
            "disconnect" -> {
                Log.d(TAG, "Authenticated disconnect command received from remote client")
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
            publishEncrypted("{\"type\":\"disconnect\"}")
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
