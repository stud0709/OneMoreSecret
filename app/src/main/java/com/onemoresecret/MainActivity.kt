package com.onemoresecret

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
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
    }
}
