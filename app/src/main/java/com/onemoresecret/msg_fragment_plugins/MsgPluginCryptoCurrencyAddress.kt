package com.onemoresecret.msg_fragment_plugins

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.R
import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import com.onemoresecret.crypto.BTCAddress
import kotlinx.coroutines.flow.MutableStateFlow

class MsgPluginCryptoCurrencyAddress(
    activity: FragmentActivity,
    messageData: ByteArray,
    hiddenState: MutableStateFlow<Boolean>,
    onNavigateBack: () -> Unit
) : MessageFragmentPlugin(activity, hiddenState, onNavigateBack) {

    private val keyPair = BTCAddress.toKeyPair(BTCAddress.toPrivateKey(messageData)).toBTCKeyPair()
    private val outputViewModel = OutputViewModel(preferences)

    override fun showBiometricPromptForDecryption() {
        // nothing
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
    }

    @Composable
    override fun OutputView() {
        OutputScreen(outputViewModel = outputViewModel)
    }

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        val publicAddressTitle = activity.getString(R.string.public_address)
        val privateKeyTitle = activity.getString(R.string.private_key_wif)
        val address = keyPair.btcAddressBase58
        val privateKeyWif = keyPair.wifBase58
        val hiddenText = activity.getString(R.string.hidden_text)

        LaunchedEffect(hiddenState) {
            if (hiddenState) {
                outputViewModel.setMessage(address, publicAddressTitle)
            } else {
                outputViewModel.setMessage(privateKeyWif, privateKeyTitle)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.public_address)
            )
            Text(
                text = if (hiddenState) hiddenText else {
                    if (address.length > 2) address.substring(0, 2) + " " + address.substring(2).chunked(4).joinToString(" ") else address
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
