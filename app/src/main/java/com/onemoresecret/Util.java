package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    public static Uri toStream(Context ctx, String filename, byte[] content, boolean deleteOnExit) throws IOException {
        File dir = new File(ctx.getCacheDir(), "tmp");
        if (!dir.exists()) dir.mkdirs();

        Path p = dir.toPath().resolve(filename);
        if (Files.exists(p)) Files.delete(p);

        Files.write(p, content);
        if (deleteOnExit) p.toFile().deleteOnExit();

        return OmsFileProvider.getUriForFile(ctx, "com.onemoresecret.fileprovider", p.toFile());
    }
}
