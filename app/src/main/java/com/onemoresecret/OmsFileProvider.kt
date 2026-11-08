package com.onemoresecret

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.onemoresecret.Util.printStackTrace
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Extended @{link [FileProvider] as described in [android.core.content.FileProvider API](https://developer.android.com/reference/androidx/core/content/FileProvider).
 */
class OmsFileProvider : FileProvider() {
    companion object {
        private val TAG: String = OmsFileProvider::class.java.simpleName

        @JvmStatic
        @Throws(IOException::class)
        fun create(ctx: Context, filename: String?, deleteOnExit: Boolean): FileRecord {
            val dir = File(ctx.cacheDir, "tmp")
            require(dir.exists() || dir.mkdirs()) { "Cannot create temporary directory" }
            val name = requireNotNull(filename)
            require(name.isNotBlank() && name != "." && name != ".." && !name.contains('/') && !name.contains('\\')) { "Unsafe filename" }
            val root = dir.toPath().toAbsolutePath().normalize()
            val p = root.resolve(name).normalize()
            require(p.parent == root) { "Unsafe output path" }
            val f = p.toFile()
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
                            printStackTrace(ex)
                        }
                    }
                }
            } catch (ex: IOException) {
                printStackTrace(ex)
            }
        }
    }

    @JvmRecord
    data class FileRecord(@JvmField val path: Path?, @JvmField val uri: Uri?)

}
