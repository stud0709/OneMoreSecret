package com.onemoresecret;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;

import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.bt.layout.KeyboardLayout;
import com.onemoresecret.bt.layout.Stroke;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.OneTimePassword;
import com.onemoresecret.databinding.FragmentOutputBinding;
import com.onemoresecret.databinding.FragmentTotpBinding;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TotpFragment extends Fragment {
    private static final String TAG = TotpFragment.class.getSimpleName();
    private FragmentTotpBinding binding;
    private final Timer timer = new Timer();
    private long lastState = -1L;
    private OneTimePassword otp;
    private Supplier<String> maskSupplier;
    private Consumer<String> onNewValue;
    private boolean runOnce = true;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentTotpBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getTimerTask().run();
    }

    public void init(OneTimePassword otp, Supplier<String> maskSupplier, Consumer<String> onNewValue) {
        this.otp = otp;
        this.maskSupplier = maskSupplier;
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

            String name = otp.getName();
            String issuer = otp.getIssuer();

            requireActivity().getMainExecutor().execute(() -> {
                if (issuer == null || issuer.isEmpty()) {
                    binding.textViewNameIssuer.setText(name);
                } else {
                    binding.textViewNameIssuer.setText(String.format("%s by %s", name, issuer));
                }
            });
        }

        String mask = maskSupplier.get();

        try {
            long[] state = otp.getState();
            String code = otp.generateResponseCode(state[0]);

            requireActivity().getMainExecutor().execute(() -> {
                if (mask != null) {
                    binding.textViewRemaining.setText("");
                    binding.textViewTotpValue.setText(mask);
                } else {
                    binding.textViewRemaining.setText(String.format("...%ss", otp.getPeriod() - state[1]));
                    binding.textViewTotpValue.setText(code);
                }
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
    }
}