package com.onemoresecret

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Arrays
import java.util.function.Consumer
import javax.crypto.Cipher

class MainActivity : FragmentActivity() {

    data class WiFiComm(
        val ipAdr: ByteArray,
        val port: Int,
        val publicKey: ByteArray,
        val tsExpiry: Long
    ) {
        fun isValid(): Boolean = System.currentTimeMillis() < tsExpiry

        fun getRsaPrivateKeyMaterial(preferences: SharedPreferences): ByteArray {
            return Base64.decode(preferences.getString(PROP_PRIVATE_KEY_COMM, ""), Base64.DEFAULT)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is WiFiComm) return false

            if (port != other.port) return false
            if (tsExpiry != other.tsExpiry) return false
            if (!ipAdr.contentEquals(other.ipAdr)) return false
            if (!publicKey.contentEquals(other.publicKey)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = port
            result = 31 * result + tsExpiry.hashCode()
            result = 31 * result + ipAdr.contentHashCode()
            result = 31 * result + publicKey.contentHashCode()
            return result
        }
    }

    private var wiFiComm: WiFiComm? = null
    private var wiFiListenerShutdown: Runnable? = null
    private var socketWaitingForReply: Socket? = null
    private lateinit var preferences: SharedPreferences

    val isWiFiCommSet: Boolean
        @Synchronized get() = wiFiComm != null

    @Synchronized
    fun setWiFiComm(wiFiComm: WiFiComm?, rsaPrivateKeyMaterial: ByteArray?) {
        destroyWiFiListener()

        this.wiFiComm = wiFiComm

        val editor = preferences.edit()
        if (wiFiComm == null) {
            editor.remove(PROP_PRIVATE_KEY_COMM)
                .remove(PROP_PUBLIC_KEY_COMM)
                .remove(PROP_WIFI_COMM_EXP)
                .remove(PROP_IPADDR)
                .remove(PROP_PORT).apply()
        } else {
            editor.putString(PROP_PRIVATE_KEY_COMM, Base64.encodeToString(rsaPrivateKeyMaterial, Base64.DEFAULT))
                .putString(PROP_PUBLIC_KEY_COMM, Base64.encodeToString(wiFiComm.publicKey, Base64.DEFAULT))
                .putLong(PROP_WIFI_COMM_EXP, wiFiComm.tsExpiry)
                .putString(PROP_IPADDR, Base64.encodeToString(wiFiComm.ipAdr, Base64.DEFAULT))
                .putInt(PROP_PORT, wiFiComm.port).apply()
        }
    }

    @Synchronized
    private fun getWiFiComm(): WiFiComm? {
        if (wiFiComm == null && preferences.contains(PROP_WIFI_COMM_EXP)) {
            val publicKeyMaterial = Base64.decode(preferences.getString(PROP_PUBLIC_KEY_COMM, ""), Base64.DEFAULT)
            val ipaddrByte = Base64.decode(preferences.getString(PROP_IPADDR, ""), Base64.DEFAULT)
            val port = preferences.getInt(PROP_PORT, 0)
            val ts_expiry = preferences.getLong(PROP_WIFI_COMM_EXP, 0)

            wiFiComm = WiFiComm(ipaddrByte, port, publicKeyMaterial, ts_expiry)
        }

        if (wiFiComm != null && !wiFiComm!!.isValid()) {
            setWiFiComm(null, null)
        }

        return wiFiComm
    }

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
        destroyWiFiListener()
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
        var shouldClose = closeSocket
        Log.d(TAG, "sendReplyViaSocket: ENTERED. Data size: ${data.size}, closeSocket: $closeSocket, Caller: ${Thread.currentThread().stackTrace[3]}")
        if (socketWaitingForReply == null || socketWaitingForReply!!.isClosed) {
            Log.w(TAG, "sendReplyViaSocket: Socket waiting for reply not set or closed")
            return
        }
        try {
            val comm = getWiFiComm()
            if (comm == null) {
                Log.e(TAG, "sendReplyViaSocket: getWiFiComm() returned null! Aborting without sending.")
                return
            }
            Log.d(TAG, "sendReplyViaSocket: Comm is valid. Creating envelope...")
            val reply = MessageComposer.createRsaAesEnvelope(
                comm.publicKey,
                RSAUtil.getRsaTransformation(preferences),
                AESUtil.getKeyLength(preferences),
                AESUtil.getAesTransformation(preferences),
                data
            )
            Log.d(TAG, "sendReplyViaSocket: Envelope created. Size: ${reply.size}")

            val outputStream = socketWaitingForReply!!.getOutputStream()
            outputStream.write(reply)
            outputStream.flush()
            Log.d(TAG, "sendReplyViaSocket: Successfully wrote envelope to output stream.")
        } catch (ex: Exception) {
            Log.e(TAG, "sendReplyViaSocket: EXCEPTION THROWN: ${ex.message}", ex)
            Util.printStackTrace(ex)
            shouldClose = true
        } finally {
            Log.d(TAG, "sendReplyViaSocket: FINALLY block. shouldClose = $shouldClose")
            if (shouldClose) {
                try {
                    socketWaitingForReply?.close()
                    Log.d(TAG, "sendReplyViaSocket: socketWaitingForReply closed.")
                } catch (ignored: IOException) {
                    Log.e(TAG, "sendReplyViaSocket: Exception while closing socket", ignored)
                } finally {
                    socketWaitingForReply = null
                }
            }
        }
    }

    @Synchronized
    fun destroyWiFiListener() {
        if (wiFiListenerShutdown == null) return
        wiFiListenerShutdown?.run()
        wiFiListenerShutdown = null
    }

    fun startWiFiListener(messageConsumer: Consumer<String>, onSuccess: Runnable) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ServerSocket().use { serverSocket ->
                    synchronized(MainActivity::class.java) {
                        if (socketWaitingForReply != null && !socketWaitingForReply!!.isClosed) {
                            sendReplyViaSocket(ByteArray(0), true)
                        }

                        destroyWiFiListener()

                        val comm = getWiFiComm() ?: return@launch

                        wiFiListenerShutdown = Runnable {
                            try {
                                serverSocket.close()
                            } catch (e: IOException) {
                                Util.printStackTrace(e)
                            }
                        }

                        serverSocket.reuseAddress = true

                        serverSocket.bind(
                            InetSocketAddress(
                                Inet4Address.getByAddress(comm.ipAdr),
                                comm.port
                            )
                        )
                        onSuccess.run()
                    }

                    while (isActive) {
                        try {
                            val socket = serverSocket.accept()
                            onWiFiConnection(socket, messageConsumer)
                        } catch (ex: Exception) {
                            Util.printStackTrace(ex)
                            break
                        }
                    }
                }
            } catch (ex: Exception) {
                Util.printStackTrace(ex)
                setWiFiComm(null, null)
                runOnUiThread {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.wifi_pairing_error)
                        .setMessage(R.string.wifi_pairing_error_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .create()
                        .show()
                }
            }
        }
    }

    private fun onWiFiConnection(socket: Socket, messageConsumer: Consumer<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dataInputStream = OmsDataInputStream(socket.getInputStream())
                val envelope = MessageComposer.readRsaAesEnvelope(dataInputStream)
                val encryptedMessage = dataInputStream.readByteArray()

                val rsaPrivateKeyMaterial = getWiFiComm()!!.getRsaPrivateKeyMaterial(preferences)
                val aesSecretKeyData = RSAUtil.process(
                    Cipher.DECRYPT_MODE,
                    rsaPrivateKeyMaterial,
                    envelope.rsaTransformation,
                    envelope.encryptedAesSecretKey
                )
                Arrays.fill(rsaPrivateKeyMaterial, 0.toByte())

                val decryptedMessage = AESUtil.process(
                    Cipher.DECRYPT_MODE,
                    encryptedMessage,
                    aesSecretKeyData,
                    envelope.iv,
                    envelope.aesTransformation
                )
                Arrays.fill(aesSecretKeyData, 0.toByte())

                synchronized(MainActivity::class.java) {
                    socketWaitingForReply = socket
                }

                messageConsumer.accept(MessageComposer.encodeAsOmsText(decryptedMessage))
            } catch (ex: Exception) {
                Util.printStackTrace(ex)
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        ex.message ?: ex.javaClass.name,
                        Toast.LENGTH_LONG
                    ).show()
                }
                try {
                    socket.close()
                } catch (_: IOException) {}
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private const val PROP_PRIVATE_KEY_COMM = "private_key_comm_base64"
        private const val PROP_PUBLIC_KEY_COMM = "public_key_comm_base64"
        private const val PROP_WIFI_COMM_EXP = "wifi_comm_exp"
        private const val PROP_IPADDR = "wifi_comm_ip"
        private const val PROP_PORT = "wifi_comm_port"
    }
}
