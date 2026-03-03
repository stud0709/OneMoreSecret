package com.onemoresecret.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.onemoresecret.R

@Composable
fun WiFiPairing(
    requestId: String,
    responseCode: String,
    isPairingAccepted: Boolean,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = stringResource(id = R.string.pairing_info_request),
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = requestId,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Button(
            onClick = onConfirm,
            enabled = !isPairingAccepted,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(
                    id = if (isPairingAccepted) R.string.pairing_accepted else R.string.accept_pairing
                )
            )
        }

        if (isPairingAccepted) {
            Text(
                text = stringResource(id = R.string.pairing_info_response),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )

            Text(
                text = responseCode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
