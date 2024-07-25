package com.onemoresecret;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extended @{link {@link FileProvider} as described in <a href="https://developer.android.com/reference/androidx/core/content/FileProvider">android.core.content.FileProvider API</a>.
 */
public class OmsFileProvider extends FileProvider {
    private static final String TAG = OmsFileProvider.class.getSimpleName();

    public OmsFileProvider() {
        super(R.xml.filepaths);
    }

    public record FileRecord(Path path, Uri uri) {
    }

    public static FileRecord create(Context ctx, String filename, boolean deleteOnExit) throws IOException {
        var dir = new File(ctx.getCacheDir(), "tmp");
        if (!dir.exists() && !dir.mkdirs()) return null; //something went wrong

        var p = dir.toPath().resolve(filename);
        File f = p.toFile();
        if (Files.exists(p)) Files.delete(p);
        if (deleteOnExit) f.deleteOnExit();

        return new FileRecord(p, OmsFileProvider.getUriForFile(ctx, "com.onemoresecret.fileprovider", f));
    }

    public static void purgeTmp(Context ctx) {
        var dir = new File(ctx.getCacheDir(), "tmp");
        if (!dir.exists()) return;

        //Log.d(TAG, String.format("purgeTmp called by %s", Thread.currentThread().getStackTrace()[3]));

        try (var fileList = Files.list(dir.toPath())) {
            fileList.filter(p -> !Files.isDirectory(p)).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            });
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
