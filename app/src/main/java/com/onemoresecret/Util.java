package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.DataInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;

public final class Util {
    private Util() {
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < a.length; i++) {
            sb.append(String.format("%02x", a[i])).append(i % 2 == 1 ? " " : "");
        }
        return sb.toString().trim();
    }

    /**
     * open URL via {@link android.content.Intent}.
     */
    public static void openUrl(int stringId, Context ctx) {
        String url = ctx.getString(stringId);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        ctx.startActivity(intent);
    }


}