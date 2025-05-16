package com.onemoresecret.compose

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import com.onemoresecret.CrashReportData
import com.onemoresecret.OmsFileProvider
import com.onemoresecret.OmsUncaughtExceptionHandler
import com.onemoresecret.R
import com.onemoresecret.databinding.FragmentCrashReportBinding
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.function.Function

class CrashReportViewModel(val crashReportData: CrashReportData, val resourceProvider: ResourceProvider) {
    val chkLogcat = mutableStateOf(false)
    val crashReport = mutableStateOf<String?>(null)

    internal fun getIntent(action:String):Intent {
        val intent = Intent(action)
        intent.setData("mailto:".toUri())
        intent.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret crash report")
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(resourceProvider.getString(R.string.contact_email)))
        return intent
    }

    internal fun displayCrashReport() {
        crashReport.value = crashReportData.toString(chkLogcat.value)
    }
}