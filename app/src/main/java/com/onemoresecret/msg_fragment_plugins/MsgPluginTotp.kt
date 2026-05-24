package com.onemoresecret.msg_fragment_plugins

import androidx.fragment.app.Fragment
import com.onemoresecret.MessageFragment
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import com.onemoresecret.TotpFragment
import com.onemoresecret.crypto.OneTimePassword

class MsgPluginTotp(
    messageFragment: MessageFragment,
    messageData: ByteArray
) : MsgPluginEncryptedMessage(messageFragment, messageData) {

    init {
        context.mainExecutor.execute {
            val message = String(messageData)
            val totpFragment = msgView as TotpFragment
            totpFragment.init(OneTimePassword(message), msgView!!) { code ->
                (outView as OutputFragment).setMessage(code, context.getString(R.string.one_time_password))
                totpFragment.setTotpText(if (messageFragment.hiddenState.value == true) {
                    "●".repeat(code.length)
                } else {
                    code
                })
            }

            // observe hidden state
            messageFragment.hiddenState.observe(msgView!!) {
                totpFragment.refresh()
            }
        }
    }

    override fun getMessageView(): Fragment {
        if (msgView == null) msgView = TotpFragment()
        return msgView!!
    }
}
