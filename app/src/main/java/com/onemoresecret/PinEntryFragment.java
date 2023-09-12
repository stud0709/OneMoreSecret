package com.onemoresecret;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentPinEntryBinding;
import com.onemoresecret.databinding.FragmentQrBinding;

import org.w3c.dom.Text;

import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class PinEntryFragment extends DialogFragment {
    private FragmentPinEntryBinding binding;
    private SharedPreferences preferences;
    private final Runnable runOnSuccess;
    private Runnable runOnDismiss;

    public PinEntryFragment(Runnable runOnSuccess, Runnable runOnDismiss) {
        this.runOnSuccess = runOnSuccess;
        this.runOnDismiss = runOnDismiss;
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentPinEntryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        binding.textViewPin.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                binding.imageButtonUnlock.setEnabled(binding.textViewPin.getText().length() > 0);
            }
        });

        binding.textViewPin.setText("");

        //initialize keys with random numbers 0...9
        var bArr = new Button[]{
                binding.btn0,
                binding.btn1,
                binding.btn2,
                binding.btn3,
                binding.btn4,
                binding.btn5,
                binding.btn6,
                binding.btn7,
                binding.btn8,
                binding.btn9};

        var bList = new ArrayList<>(Arrays.asList(bArr));

        var rnd = new Random();

        for (int i = 0; i < bArr.length; i++) {
            bList.remove(rnd.nextInt(bList.size())).setText(Integer.toString(i));
        }

        //set listeners
        for (var btn : bArr) {
            btn.setOnClickListener(v -> onDigit(v));
        }

        binding.imageButtonDel.setOnClickListener(v -> {
            var cs = binding.textViewPin.getText();
            if (cs.length() == 0) return;

            binding.textViewPin.setText(cs.subSequence(0, cs.length() - 1));
        });

        binding.imageButtonUnlock.setOnClickListener(v -> tryUnlock());
    }

    private void tryUnlock() {
        boolean panicPinEntered = binding.textViewPin.getText().toString()
                .equals(preferences.getString(PinSetupFragment.PROP_PANIC_PIN, null));

        if (panicPinEntered) {
            //panic pin
            panic();
        }

        if (panicPinEntered ||
                binding.textViewPin.getText().toString()
                        .equals(preferences.getString(PinSetupFragment.PROP_PIN_VALUE, null))) {
            //pin entered correctly
            Toast.makeText(requireContext(), R.string.pin_accepted, Toast.LENGTH_SHORT).show();
            requireContext().getMainExecutor().execute(runOnSuccess);
            runOnDismiss = null;
            dismiss();
        } else {
            //wrong pin
            int remainingAttempts = preferences.getInt(PinSetupFragment.PROP_REMAINING_ATTEMPTS, Integer.MAX_VALUE);
            remainingAttempts--;

            preferences.edit().putInt(PinSetupFragment.PROP_REMAINING_ATTEMPTS, remainingAttempts).apply();

            if (remainingAttempts <= 0) panic();

            Toast.makeText(requireContext(), R.string.wrong_pin, Toast.LENGTH_LONG).show();
            binding.textViewPin.setText("");
        }
    }

    private void panic() {
        var cryptographyManager = new CryptographyManager();

        try {
            var aliasesEnum = cryptographyManager.keyStore.aliases();

            while (aliasesEnum.hasMoreElements()) {
                cryptographyManager.deleteKey(aliasesEnum.nextElement());
            }
        } catch (KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    private void onDigit(View v) {
        binding.textViewPin.append(((Button) v).getText());
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (this.runOnDismiss != null) {
            requireContext().getMainExecutor().execute(runOnDismiss);
        }
    }
}