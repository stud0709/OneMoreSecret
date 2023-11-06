package com.onemoresecret.msg_fragment_plugins;

import android.database.Observable;
import android.os.Handler;

import androidx.fragment.app.Fragment;

import com.onemoresecret.MessageFragment;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.TotpFragment;
import com.onemoresecret.crypto.OneTimePassword;
import com.onemoresecret.databinding.FragmentMessageBinding;

import java.io.IOException;

public class MsgPluginTotp extends MsgPluginEncryptedMessage {
    public MsgPluginTotp(MessageFragment messageFragment, byte[] messageData) throws IOException {
        super(messageFragment,  messageData);
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new TotpFragment();
        return messageView;
    }

    @Override
    protected void afterDecrypt(byte[] bArr) {
        var message = new String(bArr);
        var totpFragment = ((TotpFragment) messageView);
        totpFragment.init(new OneTimePassword(message), messageView, code -> {
            ((OutputFragment) outputView).setMessage(code, context.getString(R.string.one_time_password));
            totpFragment.setTotpText(messageFragment.getHiddenState().getValue() ? "●".repeat(code.length()) : code);
        });

        //observe hidden state
        messageFragment.getHiddenState().observe(messageView, hidden -> totpFragment.refresh());
    }
}
