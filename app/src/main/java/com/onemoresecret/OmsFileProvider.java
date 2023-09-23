package com.onemoresecret;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extended @{link {@link FileProvider} as described in <a href="https://developer.android.com/reference/androidx/core/content/FileProvider">android.core.content.FileProvider API</a>.
 */
public class OmsFileProvider extends FileProvider {
    public OmsFileProvider() {
        super(R.xml.filepaths);
    }

    public record FileRecord(Path path, Uri uri) {
    }

    public static FileRecord create(Context ctx, String filename, boolean deleteOnExit) throws IOException {
        var dir = new File(ctx.getCacheDir(), "tmp");
        if (!dir.exists()) dir.mkdirs();

        var p = dir.toPath().resolve(filename);
        File f = p.toFile();
        if (Files.exists(p)) Files.delete(p);
        if (deleteOnExit) f.deleteOnExit();

        return new FileRecord(p, OmsFileProvider.getUriForFile(ctx, "com.onemoresecret.fileprovider", f));
    }
}
