package com.onemoresecret

import android.os.Build
import android.util.Log
import com.onemoresecret.Util.printStackTrace
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.Serializable
import java.io.StringWriter

class CrashReportData(throwable: Throwable?, logcatTailLines: Int? = null) : Serializable {
    private val stackTrace: String? = throwable?.let { renderStackTrace(it) }
    private val logcat: String? = getLogcat(logcatTailLines)

    fun toString(includeLogcat: Boolean): String? {
        try {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw ->
                    pw.println(
                        String.format(
                            "OneMoreSecret version: %s (%s)",
                            BuildConfig.VERSION_NAME,
                            BuildConfig.FLAVOR
                        )
                    )
                    if (stackTrace != null) {
                        pw.println("\n----- STACK TRACE -----")
                        pw.println(stackTrace)
                    }
                    pw.println("\n----- DEVICE -----")
                    pw.println("Brand: " + Build.BRAND)
                    pw.println("Device: " + Build.DEVICE)
                    pw.println("Model: " + Build.MODEL)
                    pw.println("ID: " + Build.ID)
                    pw.println("Product: " + Build.PRODUCT)
                    pw.println("\n----- ANDROID OS -----")
                    pw.println("Android SDK: " + Build.VERSION.SDK_INT)
                    pw.println("Android release: " + Build.VERSION.RELEASE)
                    pw.println("Android incremental: " + Build.VERSION.INCREMENTAL)
                    if (includeLogcat) {
                        pw.println("\n----- LOGCAT -----")
                        pw.println(logcat)
                    }
                    pw.println("----- END OF REPORT -----")
                    return sw.toString()
                }
            }
        } catch (ex: IOException) {
            printStackTrace(ex)
            return null
        }
    }

    companion object {
        private val TAG: String = CrashReportData::class.java.simpleName

        private fun renderStackTrace(t: Throwable): String = try {
            StringWriter().use { sw ->
                PrintWriter(sw).use { pw -> t.printStackTrace(pw) }
                sw.toString()
            }
        } catch (_: Throwable) {
            "(stack trace unavailable)"
        }

        fun getLogcat(tailLines: Int? = null): String? = if (tailLines == null) {
            getProcessOutput("logcat", "-b", "all", "-d")
        } else {
            getProcessOutput("logcat", "-b", "all", "-d", "-t", tailLines.toString())
        }

        fun getProcessOutput(vararg sArr: String?): String? {
            Log.d(TAG, String.format("Collecting data from %s...", sArr.contentToString()))
            try {
                val p = Runtime.getRuntime().exec(sArr)

                BufferedReader(InputStreamReader(p.inputStream)).use { bais ->
                    StringWriter().use { sw ->
                        PrintWriter(sw).use { pw ->
                            var line: String?
                            while ((bais.readLine().also { line = it }) != null) {
                                if (line?.contains("setRequestedFrameRate") == true) continue //noise from CameraX
                                pw.println(line)
                            }
                            Log.d(
                                TAG,
                                String.format(
                                    "Done collecting data from %s...",
                                    sArr.contentToString()
                                )
                            )
                            return sw.toString()
                        }
                    }
                }
            } catch (e: IOException) {
                printStackTrace(e)
            }
            return null
        }
    }
}
