package com.onemoresecret;

import android.os.Build;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

public class CrashReportData implements Serializable {

    private final Throwable throwable;
    private final String logcat;

    public CrashReportData(Throwable throwable) {
        this.throwable = throwable;
        logcat = getLogcat();
    }

    public static String getLogcat() {
        try {
            var s = "logcat -b all -d";
            var p = Runtime.getRuntime().exec(s);

            try (BufferedReader bais = new BufferedReader(new InputStreamReader(p.getInputStream())); StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
                String line;
                while ((line = bais.readLine()) != null) {
                    pw.println(line);
                }
                return sw.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String toString(boolean includeLogcat) {
        try (StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw)) {
            pw.println(String.format("OneMoreSecret version: %s (%s)", BuildConfig.VERSION_NAME, BuildConfig.FLAVOR));
            if (throwable != null) {
                pw.println("\n----- STACK TRACE -----");
                throwable.printStackTrace(pw);
            }
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
            if (includeLogcat) {
                pw.println("\n----- LOGCAT -----");
                pw.println(logcat);
            }
            pw.println("----- END OF REPORT -----");
            return sw.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
