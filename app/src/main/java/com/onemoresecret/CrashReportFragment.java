package com.onemoresecret;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.onemoresecret.databinding.FragmentCrashReportBinding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CrashReportFragment extends Fragment {

    private FragmentCrashReportBinding binding;
    private CrashReportData crashReportData;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCrashReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        crashReportData = (CrashReportData) requireActivity().getIntent().getSerializableExtra(OmsUncaughtExceptionHandler.EXTRA_CRASH_REPORT);

        binding.chkDeviceData.setOnCheckedChangeListener((buttonView, isChecked) -> displayCrashReport());
        binding.chkLogcat.setOnCheckedChangeListener((buttonView, isChecked) -> displayCrashReport());
        binding.btnDismiss.setOnClickListener(v -> endProcess());
        binding.btnSend.setOnClickListener(v -> sendEmail());
        displayCrashReport();
    }

    private void sendEmail() {
        try {
            Uri attachment = Util.toStream(requireContext(),
                    "crash_report.txt",
                    crashReportData.toString(binding.chkDeviceData.isChecked(), binding.chkLogcat.isChecked()).getBytes(StandardCharsets.UTF_8),
                    false
            );
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret crash report");
//            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"TODO"});
            intent.putExtra(Intent.EXTRA_STREAM, attachment);
            intent.putExtra(Intent.EXTRA_TEXT, "The report file has been attached. Please use this email to provide additional feedback (this is optional).");
            startActivity(intent);
            endProcess();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void displayCrashReport() {
        binding.txtCrashReport.setText(crashReportData.toString(binding.chkDeviceData.isChecked(), binding.chkLogcat.isChecked()));
    }

    private void endProcess() {
        getActivity().finish();
//            android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

}