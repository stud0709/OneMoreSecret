package com.onemoresecret.msg_fragment_plugins;

import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.Fragment;

import com.onemoresecret.HiddenTextFragment;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;

import java.io.IOException;
import java.security.KeyStoreException;


public class MsgPluginEncryptedMessage extends MessageFragmentPlugin<byte[]> {
    private byte[] messageData;

    public MsgPluginEncryptedMessage(MessageFragment messageFragment,
                                     byte[] messageData, int applicationId) throws Exception {

        super(messageFragment, messageData, applicationId);
    }

    @Override
    protected void init(byte[] messageData, int applicationId) throws IOException {
        this.messageData = messageData;
    }

    @Override
    public void showBiometricPromptForDecryption() throws KeyStoreException {
        //nothing to decrypt
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) {
            messageView = new HiddenTextFragment();
            context.getMainExecutor().execute(() -> {
                var message = new String(messageData);
                var hiddenTextFragment = (HiddenTextFragment) messageView;
                messageFragment.getHiddenState().observe(messageView,
                        hidden -> hiddenTextFragment.setText(hidden ? context.getString(R.string.hidden_text) : message));
                ((OutputFragment) outputView).setMessage(message, context.getString(R.string.decrypted_message));
            });
        }
        return messageView;
    }

    @Override
    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        //no authentication
    }
}
