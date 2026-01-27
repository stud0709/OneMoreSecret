package com.onemoresecret;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentPresetBinding;

import java.util.function.Consumer;


public class PresetFragment extends Fragment {
    private static final String TAG = PresetFragment.class.getSimpleName();
    private FragmentPresetBinding binding;
    private final Consumer<QRFragment.PresetEntry> onClick;
    private final Consumer<QRFragment.PresetEntry> onLongClick;
    private final QRFragment.PresetEntry presetEntry;

    public PresetFragment(QRFragment.PresetEntry presetEntry, Consumer<QRFragment.PresetEntry> onClick, Consumer<QRFragment.PresetEntry> onLongClick) {
        this.onClick = onClick;
        this.onLongClick = onLongClick;
        this.presetEntry = presetEntry;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPresetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        super.onViewCreated(view, savedInstanceState);

        //apply preset
        binding.button.setOnClickListener(v -> onClick.accept(presetEntry));

        //enter preset configuration
        binding.button.setOnLongClickListener(v -> {
            onLongClick.accept(presetEntry);
            return true;
        });

        binding.button.setText(presetEntry.symbol());
        binding.txtName.setText(presetEntry.name());
    }
}