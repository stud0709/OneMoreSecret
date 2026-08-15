package com.onemoresecret.nostr

import android.content.Context
import android.util.Base64
import android.util.Log
import com.onemoresecret.MainActivity
import com.onemoresecret.OmsMessageSender
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

    companion object {
        private const val TAG = "NostrConnectionManager"
    }

    fun start() {
        Log.d(TAG, "Starting NostrConnectionManager on topic: $topic")
        sessionExpiryTime = System.currentTimeMillis() + (ttlSeconds * 1000L)
        onStateChanged(State.CONNECTING)

        nostrClient = NostrClient(relayUrls, topic) { event ->
            handleNostrEvent(event)
        }
        nostrClient?.connect()

        // Periodically send ping
        pingJob = scope.launch {
            while (isActive) {
                if (System.currentTimeMillis() > sessionExpiryTime) {
                    Log.d(TAG, "Session TTL expired")
                    disconnect()
                    break
                }
                // Send Ping
                nostrClient?.publish("ping", "{\"type\":\"ping\"}", sessionExpiryTime / 1000L)
                delay(10000) // Every 10 seconds
            }
        }

        // Connection monitor
        monitorJob = scope.launch {
            lastPongTime = System.currentTimeMillis()
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                if (isConnected.get() && now - lastPongTime > 30000L) {
                    Log.w(TAG, "No pong received for 30s. Disconnecting.")
                    disconnect()
                    break
                }
            }
        }
    }

    private fun handleNostrEvent(event: NostrEvent) {
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
                            val replyB64 = Base64.encodeToString(data, Base64.NO_WRAP)
                            nostrClient?.publish("response", replyB64, sessionExpiryTime / 1000L)
                            if (shouldClose) {
                                disconnect()
                            }
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

    fun disconnect() {
        if (nostrClient == null) return
        Log.d(TAG, "Disconnecting NostrConnectionManager")
        nostrClient?.publish("disconnect", "{\"type\":\"disconnect\"}", sessionExpiryTime / 1000L)
        isConnected.set(false)
        pingJob?.cancel()
        monitorJob?.cancel()
        scope.cancel()
        nostrClient?.close()
        nostrClient = null
        onStateChanged(State.DISCONNECTED)
        (context as? MainActivity)?.activeMessageSender = null
    }
}
