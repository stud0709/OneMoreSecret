package com.onemoresecret.msg_fragment_plugins;

import androidx.fragment.app.Fragment;

import com.onemoresecret.MessageFragment;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.WiFiConnectionUpdateFragment;
import com.onemoresecret.crypto.Base58;

import java.security.KeyStoreException;

public class MsgPluginConnectionUpdate extends MessageFragmentPlugin {
    private static String TAG = MsgPluginConnectionUpdate.class.getSimpleName();

    public MsgPluginConnectionUpdate(MessageFragment messageFragment, byte[] message) {
        super(messageFragment);
        var responseCode = Base58.encode(message);
        context.getMainExecutor().execute(() -> {
            ((WiFiConnectionUpdateFragment) getMessageView()).setResponseCode(responseCode);
            ((OutputFragment) getOutputView()).setMessage(responseCode, "Port number");
        });
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new WiFiConnectionUpdateFragment();
        return messageView;
    }

    @Override
    public void showBiometricPromptForDecryption() throws KeyStoreException {
        //not used
    }
}
