package com.onemoresecret

import android.app.Activity
import android.content.Intent

class OmsUncaughtExceptionHandler(private val activity: Activity) :
    Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        val crashReportData = CrashReportData(e)
        val intent = Intent(activity, CrashReportActivity::class.java)
        intent.putExtra(EXTRA_CRASH_REPORT, crashReportData)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        activity.startActivity(intent)
        activity.finish()
    }

    companion object {
        const val EXTRA_CRASH_REPORT: String = "CRASH_REPORT"
    }
}
