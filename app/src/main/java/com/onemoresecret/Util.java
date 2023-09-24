package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.navigation.fragment.NavHostFragment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;

public final class Util {
    private Util() {
    }

    public static String byteArrayToHex(byte[] a) {
        var sb = new StringBuilder();

        for (int i = 0; i < a.length; i++) {
            sb.append(String.format("%02x", a[i])).append(i % 2 == 1 ? " " : "");
        }
        return sb.toString().trim();
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
