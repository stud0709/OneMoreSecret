package com.onemoresecret

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.onemoresecret.composable.CrashReport
import com.onemoresecret.composable.OneMoreSecretTheme
import kotlin.system.exitProcess

class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread { OmsFileProvider.purgeTmp(this) }.start()

        @Suppress("DEPRECATION")
        val crashReportData = intent.getSerializableExtra(OmsUncaughtExceptionHandler.EXTRA_CRASH_REPORT) as? CrashReportData
        
        setContent {
            OneMoreSecretTheme {
                if (crashReportData != null) {
                    CrashReport(
                        crashReportData = crashReportData,
                        onDismiss = {
                            finish()
                            exitProcess(0)
                        }
                    )
                }
            }
        }
    }
}
