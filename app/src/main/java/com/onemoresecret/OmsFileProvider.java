package com.onemoresecret;

import androidx.core.content.FileProvider;

/**
 * Extended @{link {@link FileProvider} as described in https://developer.android.com/reference/androidx/core/content/FileProvider.
 */
public class OmsFileProvider extends FileProvider {
    public OmsFileProvider() {
        super(R.xml.filepaths);
    }
}
