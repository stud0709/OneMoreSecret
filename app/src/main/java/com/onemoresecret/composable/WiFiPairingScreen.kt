package com.onemoresecret.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun WiFiPairingScreen(
    requestId: String,
    responseCode: String,
    onStartClick: (() -> Unit)?
) {
    var isPairingAccepted by remember { mutableStateOf(false) }

    WiFiPairing(
        requestId = requestId,
        responseCode = responseCode,
        isPairingAccepted = isPairingAccepted,
        onConfirm = {
            isPairingAccepted = true
            onStartClick?.invoke()
        }
    )
}
