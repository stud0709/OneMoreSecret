package com.onemoresecret

import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.ui.AppBarConfiguration
import com.onemoresecret.crypto.AESUtil.getAesTransformationIdx
import com.onemoresecret.crypto.AESUtil.getKeyLength
import com.onemoresecret.crypto.AESUtil.process
import com.onemoresecret.crypto.MessageComposer.Companion.createRsaAesEnvelope
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.crypto.MessageComposer.Companion.readRsaAesEnvelope
import com.onemoresecret.crypto.RSAUtils.getRsaTransformationIdx
import com.onemoresecret.crypto.RSAUtils.process
import com.onemoresecret.crypto.RSAUtils.restorePrivateKey
import com.onemoresecret.crypto.RSAUtils.restorePublicKey
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.NoSuchAlgorithmException
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.InvalidKeySpecException
import java.util.Objects
import java.util.function.Consumer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.onemoresecret.compose.QrScreen
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var appBarConfiguration: AppBarConfiguration? = null

    @JvmRecord
    data class WiFiComm(
        val ipAdr: ByteArray, val port: Int, val privateKey: RSAPrivateKey,
        val publicKey: RSAPublicKey, val tsExpiry: Long
    ) {
        val isValid: Boolean
            get() = System.currentTimeMillis() < tsExpiry
    }

    private var wiFiComm: WiFiComm? = null
    private var wiFiListenerShutdown: Runnable? = null
    private var socketWaitingForReply: Socket? = null
    private var preferences: SharedPreferences? = null

    fun setWiFiComm(wiFiComm: WiFiComm?) {
        synchronized(MainActivity::class.java) {
            destroyWiFiListener()
            this.wiFiComm = wiFiComm

            if (preferences == null) preferences = getPreferences(MODE_PRIVATE)
            if (wiFiComm == null) {
                preferences!!.edit(commit = true) {
                    remove(PROP_PRIVATE_KEY_COMM)
                        .remove(PROP_PUBLIC_KEY_COMM)
                        .remove(PROP_WIFI_COMM_EXP)
                        .remove(PROP_IPADDR)
                        .remove(PROP_PORT)
                }
            } else {
                preferences!!.edit(commit = true) {
                    putString(
                        PROP_PRIVATE_KEY_COMM,
                        Base64.encodeToString(wiFiComm.privateKey.encoded, Base64.DEFAULT)
                    )
                        .putString(
                            PROP_PUBLIC_KEY_COMM,
                            Base64.encodeToString(wiFiComm.publicKey.encoded, Base64.DEFAULT)
                        )
                        .putLong(PROP_WIFI_COMM_EXP, wiFiComm.tsExpiry)
                        .putString(
                            PROP_IPADDR,
                            Base64.encodeToString(wiFiComm.ipAdr, Base64.DEFAULT)
                        )
                        .putInt(PROP_PORT, wiFiComm.port)
                }
            }
        }
        invalidateOptionsMenu()
    }

    val isWiFiCommSet: Boolean
        get() {
            synchronized(MainActivity::class.java) {
                return wiFiComm != null
            }
        }

    @Throws(InvalidKeySpecException::class, NoSuchAlgorithmException::class)
    private fun getWiFiComm(): WiFiComm? {
        synchronized(MainActivity::class.java) {
            if (wiFiComm == null && preferences!!.contains(PROP_WIFI_COMM_EXP)) {
                val privateKey = restorePrivateKey(
                    Base64.decode(
                        preferences!!.getString(PROP_PRIVATE_KEY_COMM, ""), Base64.DEFAULT
                    )
                )
                val publicKey = restorePublicKey(
                    Base64.decode(
                        preferences!!.getString(PROP_PUBLIC_KEY_COMM, ""), Base64.DEFAULT
                    )
                )
                val ipaddrByte =
                    Base64.decode(preferences!!.getString(PROP_IPADDR, ""), Base64.DEFAULT)
                val port = preferences!!.getInt(PROP_PORT, 0)
                val tsExpiry = preferences!!.getLong(PROP_WIFI_COMM_EXP, 0)

                wiFiComm = WiFiComm(ipaddrByte, port, privateKey, publicKey, tsExpiry)
            }
            //it is possible that we have read outdated settings
            if (wiFiComm != null && !wiFiComm!!.isValid) {
                setWiFiComm(null)
            }
            return wiFiComm
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread.setDefaultUncaughtExceptionHandler(OmsUncaughtExceptionHandler(this))
        if (preferences == null) preferences = getPreferences(MODE_PRIVATE)

        //prevent screenshots
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        setContent {MainScreen()}
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen() {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("My App") },
                    navigationIcon = {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "QrScreen",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("QrScreen") { QrScreen(navController, snackbarHostState) }
            }
        }

        LaunchedEffect(Unit) { OmsFileProvider.purgeTmp(applicationContext) }
    }

    override fun onDestroy() {
        super.onDestroy()
        OmsFileProvider.purgeTmp(this@MainActivity)
        destroyWiFiListener()
    }

    override fun onPause() {
        super.onPause()
        destroyWiFiListener()
    }

    /**
     * Entry point for the intent is [QRFragment]. When a new intent arrives, the app state is unclear.
     * Therefore we just restart the app.
     *
     * @param intent The new intent that was started for the activity.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    fun sendReplyViaSocket(data: ByteArray, closeSocket: Boolean) {
        var tmpCloseSocket = closeSocket

        synchronized(MainActivity::class.java) {
            if (socketWaitingForReply == null || socketWaitingForReply!!.isClosed) {
                Log.w(TAG, "sendReplyViaSocket: Socket waiting for reply not set or closed")
                return
            }
            Log.d(
                TAG, String.format(
                    "sendReplyViaSocket: Sending %s bytes via socket waiting for reply. Caller: %s",
                    data.size,
                    Thread.currentThread().stackTrace[3].toString()
                )
            )
            try {
                val wiFiComm = getWiFiComm()
                val reply = createRsaAesEnvelope(
                    wiFiComm!!.publicKey,
                    getRsaTransformationIdx(preferences!!),
                    getKeyLength(preferences!!),
                    getAesTransformationIdx(preferences!!),
                    data
                )

                val outputStream = socketWaitingForReply!!.getOutputStream()

                outputStream.write(reply)
                outputStream.flush()

                Log.d(TAG, "sendReplyViaSocket: Data successfully sent")
            } catch (ex: Exception) {
                ex.printStackTrace()
                tmpCloseSocket = true
            } finally {
                if (tmpCloseSocket) {
                    try {
                        socketWaitingForReply!!.close()
                    } catch (ignored: IOException) {
                    } finally {
                        socketWaitingForReply = null
                        Log.d(TAG, "sendReplyViaSocket: Socket closed")
                    }
                }
            }
        }
    }

    fun destroyWiFiListener() {
        synchronized(MainActivity::class.java) {
            if (wiFiListenerShutdown == null) return
            Log.d(
                TAG,
                "destroyWiFiListener caller:" + Thread.currentThread().stackTrace[3].toString()
            )
            wiFiListenerShutdown!!.run()
            wiFiListenerShutdown = null
        }
    }

    fun startWiFiListener(messageConsumer: Consumer<String>, onSuccess: Runnable) {
        val wiFiListener = Thread(Runnable {
            try {
                ServerSocket().use { serverSocket ->
                    Log.d(
                        TAG, String.format(
                            "startWiFiListener caller: %s, hash: %s",
                            Thread.currentThread().stackTrace[3].toString(),
                            Objects.hashCode(Thread.currentThread())
                        )
                    )
                    synchronized(MainActivity::class.java) {
                        if (socketWaitingForReply != null && !socketWaitingForReply!!.isClosed) {
                            Log.d(
                                TAG,
                                "startWiFiListener: socketWaitingForReply found - sending empty reply"
                            )

                            //If we have gotten here, the message processing was cancelled.
                            //Notify the client sending an empty (though valid) reply
                            sendReplyViaSocket(byteArrayOf(), true)
                        }
                        destroyWiFiListener()

                        val wiFiComm = getWiFiComm() ?: return@Runnable

                        wiFiListenerShutdown = Runnable {
                        try {
                            serverSocket.close()
                            Log.d(TAG, "Server socket closed")
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }
                    }

                        Log.d(
                            TAG, String.format(
                                "startWiFiListener: Starting WiFi Listener on %s:%s...",
                                Inet4Address.getByAddress(wiFiComm.ipAdr),
                                wiFiComm.port
                            )
                        )

                        serverSocket.bind(
                            InetSocketAddress(
                                Inet4Address.getByAddress(wiFiComm.ipAdr),
                                wiFiComm.port
                            )
                        )

                        Log.d(
                            TAG, String.format(
                                "startWiFiListener: bound to %s:%s...",
                                Inet4Address.getByAddress(wiFiComm.ipAdr),
                                wiFiComm.port
                            )
                        )
                        onSuccess.run()
                    }
                    while (!Thread.currentThread().isInterrupted) {
                        try {
                            onWiFiConnection(serverSocket.accept(), messageConsumer)
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            break
                        }
                    }
                }
            } catch (ex: Exception) {
                //server socket is broken
                ex.printStackTrace()

                setWiFiComm(null)

                this.mainExecutor.execute {
                    val builder =
                        AlertDialog.Builder(this)
                    builder.setTitle(R.string.wifi_pairing_error)
                        .setMessage(R.string.wifi_pairing_error_message)
                        .setPositiveButton(android.R.string.ok, null)

                    val dialog = builder.create()
                    dialog.show()
                }
            }
            Log.d(TAG, "startWiFiListener: WiFi Listener has exited")
        })

        wiFiListener.isDaemon = true
        wiFiListener.start()
    }

    private fun onWiFiConnection(socket: Socket, messageConsumer: Consumer<String>) {
        val t = Thread {
            Log.d(
                TAG,
                "onWiFiConnection: Incoming socket connection"
            )
            try {
                val dataInputStream =
                    OmsDataInputStream(socket.getInputStream())
                //a transaction consists of a request and a response. End of request is signalled by shutdownOutput on the socket
                val envelope = readRsaAesEnvelope(dataInputStream)

                //stream has been shut down
                val encryptedMessage = dataInputStream.readByteArray()

                Log.d(
                    TAG,
                    String.format(
                        "onWiFiConnection: Message has been received, %s bytes",
                        encryptedMessage.size
                    )
                )

                // decrypt AES key
                val aesSecretKeyData = process(
                    Cipher.DECRYPT_MODE, getWiFiComm()!!.privateKey,
                    envelope.rsaTransormation, envelope.encryptedAesSecretKey
                )
                val aesSecretKey =
                    SecretKeySpec(aesSecretKeyData, "AES")

                // (7) AES-encrypted message
                val decryptedMessage = process(
                    Cipher.DECRYPT_MODE, encryptedMessage, aesSecretKey,
                    IvParameterSpec(envelope.iv), envelope.aesTransformation
                )

                synchronized(MainActivity::class.java) {
                    //keep socket open, wait for the reply
                    socketWaitingForReply = socket
                }

                messageConsumer.accept(encodeAsOmsText(decryptedMessage))
            } catch (ex: Exception) {
                ex.printStackTrace()
                this.mainExecutor.execute {
                    Toast.makeText(
                        this,
                        Objects.requireNonNullElse(
                            ex.message,
                            ex.javaClass.name
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                }
                Log.d(TAG, "onWiFiConnection: Closing socket")
                try {
                    socket.close()
                } catch (ignored: IOException) {
                }
            }
        }

        t.start()
    }

    companion object {
        private const val PROP_PRIVATE_KEY_COMM = "private_key_comm_base64"
        private const val PROP_PUBLIC_KEY_COMM = "public_key_comm_base64"
        private const val PROP_WIFI_COMM_EXP = "wifi_comm_exp"
        private const val PROP_IPADDR = "wifi_comm_ip"
        private const val PROP_PORT = "wifi_comm_port"
        private val TAG: String = MainActivity::class.java.simpleName
        const val SPARED_PREF_NAME = "com.onemoresecret"
    }
}

