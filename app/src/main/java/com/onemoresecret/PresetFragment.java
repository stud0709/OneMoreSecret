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


public class PresetFragment extends Fragment {
    private static final String TAG = PresetFragment.class.getSimpleName();
    private FragmentPresetBinding binding;
    private final Runnable onClick, onLongClick;

    public PresetFragment(Runnable onClick, Runnable onLongClick) {
        this.onClick = onClick;
        this.onLongClick = onLongClick;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPresetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.d(TAG, "onViewCreated");
        super.onViewCreated(view, savedInstanceState);

        //apply preset
        binding.button.setOnClickListener(v -> {
            onClick.run();
        });

        //enter preset configuration
        binding.button.setOnLongClickListener(v -> {
            this.onLongClick.run();
            return true;
        });
    }
}