package com.onemoresecret.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.onemoresecret.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun KeyRequestPairingScreen(
    replyState: ByteArray?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    KeyRequestPairing(
        reply = replyState,
        onSendKeyClicked = {
            val activity = context as? MainActivity
            replyState?.let { replyData ->
                coroutineScope.launch(Dispatchers.IO) {
                    activity?.sendReplyViaSocket(replyData, true)
                }
            }
            onNavigateBack()
        }
    )
}
