package com.onemoresecret;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.onemoresecret.databinding.FragmentPinSetupBinding;

public class PinSetupFragment extends Fragment {
    private FragmentPinSetupBinding binding;
    private SharedPreferences preferences;
    public static final String PROP_PIN_ENABLED = "pin_enabled",
            PROP_PIN_VALUE = "pin_value",
            PROP_PANIC_PIN = "pin_panic",
            PROP_MAX_ATTEMPTS = "pin_max_attempts",
            PROP_REQUEST_INTERVAL_MINUTES = "pin_request_interval_minutes",
            PROP_REMAINING_ATTEMPTS = "pin_remaining_attempts";

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

        boolean pinEnabled = preferences.getBoolean(PROP_PIN_ENABLED, false);
        binding.chkEnablePin.setChecked(pinEnabled);
        setControls(pinEnabled);
        binding.chkEnablePin.setOnCheckedChangeListener((buttonView, isChecked) -> setControls(isChecked));
        binding.btnSavePinSettings.setOnClickListener(v -> onSave());
        binding.editTextPin.addTextChangedListener(textWatcherPin);
        binding.editTextRepeatPin.addTextChangedListener(textWatcherPin);
        binding.editTextPanicPin.addTextChangedListener(textWatcherPanicPin);
        binding.editTextRepeatPanicPin.addTextChangedListener(textWatcherPanicPin);

        //restoring values
        binding.editTextPin.setText(preferences.getString(PROP_PIN_VALUE, ""));
        binding.editTextRepeatPin.setText(preferences.getString(PROP_PIN_VALUE, ""));

        binding.editTextPanicPin.setText(preferences.getString(PROP_PANIC_PIN, ""));
        binding.editTextRepeatPanicPin.setText(preferences.getString(PROP_PANIC_PIN, ""));

        int maxAttempts = preferences.getInt(PROP_MAX_ATTEMPTS, 0);
        if (maxAttempts > 0)
            binding.editTextFailedAttempts.setText(Integer.toString(maxAttempts));

        long requestInterval = preferences.getLong(PROP_REQUEST_INTERVAL_MINUTES, 0);
        if (requestInterval > 0)
            binding.editTextRequestInterval.setText(Long.toString(requestInterval));
    }

    private void onSave() {
        var editor = preferences.edit();

        editor.putBoolean(PROP_PIN_ENABLED, binding.chkEnablePin.isChecked());

        if (binding.chkEnablePin.isChecked()) {
            editor.putString(PROP_PIN_VALUE, binding.editTextPin.getText().toString());

            if (binding.editTextRepeatPanicPin.getText().toString().isEmpty()) {
                editor.remove(PROP_PANIC_PIN);
            } else {
                editor.putString(PROP_PANIC_PIN, binding.editTextPanicPin.getText().toString());
            }

            int maxAttempts = binding.editTextFailedAttempts.getText().toString().isEmpty() ? 0 : Integer.parseInt(binding.editTextFailedAttempts.getText().toString());
            if (maxAttempts > 0) {
                editor.putInt(PROP_MAX_ATTEMPTS, maxAttempts);
                editor.putInt(PROP_REMAINING_ATTEMPTS, maxAttempts);
            } else {
                editor.remove(PROP_MAX_ATTEMPTS);
                editor.remove(PROP_REMAINING_ATTEMPTS);
            }

            int request_interval = binding.editTextRequestInterval.getText().toString().isEmpty() ? 0 : Integer.parseInt(binding.editTextRequestInterval.getText().toString());
            if (request_interval > 0) {
                editor.putLong(PROP_REQUEST_INTERVAL_MINUTES, request_interval);
            } else {
                editor.remove(PROP_REQUEST_INTERVAL_MINUTES);
            }
        } else {
            editor.remove(PROP_PIN_VALUE)
                    .remove(PROP_PANIC_PIN)
                    .remove(PROP_MAX_ATTEMPTS)
                    .remove(PROP_REQUEST_INTERVAL_MINUTES);
        }

        editor.apply();

        requireContext().getMainExecutor().execute(() -> {
            Toast.makeText(getContext(), R.string.pin_preferences_saved, Toast.LENGTH_SHORT).show();
            NavHostFragment.findNavController(PinSetupFragment.this).popBackStack();
        });
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

    /**
     * Check if the form data is valid and it is OK to save it
     */
    private void validateForm() {
        binding.btnSavePinSettings.setEnabled(!binding.chkEnablePin.isChecked() || (isPinValid() && isPanicPinValid()));
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

            binding.imgViewPanicMatch.setImageDrawable(drawable);
            validateForm();
        }
    };

    private boolean isPinValid() {
        return !binding.editTextPin.getText().toString().isEmpty() &&
                binding.editTextPin.getText().toString()
                        .equals(binding.editTextRepeatPin.getText().toString());
    }

    private boolean isPanicPinValid() {
        boolean b = binding.editTextPanicPin.getText().toString()
                .equals(binding.editTextRepeatPanicPin.getText().toString());

        if (b && !binding.editTextPin.getText().toString().isEmpty() &&
                binding.editTextPanicPin.getText().toString().equals(binding.editTextPin.getText().toString())) {
            Toast.makeText(requireContext(), R.string.panic_pin_should_not_match_pin, Toast.LENGTH_LONG).show();
            b = false;
        }

        return b;
    }
}