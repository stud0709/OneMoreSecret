package com.onemoresecret.msg_fragment_plugins

import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import com.onemoresecret.CryptoCurrencyAddressFragment
import com.onemoresecret.MessageFragment
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import com.onemoresecret.crypto.BTCAddress.toKeyPair
import com.onemoresecret.crypto.BTCAddress.toPrivateKey

class MsgPluginCryptoCurrencyAddress(
    messageFragment: MessageFragment,
    wif: ByteArray
) : MessageFragmentPlugin(messageFragment) {
    private val keyPair =
        toKeyPair(toPrivateKey(wif)).toBTCKeyPair()

    override fun showBiometricPromptForDecryption() {
        //nothing to decrypt
    }

    override fun getMessageView(): Fragment {
        if (messageView == null) {
            messageView = CryptoCurrencyAddressFragment()
            context.mainExecutor.execute {
                val cryptoCurrencyAddressFragmentFragment =
                    messageView as CryptoCurrencyAddressFragment
                cryptoCurrencyAddressFragmentFragment.setValue(keyPair.btcAddressBase58)
                messageFragment.hiddenState.observe(
                    messageView as CryptoCurrencyAddressFragment
                ) { hidden: Boolean ->
                    if (hidden) {
                        (outputView as OutputFragment)
                            .setMessage(
                                keyPair.btcAddressBase58,
                                context.getString(R.string.public_address)
                            )
                    } else {
                        (outputView as OutputFragment)
                            .setMessage(
                                keyPair.wifBase58,
                                context.getString(R.string.private_key_wif)
                            )
                    }
                }
            }
        }
        return messageView as Fragment
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        //no authentication
    }
}
