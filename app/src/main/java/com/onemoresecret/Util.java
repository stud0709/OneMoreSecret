package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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

    public static Uri toStream(Context ctx, String filename, byte[] content, boolean deleteOnExit) throws IOException {
        var dir = new File(ctx.getCacheDir(), "tmp");
        if (!dir.exists()) dir.mkdirs();

        var p = dir.toPath().resolve(filename);
        if (Files.exists(p)) Files.delete(p);

        Files.write(p, content);
        if (deleteOnExit) p.toFile().deleteOnExit();

        return OmsFileProvider.getUriForFile(ctx, "com.onemoresecret.fileprovider", p.toFile());
    }
}
