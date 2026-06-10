package com.onemoresecret.msg_fragment_plugins

import android.net.Uri
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.Util
import com.onemoresecret.composable.FileInfoScreen
import com.onemoresecret.composable.FileOutputScreen

class MsgPluginEncryptedFile(
    activity: FragmentActivity,
    val uri: Uri,
    onNavigateBack: () -> Unit
) : MessageFragmentPlugin(activity, onNavigateBack) {

    private val fileInfo = Util.getFileInfo(activity, uri)
    
    init {
        // Assume file info parsed from URI
    }

    override fun showBiometricPromptForDecryption() {
        // biometric prompt for file
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        // decrypt file
    }

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        FileInfoScreen(fileInfo = fileInfo)
    }

    @Composable
    override fun OutputView() {
        FileOutputScreen(uri = uri, progressText = "")
    }
}
