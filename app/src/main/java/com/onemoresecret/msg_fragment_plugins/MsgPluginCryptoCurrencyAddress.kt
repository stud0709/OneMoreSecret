package com.onemoresecret.msg_fragment_plugins

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import com.onemoresecret.CryptoCurrencyAddressFragment
import com.onemoresecret.MessageFragment
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import com.onemoresecret.crypto.BTCAddress

class MsgPluginCryptoCurrencyAddress(
    messageFragment: MessageFragment,
    wif: ByteArray
) : MessageFragmentPlugin(messageFragment) {

    private val keyPair: BTCAddress.BTCKeyPair = BTCAddress.toKeyPair(BTCAddress.toPrivateKey(wif)).toBTCKeyPair()

    override fun showBiometricPromptForDecryption() {
        // nothing to decrypt
    }

    override fun getMessageView(): Fragment {
        if (msgView == null) {
            msgView = CryptoCurrencyAddressFragment()
            context.mainExecutor.execute {
                val fragment = msgView as CryptoCurrencyAddressFragment
                fragment.setValue(keyPair.btcAddressBase58)

                messageFragment.hiddenState.observe(msgView!!) { hidden ->
                    if (hidden == true) {
                        (outView as OutputFragment).setMessage(
                            keyPair.btcAddressBase58,
                            context.getString(R.string.public_address)
                        )
                    } else {
                        (outView as OutputFragment).setMessage(
                            keyPair.wifBase58,
                            context.getString(R.string.private_key_wif)
                        )
                    }
                }
            }
        }
        return msgView!!
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        // no authentication
    }
}
