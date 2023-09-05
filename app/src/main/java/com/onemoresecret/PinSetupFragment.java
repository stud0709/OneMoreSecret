package com.onemoresecret;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentPinSetupBinding;
import com.onemoresecret.databinding.FragmentQrBinding;

public class PinSetupFragment extends Fragment {
    private FragmentPinSetupBinding binding;
    private SharedPreferences preferences;
    private static final String prop_pin_enabled = "pin_enabled";

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentPinSetupBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        boolean pinEnabled = preferences.getBoolean(prop_pin_enabled, false);
        binding.chkEnablePin.setChecked(pinEnabled);
        setControls(pinEnabled);
        binding.chkEnablePin.setOnCheckedChangeListener((buttonView, isChecked) -> setControls(isChecked));
        binding.btnSavePinSettings.setOnClickListener(v -> onSave());
        binding.editTextPin.addTextChangedListener(textWatcherPin);
        binding.editTextRepeatPin.addTextChangedListener(textWatcherPin);
        binding.editTextPanicPin.addTextChangedListener(textWatcherPanicPin);
        binding.editTextRepeatPanicPin.addTextChangedListener(textWatcherPanicPin);
    }

    private void onSave() {
        //todo
    }

    private void setControls(boolean isChecked) {
        validateForm();
        binding.editTextFailedAttempts.setEnabled(isChecked);
        binding.editTextPin.setEnabled(isChecked);
        binding.editTextPanicPin.setEnabled(isChecked);
        binding.editTextRepeatPin.setEnabled(isChecked);
        binding.editTextRequestInterval.setEnabled(isChecked);
        binding.editTextRepeatPanicPin.setEnabled(isChecked);
        binding.imgViewPinMatch.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
        binding.imgViewPanicMatch.setVisibility(isChecked ? View.VISIBLE : View.INVISIBLE);
    }

    /** Check if the form data is valid and it is OK to save it
     *
     */
    private void validateForm() {
        boolean b = binding.chkEnablePin.isChecked();
        if (b) {
            b &= isPinValid();
            b &= isPanicPinValid();
        }

        binding.btnSavePinSettings.setEnabled(b);
    }

    private final TextWatcher textWatcherPin = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            var drawable = ResourcesCompat.getDrawable(getResources(), isPinValid() ?
                            R.drawable.baseline_check_circle_24 :
                            R.drawable.baseline_cancel_24,
                    getContext().getTheme());

            binding.imgViewPinMatch.setImageDrawable(drawable);
            validateForm();
        }
    };

    private final TextWatcher textWatcherPanicPin = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            var drawable = ResourcesCompat.getDrawable(getResources(), isPanicPinValid() ?
                            R.drawable.baseline_check_circle_24 :
                            R.drawable.baseline_cancel_24,
                    getContext().getTheme());

            binding.imgViewPinMatch.setImageDrawable(drawable);
            validateForm();
        }
    };

    private boolean isPinValid() {
        return binding.editTextPin.getText().toString().isEmpty() ||
                binding.editTextPin.getText().toString()
                        .equals(binding.editTextRepeatPin.getText().toString());
    }

    private boolean isPanicPinValid() {
        return binding.editTextPanicPin.getText().toString()
                .equals(binding.editTextRepeatPanicPin.getText().toString());
    }
}