package com.onemoresecret.compose

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onemoresecret.R
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.onemoresecret.CrashReportData
import com.onemoresecret.OmsFileProvider
import com.onemoresecret.OmsUncaughtExceptionHandler
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@Composable
fun CrashReportScreen(snackbarHostState: SnackbarHostState?, contentPadding: PaddingValues) {
    val activity = LocalActivity.current!!
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val crashReportData =
        activity.intent?.getSerializableExtra(OmsUncaughtExceptionHandler.EXTRA_CRASH_REPORT) as CrashReportData?

    val viewModel = CrashReportViewModel(crashReportData!!, ResourceProvider(context))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Text(text = stringResource(id = R.string.disclaimer_crash_report))

        CheckboxWithText(
            text = stringResource(id = R.string.chk_stack_trace),
            checked = true,
            enabled = false
        )
        CheckboxWithText(
            text = stringResource(id = R.string.chk_device_info),
            checked = true,
            enabled = false
        )
        CheckboxWithText(
            text = stringResource(id = R.string.chk_logcat),
            checked = viewModel.chkLogcat.value,
            onCheckedChange = { viewModel.displayCrashReport() }
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = crashReportData.toString(viewModel.chkLogcat.value).toString(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = activity::finish,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(text = stringResource(id = R.string.btn_dismiss))
            }
            Button(
                onClick = {
                    viewModel.crashReport.value?.let {
                        try {
                            val fileRecord =
                                OmsFileProvider.create(context, "crash_report.txt", false)
                            Files.write(fileRecord!!.path, it.toByteArray(StandardCharsets.UTF_8))
                            val intentSend = viewModel.getIntent(Intent.ACTION_SEND)
                            intentSend.putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
                            intentSend.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            intentSend.putExtra(
                                Intent.EXTRA_TEXT,
                                "The report file has been attached. Please use this email to provide additional feedback (this is optional)."
                            )
                            try {
                                activity.startActivity(intentSend)
                                activity.finish()
                            } catch (_: ActivityNotFoundException) {
                                try {
                                    //email without attachment
                                    val intentSendTo = viewModel.getIntent(Intent.ACTION_SENDTO)
                                    intentSendTo.putExtra(Intent.EXTRA_TEXT, it)
                                    activity.startActivity(intentSendTo)
                                    activity.finish()
                                } catch (_: ActivityNotFoundException) {
                                    coroutineScope.launch {
                                        val result =
                                            snackbarHostState?.showSnackbar("Could not send email")
                                        if (result == SnackbarResult.Dismissed) activity.finish()
                                    }
                                }
                            }
                        } catch (ex: IOException) {
                            ex.printStackTrace()
                            activity.finish()
                        }
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 8.dp)
            ) {
                Text(text = stringResource(id = R.string.btn_send))
            }
        }

        LaunchedEffect(Unit) { viewModel.displayCrashReport() }

        LaunchedEffect(viewModel.crashReport.value) {
            viewModel.crashReport.value?.let {
                try {
                    val fileRecord = OmsFileProvider.create(context, "crash_report.txt", false)
                    Files.write(fileRecord!!.path, it.toByteArray(StandardCharsets.UTF_8))
                    val intentSend = viewModel.getIntent(Intent.ACTION_SEND)
                    intentSend.putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
                    intentSend.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intentSend.putExtra(
                        Intent.EXTRA_TEXT,
                        "The report file has been attached. Please use this email to provide additional feedback (this is optional)."
                    )

                    try {
                        activity.startActivity(intentSend)
                        activity.finish()
                    } catch (ex: ActivityNotFoundException) {
                        try {
                            //email without attachment
                            val intentSendTo = viewModel.getIntent(Intent.ACTION_SENDTO)
                            intentSendTo.putExtra(Intent.EXTRA_TEXT, it)
                            activity.startActivity(intentSendTo)
                            activity.finish()
                        } catch (exx: ActivityNotFoundException) {
                            coroutineScope.launch {
                                val result = snackbarHostState?.showSnackbar("Could not send email")
                                if (result == SnackbarResult.Dismissed) activity.finish()
                            }
                        }
                    }
                } catch (ex: IOException) {
                    ex.printStackTrace()
                }
            }
        }
    }
}

@Composable
fun CheckboxWithText(
    text: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Text(text = text)
    }
}


@Preview(showBackground = true)
@Composable
fun CrashReportScreenPreview() {
    CrashReportScreen(null, PaddingValues.Absolute(16.dp, 16.dp, 16.dp, 16.dp))
}


