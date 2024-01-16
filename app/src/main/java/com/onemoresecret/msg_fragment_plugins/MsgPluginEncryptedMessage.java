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
    public MsgPluginEncryptedMessage(MessageFragment messageFragment,
                                     byte[] messageData) throws IOException {

        super(messageFragment, messageData);
    }

    @Override
    protected void init(byte[] messageData) throws IOException {
        context.getMainExecutor().execute(() -> {
            var message = new String(messageData);
            var hiddenTextFragment = (HiddenTextFragment) messageView;
            messageFragment.getHiddenState().observe(hiddenTextFragment, hidden -> hiddenTextFragment.setText(hidden ? context.getString(R.string.hidden_text) : message));
            ((OutputFragment) outputView).setMessage(message, context.getString(R.string.decrypted_message));
        });
    }

    @Override
    public void showBiometricPromptForDecryption() throws KeyStoreException {
        //nothing to decrypt
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new HiddenTextFragment();
        return messageView;
    }

    @Override
    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
        //no authentication
    }
}
