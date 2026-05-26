package com.onemoresecret.msg_fragment_plugins

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.R
import kotlinx.coroutines.flow.MutableStateFlow

class MsgPluginCryptoCurrencyAddress(
    activity: FragmentActivity,
    messageData: ByteArray,
    hiddenState: MutableStateFlow<Boolean>,
    onNavigateBack: () -> Unit
) : MessageFragmentPlugin(activity, hiddenState, onNavigateBack) {

    private val address = String(messageData)

    override fun showBiometricPromptForDecryption() {
        // nothing
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
    }

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.public_address),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = if (hiddenState) "●".repeat(address.length) else address.chunked(4).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
