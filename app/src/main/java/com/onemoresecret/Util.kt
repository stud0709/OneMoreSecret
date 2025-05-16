package com.onemoresecret

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.Objects
import androidx.core.net.toUri

object Util {
    const val FLAVOR_FOSS = "foss"
    const val BASE64_LINE_LENGTH = 75

    @JvmField
    val JACKSON_MAPPER = ObjectMapper()

    @JvmOverloads
    @JvmStatic
    fun byteArrayToHex(a: ByteArray, spaces: Boolean = true): String {
        val sb = StringBuilder()

        for (i in a.indices) {
            sb.append(String.format("%02x", a[i])).append(if (i % 2 == 1 && spaces) " " else "")
        }
        return sb.toString().trim { it <= ' ' }
    }

    fun hexToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((((s[i].digitToIntOrNull(16) ?: (-1 shl 4)) + s[i + 1]
                .digitToIntOrNull(16)!!) ?: -1)).toByte()
            i += 2
        }
        return data
    }

    /**
     * open URL via [android.content.Intent].
     */
    @JvmStatic
    fun openUrl(stringId: Int, ctx: Context) {
        val url = ctx.getString(stringId)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setData(url.toUri())
        ctx.startActivity(intent)
    }

    @JvmStatic
    fun getFileInfo(ctx: Context, uri: Uri): UriFileInfo {
        ctx.contentResolver.query(uri, null, null, null, null).use { cursor ->
            Objects.requireNonNull(cursor)
            cursor!!.moveToFirst()

            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return UriFileInfo(cursor.getString(nameIndex), cursor.getInt(sizeIndex))
        }
    }

    @JvmStatic
    fun discardBackStack(fragment: Fragment) {
        val navController = NavHostFragment.findNavController(fragment)
        navController.popBackStack(navController.graph.startDestinationId, false)
    }

    @JvmRecord
    data class UriFileInfo(@JvmField val filename: String, @JvmField val fileSize: Int)
}
