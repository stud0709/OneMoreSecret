package com.onemoresecret.composable

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onemoresecret.CrashReportData
import com.onemoresecret.OmsFileProvider
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import com.onemoresecret.R
import androidx.core.net.toUri

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
    Surface(color = MaterialTheme.colorScheme.background) {
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
            Button(onClick = {
                sendEmail(context, crashReportData, includeLogcat, onDismiss)
            }) {
                Text("Send")
            }
        }
    }}
}

// Extracted logic for sending the email
private fun sendEmail(
    context: android.content.Context,
    crashReportData: CrashReportData,
    includeLogcat: Boolean,
    onComplete: () -> Unit
) {
    val contactEmail = context.getString(R.string.contact_email)

    fun createBaseIntent(action: String): Intent {
        return Intent(action).apply {
            data = "mailto:".toUri()
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_email_subject))
            putExtra(Intent.EXTRA_EMAIL, arrayOf(contactEmail))
        }
    }

    try {
        val crashReport = crashReportData.toString(includeLogcat)
        val fileRecord = OmsFileProvider.create(context, "crash_report.txt", false)
        Files.write(fileRecord.path, crashReport!!.toByteArray(StandardCharsets.UTF_8))

        val intentSend = createBaseIntent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.crash_email_body))
        }

        try {
            context.startActivity(intentSend)
            onComplete()
        } catch (_: ActivityNotFoundException) {
            try {
                val intentSendTo = createBaseIntent(Intent.ACTION_SENDTO).apply {
                    putExtra(Intent.EXTRA_TEXT, crashReport)
                }
                context.startActivity(intentSendTo)
                onComplete()
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context,
                    context.getString(R.string.could_not_send_email), Toast.LENGTH_LONG).show()
                onComplete()
            }
        }
    } catch (ex: IOException) {
        ex.printStackTrace()
    }
}