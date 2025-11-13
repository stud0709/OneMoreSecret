package com.onemoresecret.msg_fragment_plugins;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;

import com.onemoresecret.CryptoCurrencyAddressFragment;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.crypto.BTCAddress;


public class MsgPluginCryptoCurrencyAddress extends MessageFragmentPlugin {
    private final BTCAddress.BTCKeyPair keyPair;

    public MsgPluginCryptoCurrencyAddress(MessageFragment messageFragment,
                                          byte[] wif) throws Exception {
        super(messageFragment);
        keyPair = BTCAddress.toKeyPair(BTCAddress.toPrivateKey(wif)).toBTCKeyPair();
    }

    @Override
    public void showBiometricPromptForDecryption() {
        //nothing to decrypt
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) {
            messageView = new CryptoCurrencyAddressFragment();
            context.getMainExecutor().execute(() -> {
                var cryptoCurrencyAddressFragmentFragment = (CryptoCurrencyAddressFragment) messageView;
                cryptoCurrencyAddressFragmentFragment.setValue(keyPair.btcAddressBase58());

                messageFragment.getHiddenState().observe(messageView, hidden -> {
                    if (hidden) {
                        ((OutputFragment) outputView)
                                .setMessage(
                                        keyPair.btcAddressBase58(),
                                        context.getString(R.string.public_address));
                    } else {
                        ((OutputFragment) outputView)
                                .setMessage(
                                        keyPair.wifBase58(),
                                        context.getString(R.string.private_key_wif));
                    }
                });
            });
        }
        return messageView;
    }

    @Override
    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
        //no authentication
    }
}
