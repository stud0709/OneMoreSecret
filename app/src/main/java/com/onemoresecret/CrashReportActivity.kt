package com.onemoresecret

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onemoresecret.composable.OneMoreSecretTheme
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.system.exitProcess

private const val MAX_INLINE_REPORT_LENGTH = 128 * 1024

class CrashReportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //prohibit screenshots
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        Thread { OmsFileProvider.purgeTmp(this) }.start()

        @Suppress("DEPRECATION")
        val crashReportData = intent.getSerializableExtra(OmsUncaughtExceptionHandler.EXTRA_CRASH_REPORT) as? CrashReportData

        if (crashReportData == null) {
            finish()
            return
        }

        setContent {
            OneMoreSecretTheme {
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



@Composable
fun CrashReport(
    crashReportData: CrashReportData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var includeLogcat by remember { mutableStateOf(false) }

    // Equivalent to displayCrashReport()
    val reportText = remember(includeLogcat) {
        crashReportData.toString(includeLogcat) ?: ""
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Scrollable area for the report text
        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = reportText,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Checkbox for Logcat
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = includeLogcat,
                onCheckedChange = { includeLogcat = it }
            )
            Text(text = "Include Logcat")
        }

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = {
                copyToClipboard(context, reportText)
            }) {
                Text(stringResource(R.string.copy))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                shareReport(context, reportText)
            }) {
                Text(stringResource(R.string.share))
            }
        }
    }
}

private fun copyToClipboard(context: Context, report: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clipData = ClipData.newPlainText("oneMoreSecret", report)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clipData.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }

    clipboardManager.setPrimaryClip(clipData)
    Toast.makeText(context, context.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show()
}

// Extracted logic for sharing the report
private fun shareReport(context: Context, report: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_email_subject))
        putExtra(Intent.EXTRA_TITLE, context.getString(R.string.crash_email_subject))
        putExtra(Intent.EXTRA_EMAIL, arrayOf(context.getString(R.string.contact_email)))
    }

    val attached = try {
        val fileRecord = OmsFileProvider.create(context, "crash_report.txt", false, "crash")
        Files.write(fileRecord.path, report.toByteArray(StandardCharsets.UTF_8))
        sendIntent.putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (ex: IOException) {
        Util.printStackTrace(ex)
        false
    }

    val body = context.getString(R.string.crash_email_body)
    sendIntent.putExtra(
        Intent.EXTRA_TEXT,
        when {
            report.length <= MAX_INLINE_REPORT_LENGTH -> "$body\n\n$report"
            attached -> body
            else -> "$body\n\n" + report.take(MAX_INLINE_REPORT_LENGTH)
        }
    )

    context.startActivity(Intent.createChooser(sendIntent, null))
}
