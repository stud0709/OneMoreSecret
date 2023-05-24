package com.onemoresecret;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.onemoresecret.crypto.OneTimePassword;
import com.onemoresecret.databinding.FragmentHiddenTextBinding;
import com.onemoresecret.databinding.FragmentTotpBinding;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HiddenTextFragment extends Fragment {
    private FragmentHiddenTextBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentHiddenTextBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void setText(String text) {
        binding.textViewMessage.setText(text);
    }
}