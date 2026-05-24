package com.onemoresecret.msg_fragment_plugins

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.onemoresecret.HiddenTextFragment
import com.onemoresecret.MessageFragment
import com.onemoresecret.OutputFragment
import com.onemoresecret.R

open class MsgPluginEncryptedMessage(
    messageFragment: MessageFragment,
    private val messageData: ByteArray?
) : MessageFragmentPlugin(messageFragment) {
    override fun showBiometricPromptForDecryption() {
        //nothing to decrypt
    }

    override fun getMessageView(): Fragment {
        if (msgView == null) {
            msgView = HiddenTextFragment()
            context.mainExecutor.execute(Runnable {
                val message = String(messageData!!)
                val hiddenTextFragment = msgView as HiddenTextFragment?
                messageFragment.hiddenState.observe(
                    msgView!!,
                    Observer { hidden: Boolean ->
                        hiddenTextFragment?.text = if (hidden) context.getString(
                                R.string.hidden_text
                            ) else message
                    })
                (outView as OutputFragment).setMessage(
                    message,
                    context.getString(R.string.decrypted_message)
                )
            })
        }
        return msgView!!
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        //no authentication
    }
}
