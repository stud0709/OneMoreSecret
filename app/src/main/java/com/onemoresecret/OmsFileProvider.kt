package com.onemoresecret

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Extended @{link [FileProvider] as described in [android.core.content.FileProvider API](https://developer.android.com/reference/androidx/core/content/FileProvider).
 */
open class OmsFileProvider : FileProvider(R.xml.filepaths) {

    companion object {
        private val TAG: String = OmsFileProvider::class.java.simpleName

        @JvmStatic
        @Throws(IOException::class)
        fun create(ctx: Context, filename: String?, deleteOnExit: Boolean): FileRecord? {
            val dir = File(ctx.cacheDir, "tmp")
            if (!dir.exists() && !dir.mkdirs()) return null //something went wrong


            val p = dir.toPath().resolve(filename)
            val f = p.toFile()
            if (Files.exists(p)) Files.delete(p)
            if (deleteOnExit) f.deleteOnExit()

            return FileRecord(p, getUriForFile(ctx, "com.onemoresecret.fileprovider", f))
        }

        @JvmStatic
        fun purgeTmp(ctx: Context) {
            val dir = File(ctx.cacheDir, "tmp")
            if (!dir.exists()) return

            //Log.d(TAG, String.format("purgeTmp called by %s", Thread.currentThread().getStackTrace()[3]));
            try {
                Files.list(dir.toPath()).use { fileList ->
                    fileList.filter { p: Path? -> !Files.isDirectory(p) }.forEach { p: Path? ->
                        try {
                            Files.delete(p)
                        } catch (ex: IOException) {
                            ex.printStackTrace()
                        }
                    }
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        }
    }

    @JvmRecord
    data class FileRecord(@JvmField val path: Path, @JvmField val uri: Uri)
}
