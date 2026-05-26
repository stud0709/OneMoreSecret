package com.onemoresecret.msg_fragment_plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.R
import com.onemoresecret.crypto.OneTimePassword
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

class MsgPluginTotp(
    activity: FragmentActivity,
    messageData: ByteArray,
    hiddenState: MutableStateFlow<Boolean>,
    onNavigateBack: () -> Unit
) : MsgPluginEncryptedMessage(activity, messageData, hiddenState, onNavigateBack) {

    private val otp = OneTimePassword(String(messageData))

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        var code by remember { mutableStateOf("") }
        var remaining by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            while (true) {
                val state = otp.state
                remaining = (otp.period - state.secondsUntilNext).toInt()
                code = otp.generateResponseCode(state.current)
                delay(1000)
            }
        }

        Column {
            Text("...${remaining}s")
            Text(if (hiddenState) "●".repeat(code.length) else code)
        }
    }
}
