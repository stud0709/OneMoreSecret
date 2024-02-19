package com.onemoresecret;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentWiFiPairingBinding;

public class WiFiPairingFragment extends Fragment {
    private FragmentWiFiPairingBinding binding;

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        binding = FragmentWiFiPairingBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.txtResponseCode.setVisibility(View.INVISIBLE);
        binding.txtIntroResponse.setVisibility(View.INVISIBLE);
    }

    public void setData(String requestId, String responseCode, Runnable onConfirm) {
        binding.txtRequestId.setText(requestId);
        binding.txtResponseCode.setText(responseCode);
        binding.btnConfirm.setOnClickListener(e -> {
            binding.btnConfirm.setEnabled(false);
            binding.btnConfirm.setText(R.string.pairing_accepted);
            binding.txtResponseCode.setVisibility(View.VISIBLE);
            binding.txtIntroResponse.setVisibility(View.VISIBLE);
            onConfirm.run();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}