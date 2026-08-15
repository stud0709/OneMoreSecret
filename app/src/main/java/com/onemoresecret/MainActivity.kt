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

    var activeMessageSender: OmsMessageSender? = null
    var activeNostrConnection: com.onemoresecret.nostr.NostrConnectionManager? = null

    val isWiFiCommSet: Boolean
        @Synchronized get() = activeNostrConnection != null

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
        Log.d(TAG, "sendReplyViaSocket: Delegating to activeMessageSender. Data size: ${data.size}, closeSocket: $closeSocket")
        val sender = activeMessageSender
        if (sender != null) {
            sender.sendReply(data, closeSocket)
        } else {
            Log.e(TAG, "sendReplyViaSocket: activeMessageSender is null! Cannot send reply.")
        }
    }

    @Synchronized
    fun destroyWiFiListener() {
        Log.d(TAG, "destroyWiFiListener: closing active Nostr connection")
        activeNostrConnection?.disconnect()
        activeNostrConnection = null
    }

    var messageConsumer: Consumer<String>? = null

    fun startWiFiListener(messageConsumer: Consumer<String>, onSuccess: Runnable) {
        this.messageConsumer = messageConsumer
        onSuccess.run()
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
