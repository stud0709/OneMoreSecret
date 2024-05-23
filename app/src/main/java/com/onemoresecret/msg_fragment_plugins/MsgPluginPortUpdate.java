package com.onemoresecret.msg_fragment_plugins;

import androidx.fragment.app.Fragment;

import com.onemoresecret.MessageFragment;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.WiFiPortUpdateFragment;
import com.onemoresecret.crypto.Base58;

import java.nio.ByteBuffer;
import java.security.KeyStoreException;

public class MsgPluginPortUpdate extends MessageFragmentPlugin {
    private static String TAG = MsgPluginPortUpdate.class.getSimpleName();

    public MsgPluginPortUpdate(MessageFragment messageFragment, byte[] message) {
        super(messageFragment);
        var portNr = Base58.encode(message);
        context.getMainExecutor().execute(() -> {
            ((WiFiPortUpdateFragment) getMessageView()).setPort(portNr);
            ((OutputFragment) getOutputView()).setMessage(portNr, "Port number");
        });
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new WiFiPortUpdateFragment();
        return messageView;
    }

    @Override
    public void showBiometricPromptForDecryption() throws KeyStoreException {
        //not used
    }
}
