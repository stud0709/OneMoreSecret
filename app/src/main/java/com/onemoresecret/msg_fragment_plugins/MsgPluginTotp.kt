package com.onemoresecret.msg_fragment_plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.R
import com.onemoresecret.crypto.OneTimePassword
import com.onemoresecret.composable.TotpCodeView
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

class MsgPluginTotp(
    activity: FragmentActivity,
    messageData: ByteArray,
    onNavigateBack: () -> Unit
) : MsgPluginEncryptedMessage(activity, messageData, onNavigateBack) {

    private val otp = OneTimePassword(String(messageData))

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        var code by remember { mutableStateOf("") }
        var remaining by remember { mutableIntStateOf(0) }
        val title = activity.getString(R.string.one_time_password)

        LaunchedEffect(Unit) {
            while (true) {
                val state = otp.state
                remaining = (otp.period - state.secondsUntilNext).toInt()
                val newCode = otp.generateResponseCode(state.current)
                if (code != newCode) {
                    code = newCode
                    outputViewModel.setMessage(newCode, title)
                }
                delay(500.milliseconds)
            }
        }

        TotpCodeView(
            code = code,
            remaining = "...${remaining}s",
            hiddenState = hiddenState
        )
    }
}
