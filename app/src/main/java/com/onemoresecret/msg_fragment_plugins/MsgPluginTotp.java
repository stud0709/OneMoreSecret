package com.onemoresecret.msg_fragment_plugins;


import androidx.fragment.app.Fragment;

import com.onemoresecret.MessageFragment;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.TotpFragment;
import com.onemoresecret.crypto.OneTimePassword;

public class MsgPluginTotp extends MsgPluginEncryptedMessage {
    public MsgPluginTotp(MessageFragment messageFragment, byte[] messageData) {
        super(messageFragment, messageData);
        context.getMainExecutor().execute(() -> {
            var message = new String(messageData);
            var totpFragment = ((TotpFragment) messageView);
            totpFragment.init(new OneTimePassword(message), messageView, code -> {
                ((OutputFragment) outputView).setMessage(code, context.getString(R.string.one_time_password));
                totpFragment.setTotpText(Boolean.TRUE.equals(messageFragment.getHiddenState().getValue()) ? "●".repeat(code.length()) : code);
            });

            //observe hidden state
            messageFragment.getHiddenState().observe(messageView, hidden -> totpFragment.refresh());
        });
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new TotpFragment();
        return messageView;
    }
}
