package com.onemoresecret.nostr

import android.util.Log
import fr.acinq.secp256k1.Secp256k1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*
import okhttp3.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Serializable
data class NostrEvent(
    val id: String,
    val pubkey: String,
    @SerialName("created_at") val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val sig: String
)

object NostrCrypto {
    private val secureRandom = SecureRandom()

    fun generatePrivateKey(): ByteArray {
        val key = ByteArray(32)
        do {
            secureRandom.nextBytes(key)
        } while (!Secp256k1.secKeyVerify(key))
        return key
    }

    fun getXOnlyPublicKey(privateKey: ByteArray): ByteArray {
        val pubkey = Secp256k1.pubkeyCreate(privateKey)
        val compressed = Secp256k1.pubKeyCompress(pubkey)
        return compressed.copyOfRange(1, 33)
    }

    fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    fun signEvent(
        privateKey: ByteArray,
        createdAt: Long,
        kind: Int,
        tags: List<List<String>>,
        content: String
    ): NostrEvent {
        val pubKeyBytes = getXOnlyPublicKey(privateKey)
        val pubkeyHex = bytesToHex(pubKeyBytes)

        val jsonArray = Json.encodeToString(
            ListSerializer(JsonElement.serializer()),
            listOf(
                JsonPrimitive(0),
                JsonPrimitive(pubkeyHex),
                JsonPrimitive(createdAt),
                JsonPrimitive(kind),
                Json.encodeToJsonElement(tags),
                JsonPrimitive(content)
            )
        )

        val messageHash = MessageDigest.getInstance("SHA-256").digest(jsonArray.toByteArray(Charsets.UTF_8))
        val idHex = bytesToHex(messageHash)

        val auxRand = ByteArray(32)
        secureRandom.nextBytes(auxRand)

        val sigBytes = Secp256k1.signSchnorr(messageHash, privateKey, auxRand)
        val sigHex = bytesToHex(sigBytes)

        return NostrEvent(
            id = idHex,
            pubkey = pubkeyHex,
            createdAt = createdAt,
            kind = kind,
            tags = tags,
            content = content,
            sig = sigHex
        )
    }

    fun verifyEvent(event: NostrEvent): Boolean {
        return try {
            val pubkeyBytes = hexToBytes(event.pubkey)
            val sigBytes = hexToBytes(event.sig)

            val jsonArray = Json.encodeToString(
                ListSerializer(JsonElement.serializer()),
                listOf(
                    JsonPrimitive(0),
                    JsonPrimitive(event.pubkey),
                    JsonPrimitive(event.createdAt),
                    JsonPrimitive(event.kind),
                    Json.encodeToJsonElement(event.tags),
                    JsonPrimitive(event.content)
                )
            )
            val messageHash = MessageDigest.getInstance("SHA-256").digest(jsonArray.toByteArray(Charsets.UTF_8))

            val idHex = bytesToHex(messageHash)
            if (idHex != event.id) return false

            Secp256k1.verifySchnorr(sigBytes, messageHash, pubkeyBytes)
        } catch (ex: Exception) {
            false
        }
    }
}

class NostrClient(
    private val relayUrls: List<String>,
    private val topic: String,
    val privateKey: ByteArray = NostrCrypto.generatePrivateKey(),
    private val minCreatedAt: Long = 0L,
    private val onRelayConnected: ((String) -> Unit)? = null,
    private val onEventReceived: (NostrEvent) -> Unit
) {
    private val client = OkHttpClient()
    private val webSockets = ConcurrentHashMap<String, WebSocket>()
    private val processedEventIds = Collections.synchronizedSet(LinkedHashSet<String>())
    private var isClosed = false

    companion object {
        private const val TAG = "NostrClient"
        private const val KIND_EPHEMERAL = 25000
    }

    fun connect() {
        if (isClosed) return
        for (url in relayUrls) {
            connectToRelay(url)
        }
    }

    private fun connectToRelay(url: String) {
        val request = Request.Builder().url(url).build()
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (isClosed) {
                    webSocket.close(1000, "Closed")
                    return
                }
                Log.d(TAG, "Connected to relay: $url")
                webSockets[url] = webSocket
                sendSubscription(webSocket)
                onRelayConnected?.invoke(url)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val jsonArray = Json.parseToJsonElement(text) as? JsonArray ?: return
                    if (jsonArray.size >= 2 && jsonArray[0].jsonPrimitive.content == "EVENT") {
                        val subId = jsonArray[1].jsonPrimitive.content
                        if (subId == "oms_sub") {
                            val eventObj = jsonArray[2]
                            val event = Json.decodeFromJsonElement<NostrEvent>(eventObj)
                            if (event.createdAt < minCreatedAt) {
                                Log.d(TAG, "Ignoring stale event created at ${event.createdAt} (minCreatedAt: $minCreatedAt)")
                                return
                            }
                            val nowSec = System.currentTimeMillis() / 1000L
                            if (event.createdAt < (nowSec - 60L)) {
                                Log.d(TAG, "Ignoring expired event created at ${event.createdAt} (now: $nowSec, age: ${nowSec - event.createdAt}s > 60s)")
                                return
                            }
                            if (NostrCrypto.verifyEvent(event)) {
                                if (processedEventIds.add(event.id)) {
                                    // Limit size of processed set
                                    if (processedEventIds.size > 200) {
                                        processedEventIds.remove(processedEventIds.first())
                                    }
                                    onEventReceived(event)
                                }
                            } else {
                                Log.w(TAG, "Received invalid Nostr event signature for id: ${event.id}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing Nostr message from $url: ${e.message}", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed connection to $url: $reason")
                webSockets.remove(url)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Failure on relay $url: ${t.message}", t)
                webSockets.remove(url)
                // Retry connection after 5 seconds if not closed
                if (!isClosed) {
                    Thread {
                        try {
                            Thread.sleep(5000)
                            if (!isClosed) {
                                Log.d(TAG, "Retrying connection to $url")
                                connectToRelay(url)
                            }
                        } catch (e: InterruptedException) {
                            // Ignored
                        }
                    }.start()
                }
            }
        }
        client.newWebSocket(request, listener)
    }

    private fun sendSubscription(webSocket: WebSocket) {
        try {
            val filter = JsonObject(
                mapOf(
                    "kinds" to JsonArray(listOf(JsonPrimitive(KIND_EPHEMERAL))),
                    "#t" to JsonArray(listOf(JsonPrimitive(topic)))
                )
            )
            val subMsg = JsonArray(
                listOf(
                    JsonPrimitive("REQ"),
                    JsonPrimitive("oms_sub"),
                    filter
                )
            )
            webSocket.send(Json.encodeToString(JsonArray.serializer(), subMsg))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send subscription: ${e.message}", e)
        }
    }

    fun publish(type: String, content: String, expirationSec: Long? = null, extraTags: List<List<String>> = emptyList()) {
        val tags = mutableListOf(
            listOf("t", topic),
            listOf("type", type)
        )
        if (expirationSec != null) {
            tags.add(listOf("expiration", expirationSec.toString()))
        }
        tags.addAll(extraTags)

        val event = NostrCrypto.signEvent(
            privateKey = privateKey,
            createdAt = System.currentTimeMillis() / 1000L,
            kind = KIND_EPHEMERAL,
            tags = tags,
            content = content
        )

        val eventMsg = JsonArray(
            listOf(
                JsonPrimitive("EVENT"),
                Json.encodeToJsonElement(NostrEvent.serializer(), event)
            )
        )
        val eventMsgStr = Json.encodeToString(JsonArray.serializer(), eventMsg)

        for (ws in webSockets.values) {
            try {
                ws.send(eventMsgStr)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send event to a relay: ${e.message}")
            }
        }
    }

    fun close() {
        isClosed = true
        for (ws in webSockets.values) {
            try {
                ws.close(1000, "Normal closure")
            } catch (e: Exception) {
                // Ignored
            }
        }
        webSockets.clear()
        client.dispatcher.executorService.shutdown()
    }
}
