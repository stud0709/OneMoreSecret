package com.onemoresecret.msg_fragment_plugins

import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.R
import com.onemoresecret.composable.HiddenTextScreen

import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import kotlinx.coroutines.flow.MutableStateFlow

open class MsgPluginEncryptedMessage(
    activity: FragmentActivity,
    protected val messageData: ByteArray?,
    hiddenState: MutableStateFlow<Boolean>,
    onNavigateBack: () -> Unit
) : MessageFragmentPlugin(activity, hiddenState, onNavigateBack) {

    private val message = String(messageData!!)
    private val outputViewModel = OutputViewModel(preferences)

    init {
        outputViewModel.setMessage(message, context.getString(R.string.decrypted_message))
    }

    override fun showBiometricPromptForDecryption() {
        //nothing to decrypt
    }

    @Composable
    override fun TopBarActions() {

    }

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        HiddenTextScreen(text = if (hiddenState) context.getString(R.string.hidden_text) else message)
    }

    @Composable
    override fun OutputView() {
        OutputScreen(outputViewModel = outputViewModel)
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        //no authentication
    }
}
