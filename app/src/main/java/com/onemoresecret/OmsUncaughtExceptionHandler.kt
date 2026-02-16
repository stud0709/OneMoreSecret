package com.onemoresecret

import android.app.Activity
import android.content.Intent
import kotlin.system.exitProcess

class OmsUncaughtExceptionHandler(private val activity: Activity) :
    Thread.UncaughtExceptionHandler {
    override fun uncaughtException(t: Thread, e: Throwable) {
        val crashReportData = CrashReportData(e)
        val intent = Intent(activity.applicationContext, CrashReportActivity::class.java).apply{
            putExtra(EXTRA_CRASH_REPORT, crashReportData)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        activity.applicationContext.startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
        exitProcess(1)
    }

    companion object {
        const val EXTRA_CRASH_REPORT: String = "CRASH_REPORT"
    }
}
