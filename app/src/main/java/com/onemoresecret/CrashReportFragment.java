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
import java.util.function.Function;

public class CrashReportFragment extends Fragment {

    private FragmentCrashReportBinding binding;
    private CrashReportData crashReportData;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCrashReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        crashReportData = (CrashReportData) requireActivity().getIntent().getSerializableExtra(OmsUncaughtExceptionHandler.EXTRA_CRASH_REPORT);

        binding.chkLogcat.setOnCheckedChangeListener((buttonView, isChecked) -> displayCrashReport());
        binding.btnDismiss.setOnClickListener(v -> requireActivity().finish());
        binding.btnSend.setOnClickListener(v -> sendEmail());
        displayCrashReport();
    }

    private void sendEmail() {
        final Function<String, Intent> intentFx = action -> {
            var intent = new Intent(action);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret crash report");
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.contact_email)});
            return intent;
        };

        try {
            var crashReport = crashReportData.toString(binding.chkLogcat.isChecked());
            var attachment = Util.toStream(requireContext(),
                    "crash_report.txt",
                    crashReport.getBytes(StandardCharsets.UTF_8),
                    false
            );
            var intentSend = intentFx.apply(Intent.ACTION_SEND);
            intentSend.putExtra(Intent.EXTRA_STREAM, attachment);
            intentSend.putExtra(Intent.EXTRA_TEXT, "The report file has been attached. Please use this email to provide additional feedback (this is optional).");

            try {
                //try send as attached file
                startActivity(intentSend);
                requireActivity().finish();
            } catch (ActivityNotFoundException ex) {
                try {
                    //email without attachment
                    var intentSendTo = intentFx.apply(Intent.ACTION_SENDTO);
                    intentSendTo.putExtra(Intent.EXTRA_TEXT, crashReport);
                    startActivity(intentSendTo);
                    requireActivity().finish();
                } catch (ActivityNotFoundException exx) {
                    requireContext().getMainExecutor().execute(() -> {
                        Toast.makeText(getContext(), "Could not send email", Toast.LENGTH_LONG).show();
                        requireActivity().finish();
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
}