package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.util.Objects;

public final class Util {
    public static final String FLAVOR_FOSS = "foss";
    private Util() {
    }

    public static String byteArrayToHex(byte[] a) {
        return byteArrayToHex(a, true);
    }

    public static String byteArrayToHex(byte[] a, boolean spaces) {
        var sb = new StringBuilder();

        for (int i = 0; i < a.length; i++) {
            sb.append(String.format("%02x", a[i])).append(i % 2 == 1 && spaces ? " " : "");
        }
        return sb.toString().trim();
    }

    public static byte[] hexToByteArray(String s) {
        var len = s.length();
        var data = new byte[len / 2];
        for (var i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * open URL via {@link android.content.Intent}.
     */
    public static void openUrl(int stringId, Context ctx) {
        var url = ctx.getString(stringId);
        var intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        ctx.startActivity(intent);
    }

    public record UriFileInfo(String filename, int fileSize) {
    }

    ;

    public static UriFileInfo getFileInfo(Context ctx, Uri uri) {
        try (Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null)) {
            Objects.requireNonNull(cursor);
            cursor.moveToFirst();

            var sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
            var nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            return new UriFileInfo(cursor.getString(nameIndex), cursor.getInt(sizeIndex));
        }
    }
}
