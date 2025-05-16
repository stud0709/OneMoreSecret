package com.onemoresecret.msg_fragment_plugins

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import com.onemoresecret.HiddenTextFragment
import com.onemoresecret.MessageFragment
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import java.security.KeyStoreException

open class MsgPluginEncryptedMessage(
    messageFragment: MessageFragment,
    private val messageData: ByteArray
) : MessageFragmentPlugin(messageFragment) {
    @Throws(KeyStoreException::class)
    override fun showBiometricPromptForDecryption() {
        //nothing to decrypt
    }

    override fun getMessageView(): Fragment {
        if (messageView == null) {
            messageView = HiddenTextFragment()
            context.mainExecutor.execute {
                val message = String(messageData)
                val hiddenTextFragment = messageView as HiddenTextFragment
                messageFragment.hiddenState.observe(
                    messageView as HiddenTextFragment
                ) { hidden: Boolean ->
                    hiddenTextFragment.setText(
                        if (hidden) context.getString(
                            R.string.hidden_text
                        ) else message
                    )
                }
                (outputView as OutputFragment).setMessage(
                    message,
                    context.getString(R.string.decrypted_message)
                )
            }
        }
        return messageView as Fragment
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        //no authentication
    }
}
