package com.onemoresecret.qr;

import android.content.Context;

import androidx.fragment.app.FragmentActivity;

import java.io.DataInputStream;
import java.util.function.Consumer;

public interface MessageProcessorApplication<T> {
    int APPLICATION_AES_ENCRYPTED_KEY_PAIR_TRANSFER = 0, APPLICATION_ENCRYPTED_MESSAGE_TRANSFER = 1;

    public void processData(String message,
                            Context ctx,
                            Consumer<T> onSuccess,
                            Consumer<Exception> onException);
}
