package com.onemoresecret.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun KeyRequestPairing(
    reply: ByteArray?,
    onSendKeyClicked: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = onSendKeyClicked,
                // The button is enabled only if the reply data is present
                enabled = reply != null
            ) {
                Text(text = "Send Key")
            }
        }
    }
}