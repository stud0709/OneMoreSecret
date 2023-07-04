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
import com.onemoresecret.databinding.FragmentTotpBinding;

import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class TotpFragment extends Fragment {
    private static final String TAG = TotpFragment.class.getSimpleName();
    private FragmentTotpBinding binding;
    private final Timer timer = new Timer();
    private long lastState = -1L;
    private OneTimePassword otp;
    private Function<Integer, String> maskFx;
    private Consumer<String> onNewValue;
    private boolean runOnce = true;

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

    public void init(OneTimePassword otp, Function<Integer, String> maskFx, Consumer<String> onNewValue) {
        this.otp = otp;
        this.maskFx = maskFx;
        this.onNewValue = onNewValue;
    }

    private TimerTask getTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                if (generateResponseCode()) {
                    timer.schedule(getTimerTask(), 1000);
                }
            }
        };
    }

    public void refresh() {
        generateResponseCode();
    }

    private boolean generateResponseCode() {
        if (otp == null) return true; //otp not set yet

        if (runOnce) {
            runOnce = false;

            var name = otp.getName();
            var issuer = otp.getIssuer();

            requireActivity().getMainExecutor().execute(() -> {
                if (issuer == null || issuer.isEmpty()) {
                    binding.textViewNameIssuer.setText(name);
                } else {
                    binding.textViewNameIssuer.setText(String.format("%s by %s", name, issuer));
                }
            });
        }

        var mask = maskFx.apply(otp.getDigits());

        try {
            long[] state = otp.getState();
            var code = otp.generateResponseCode(state[0]);

            requireActivity().getMainExecutor().execute(() -> {
                if (binding == null) return; //fragment has been destroyed
                binding.textViewRemaining.setText(String.format("...%ss", otp.getPeriod() - state[1]));
                binding.textViewTotpValue.setText(mask == null ? code : mask);
            });

            if (lastState != state[0]) {
                //new State = new code; update output fragment
                onNewValue.accept(code);
                lastState = state[0];
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