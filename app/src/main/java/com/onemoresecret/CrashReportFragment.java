package com.onemoresecret;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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

        binding.chkLogcat.setOnCheckedChangeListener((buttonView, isChecked) -> displayCrashReport());
        binding.btnDismiss.setOnClickListener(v -> endProcess());
        binding.btnSend.setOnClickListener(v -> sendEmail());
        displayCrashReport();
    }

    private void sendEmail() {
        try {
            String crashReport = crashReportData.toString(binding.chkLogcat.isChecked());
            Uri attachment = Util.toStream(requireContext(),
                    "crash_report.txt",
                    crashReport.getBytes(StandardCharsets.UTF_8),
                    false
            );
            Intent intentSend = new Intent(Intent.ACTION_SEND);
            intentSend.setData(Uri.parse("mailto:"));
            intentSend.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret crash report");
//            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"TODO"});
            intentSend.putExtra(Intent.EXTRA_STREAM, attachment);
            intentSend.putExtra(Intent.EXTRA_TEXT, "The report file has been attached. Please use this email to provide additional feedback (this is optional).");

            try {
                //try send as attached file
                startActivity(intentSend);
                endProcess();
            } catch (ActivityNotFoundException ex) {
                try {
                    //email without attachment
                    Intent intentSendTo = new Intent(Intent.ACTION_SENDTO);
                    intentSendTo.setData(Uri.parse("mailto:"));
                    intentSendTo.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret crash report");
//                intentSendTo.putExtra(Intent.EXTRA_EMAIL, new String[]{"TODO"});
                    intentSendTo.putExtra(Intent.EXTRA_TEXT, crashReport);
                    startActivity(intentSendTo);
                    endProcess();
                } catch (ActivityNotFoundException exx) {
                    getContext().getMainExecutor().execute(() -> {
                        Toast.makeText(getContext(), "Could not send email", Toast.LENGTH_LONG).show();
                        endProcess();
                    });
                }
            }
            if (intentSend.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intentSend);
                endProcess();
            } else {
                //email without attachment
                Intent intentSendTo = new Intent(Intent.ACTION_SENDTO);
                intentSendTo.setData(Uri.parse("mailto:"));
                intentSendTo.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret crash report");
//                intentSendTo.putExtra(Intent.EXTRA_EMAIL, new String[]{"TODO"});
                intentSendTo.putExtra(Intent.EXTRA_TEXT, crashReport);
                if (intentSendTo.resolveActivity(requireActivity().getPackageManager()) != null) {
                    startActivity(intentSendTo);
                    endProcess();
                } else {
                    getContext().getMainExecutor().execute(() -> {
                        Toast.makeText(getContext(), "Could not send email", Toast.LENGTH_LONG).show();
                        endProcess();
                    });
                }
            }

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
        binding.txtCrashReport.setText(crashReportData.toString(binding.chkLogcat.isChecked()));
    }

    private void endProcess() {
        getActivity().finish();
//            android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

}