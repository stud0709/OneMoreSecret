package com.onemoresecret;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.databinding.FragmentPermissionsBinding;

import java.util.Arrays;

public class PermissionsFragment extends Fragment {
    public static final String PROP_PERMISSIONS_REQUESTED = "permissions_requested";
    private static final String TAG = PermissionsFragment.class.getSimpleName();

    public static final String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.CAMERA
    };

    FragmentPermissionsBinding binding;

    ActivityResultLauncher<String[]> activityResultLauncher;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPermissionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SharedPreferences preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);
        preferences.edit().putBoolean(PROP_PERMISSIONS_REQUESTED, true).apply();
        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Log.d(TAG, String.format("Granted permissions: %s", result));
                    Util.discardBackStack(PermissionsFragment.this);
                });

        //request all app permissions
        binding.btnProceed.setOnClickListener(v -> activityResultLauncher.launch(REQUIRED_PERMISSIONS));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public static boolean isAllPermissionsGranted(String tag, @NonNull Context ctx, String... permissions) {
        if (Arrays.stream(permissions).allMatch(p -> ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED))
            return true;

        Log.d(tag, "Granted permissions:");

        Arrays.stream(permissions).forEach(p -> {
            var check = ContextCompat.checkSelfPermission(ctx, p);
            Log.d(tag, p + ": " + (check == PackageManager.PERMISSION_GRANTED) + " (" + check + ")");
        });

        return false;
    }
}