package com.onemoresecret;

import androidx.core.content.FileProvider;

/**
 * Extended @{link {@link FileProvider} as described in <a href="https://developer.android.com/reference/androidx/core/content/FileProvider">android.core.content.FileProvider API</a>.
 */
public class OmsFileProvider extends FileProvider {
    public OmsFileProvider() {
        super(R.xml.filepaths);
    }
}
