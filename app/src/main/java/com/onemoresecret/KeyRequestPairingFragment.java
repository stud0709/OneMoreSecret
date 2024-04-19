package com.onemoresecret;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentKeyRequestPairingBinding;
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause;


public class KeyRequestPairingFragment extends FragmentWithNotificationBeforePause {
    private static final String TAG = KeyRequestPairingFragment.class.getSimpleName();

    private FragmentKeyRequestPairingBinding binding;
    private byte[] reply;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentKeyRequestPairingBinding.inflate(
                inflater,
                container,
                false);

        return binding.getRoot();
    }

    private final View.OnClickListener btnListener = btn -> {
        if (beforePause != null) beforePause.run();

        var activity = (MainActivity) requireActivity();
        new Thread(() -> activity.sendReplyViaSocket(reply, true)).start();

        Util.discardBackStack(this);
    };

    @Override
    public void setBeforePause(Runnable r) {
        //not necessary
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnSendKey.setOnClickListener(btnListener);
        binding.btnSendKey.setEnabled(false);
    }

    public void setReply(byte[] reply) {
        this.reply = reply;
        requireActivity().getMainExecutor().execute(() -> binding.btnSendKey.setEnabled(true));
    }
}