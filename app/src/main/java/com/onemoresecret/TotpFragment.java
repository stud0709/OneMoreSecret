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
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.onemoresecret.crypto.OneTimePassword;
import com.onemoresecret.databinding.FragmentTotpBinding;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Function;

public class TotpFragment extends Fragment {
    private static final String TAG = TotpFragment.class.getSimpleName();
    private FragmentTotpBinding binding;
    private final Timer timer = new Timer();
    private long lastState = -1L;
    private OneTimePassword otp;
    private final MutableLiveData<String> code = new MutableLiveData<>();

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentTotpBinding.inflate(inflater, container, false);
        binding.textViewTotpValue.setText("");
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getTimerTask().run();
    }

    public void init(OneTimePassword otp,
                     LifecycleOwner owner,
                     Observer<String> observer) {
        this.otp = otp;
        code.observe(owner, observer);
        var name = otp.getName();
        var issuer = otp.getIssuer();

        requireActivity().getMainExecutor().execute(() -> {
            if (issuer == null || issuer.isEmpty()) {
                binding.textViewNameIssuer.setText(name);
            } else {
                binding.textViewNameIssuer.setText(String.format(getString(R.string.totp_name_issuer), name, issuer));
            }
        });
    }

    private TimerTask getTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                if (generateResponseCode(false)) {
                    timer.schedule(getTimerTask(), 1000);
                }
            }
        };
    }

    public void setTotpText(String s) {
        requireActivity().getMainExecutor().execute(() -> {
            if (binding == null) return; //fragment has been destroyed
            binding.textViewTotpValue.setText(s);
        });
    }

    public void refresh() {
        generateResponseCode(true);
    }

    private boolean generateResponseCode(boolean force) {
        if (otp == null) return true; //otp not set (yet)

        try {
            var state = otp.getState();

            requireActivity().getMainExecutor().execute(() -> {
                if (binding == null) return; //fragment has been destroyed
                binding.textViewRemaining.setText(String.format("...%ss", otp.getPeriod() - state.secondsUntilNext()));
            });

            if (lastState != state.current() || force) {
                this.code.postValue(otp.generateResponseCode(state.current()));
                lastState = state.current();
            }
        } catch (Exception e) {
            Log.wtf(TAG, e);
            requireActivity().getMainExecutor().execute(() ->
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
            return false;
        }
        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timer.cancel();
        binding.textViewTotpValue.setText("");
        binding = null;
    }
}