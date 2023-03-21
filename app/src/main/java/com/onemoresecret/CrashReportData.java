package com.onemoresecret;

import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class CrashReportData implements Serializable {

    private final Throwable throwable;
    private final List<String> logcat = new ArrayList<>();

    public CrashReportData(Throwable throwable) {
        this.throwable = throwable;

        try {
            String s = "logcat -d";
            Process p = Runtime.getRuntime().exec(s);

            try (BufferedReader bais = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                int cnt = 0;
                String line;
                while ((line = bais.readLine()) != null) {
                    if (cnt++ < 100) {
                        logcat.add(line);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    public String toString(boolean includeDeviceData, boolean includeLogcat) {
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            pw.println("OneMoreSecret version: " + BuildConfig.VERSION_NAME);
            pw.println("\n----- STACK TRACE -----");
            throwable.printStackTrace(pw);
            if (includeDeviceData) {
                pw.println("\n----- DEVICE -----");
                pw.println("Brand: " + Build.BRAND);
                pw.println("Device: " + Build.DEVICE);
                pw.println("Model: " + Build.MODEL);
                pw.println("ID: " + Build.ID);
                pw.println("Product: " + Build.PRODUCT);
                pw.println("\n----- ANDROID OS -----");
                pw.println("Android SDK: " + Build.VERSION.SDK_INT);
                pw.println("Android release: " + Build.VERSION.RELEASE);
                pw.println("Android incremental: " + Build.VERSION.INCREMENTAL);
            }
            if (includeLogcat) {
                pw.println("\n----- LOGCAT -----");
                logcat.stream().forEach(s -> pw.println(s));
            }
            pw.println("----- END OF REPORT -----");
            return sw.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
