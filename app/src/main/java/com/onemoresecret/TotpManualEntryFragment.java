package com.onemoresecret;

import static com.onemoresecret.crypto.OneTimePassword.ALGORITHM;
import static com.onemoresecret.crypto.OneTimePassword.ALGORITHM_PARAM;
import static com.onemoresecret.crypto.OneTimePassword.DEFAULT_PERIOD;
import static com.onemoresecret.crypto.OneTimePassword.DIGITS;
import static com.onemoresecret.crypto.OneTimePassword.DIGITS_PARAM;
import static com.onemoresecret.crypto.OneTimePassword.OTP_SCHEME;
import static com.onemoresecret.crypto.OneTimePassword.PERIOD_PARAM;
import static com.onemoresecret.crypto.OneTimePassword.SECRET_PARAM;
import static com.onemoresecret.crypto.OneTimePassword.TOTP;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.SelectionTracker;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.OneTimePassword;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.crypto.TotpUriTransfer;
import com.onemoresecret.databinding.FragmentTotpManualEntryBinding;

import java.security.interfaces.RSAPublicKey;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TotpManualEntryFragment extends Fragment {
    private static final String TAG = TotpManualEntryFragment.class.getSimpleName();
    private FragmentTotpManualEntryBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private static final int PERIOD_MIN = 1, PERIOD_MAX = 120;
    private SharedPreferences preferences;
    private final Timer timer = new Timer();

    private final CryptographyManager cryptographyManager = new CryptographyManager();

    private String selectedAlias = null;
    private OneTimePassword otp = null;
    private long lastState = -1L;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentTotpManualEntryBinding.inflate(inflater, container, false);
        binding.chipPeriod.setText(String.format(getString(R.string.format_seconds), DEFAULT_PERIOD));
        binding.chipDigits.setText(DIGITS[0]);
        binding.chipAlgorithm.setText(ALGORITHM[0]);
        binding.textViewTotp.setText("");
        binding.textViewTimer.setText("");
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        keyStoreListFragment = binding.fragmentContainerView.getFragment();

        keyStoreListFragment.setRunOnStart(
                fragmentKeyStoreListBinding -> keyStoreListFragment
                        .getSelectionTracker()
                        .addObserver(new SelectionTracker.SelectionObserver<>() {
                            @Override
                            public void onSelectionChanged() {
                                super.onSelectionChanged();
                                if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                                    selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
                                } else {
                                    selectedAlias = null;
                                }
                                generateResult();
                            }
                        }));

        var textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                generateResult();
            }
        };

        binding.editTextLabel.addTextChangedListener(textWatcher);
        binding.editTextSecret.addTextChangedListener(textWatcher);
        binding.chipAlgorithm.setOnClickListener(e -> selectAlgorithm());
        binding.chipDigits.setOnClickListener(e -> selectDigits());
        binding.chipPeriod.setOnClickListener(e -> setPeriod());

        requireActivity().getMainExecutor().execute(() -> {
            generateResult();
            getTimerTask().run();
        });
    }

    private void setPeriod() {
        var builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Period, s");
        var numberPicker = new NumberPicker(requireContext());
        numberPicker.setMinValue(PERIOD_MIN);
        numberPicker.setMaxValue(PERIOD_MAX);
        numberPicker.setValue(DEFAULT_PERIOD);
        builder.setView(numberPicker);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            binding.chipPeriod.setText(String.format(getString(R.string.format_seconds), numberPicker.getValue()));
            generateResult();
        });
        builder.setNegativeButton(android.R.string.cancel, ((dialog, which) -> dialog.cancel()));
        builder.show();
    }

    private void selectDigits() {
        var builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Digits");
        builder.setItems(OneTimePassword.DIGITS, (dialog, which) -> requireContext().getMainExecutor().execute(() -> {
            binding.chipDigits.setText(OneTimePassword.DIGITS[which]);
            generateResult();
        }));

        builder.show();
    }

    private void selectAlgorithm() {
        var builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Algorithm");
        builder.setItems(OneTimePassword.ALGORITHM, (dialog, which) -> requireContext().getMainExecutor().execute(() -> {
            binding.chipAlgorithm.setText(OneTimePassword.ALGORITHM[which]);
            generateResult();
        }));

        builder.show();
    }

    private void generateResult() {
        var builder = new Uri.Builder();
        builder.scheme(OTP_SCHEME)
                .authority(TOTP)
                .appendPath(binding.editTextLabel.getText().toString());

        var param = binding.editTextSecret.getText().toString();
        builder.appendQueryParameter(SECRET_PARAM, param);

        param = binding.chipPeriod.getText().toString();
        if (!param.equals(String.format(getString(R.string.format_seconds), DEFAULT_PERIOD))) {
            Pattern pat = Pattern.compile("\\d+");
            Matcher m = pat.matcher(param);
            m.find();
            builder.appendQueryParameter(PERIOD_PARAM, m.group());
        }

        param = binding.chipDigits.getText().toString();
        if (!param.equals(DIGITS[0])) {
            builder.appendQueryParameter(DIGITS_PARAM, param);
        }

        param = binding.chipAlgorithm.getText().toString();
        if (!param.equals(ALGORITHM[0])) {
            builder.appendQueryParameter(ALGORITHM_PARAM, param);
        }

        var uri = builder.build().toString();

        otp = new OneTimePassword(uri);

        if (selectedAlias == null) {
            generateResponseCode(true);
        } else {
            try {
                var result = MessageComposer.encodeAsOmsText(
                        new TotpUriTransfer(uri.getBytes(),
                                (RSAPublicKey) cryptographyManager.getCertificate(selectedAlias).getPublicKey(),
                                RSAUtils.getRsaTransformationIdx(preferences),
                                AESUtil.getKeyLength(preferences),
                                AESUtil.getAesTransformationIdx(preferences)).getMessage());

                keyStoreListFragment.getOutputFragment().setMessage(result, "TOTP Configuration (encrypted)");
                binding.textViewTotp.setText(MessageComposer.OMS_PREFIX + "...");
                binding.textViewTimer.setText("");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        binding.editTextSecret.setEnabled(selectedAlias == null);
        binding.editTextLabel.setEnabled(selectedAlias == null);
        binding.chipAlgorithm.setEnabled(selectedAlias == null);
        binding.chipDigits.setEnabled(selectedAlias == null);
        binding.chipPeriod.setEnabled(selectedAlias == null);

        generateResponseCode(true);
    }

    private TimerTask getTimerTask() {
        return new TimerTask() {
            @Override
            public void run() {
                generateResponseCode(false);
                timer.schedule(getTimerTask(), 1000);
            }
        };
    }

    private void generateResponseCode(boolean force) {
        if (otp == null) return; //otp not set yet

        if (selectedAlias != null) return;

        try {
            long[] state = otp.getState();
            var code = otp.generateResponseCode(state[0]);

            requireActivity().getMainExecutor().execute(() -> {
                if (binding == null) return; //fragment has been destroyed
                binding.textViewTimer.setText(String.format("...%ss", otp.getPeriod() - state[1]));
                binding.textViewTotp.setText(code);

                if (lastState != state[0] || force) {
                    //new State = new code; update output fragment
                    keyStoreListFragment.getOutputFragment().setMessage(code, "One-Time-Password");
                    lastState = state[0];
                }
            });
        } catch (Exception e) {
            //invalid secret
            Log.e(TAG, e.getMessage());
            keyStoreListFragment.getOutputFragment().setMessage(null, null);
            requireActivity().getMainExecutor().execute(() -> {
                binding.textViewTotp.setText("-".repeat(Integer.parseInt(binding.chipDigits.getText().toString())));
                binding.textViewTimer.setText("");
            });
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timer.cancel();
        binding = null;
    }
}