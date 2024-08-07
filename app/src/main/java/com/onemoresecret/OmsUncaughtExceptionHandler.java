package com.onemoresecret;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;

public class OmsUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    public static final String EXTRA_CRASH_REPORT = "CRASH_REPORT";
    private final Activity activity;

    public OmsUncaughtExceptionHandler(Activity context) {
        this.activity = context;
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        var crashReportData = new CrashReportData(e);
        var intent = new Intent(activity, CrashReportActivity.class);
        intent.putExtra(EXTRA_CRASH_REPORT, crashReportData);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }
}
