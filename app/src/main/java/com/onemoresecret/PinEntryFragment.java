package com.onemoresecret;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.onemoresecret.databinding.FragmentPinEntryBinding;
import com.onemoresecret.databinding.FragmentQrBinding;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class PinEntryFragment extends Fragment {
    private FragmentPinEntryBinding binding;
    private SharedPreferences preferences;

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

        var bList = Arrays.asList(bArr);

        var rnd = new Random();

        while (!bList.isEmpty()) {
            var txt = Integer.toString(rnd.nextInt(bList.size()));
            bList.remove(0).setText(txt);
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
        //todo
    }

    private void onDigit(View v) {
        binding.textViewPin.append(((Button) v).getText());
    }
}