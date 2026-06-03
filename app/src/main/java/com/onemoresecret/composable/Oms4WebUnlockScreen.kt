package com.onemoresecret.composable

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.onemoresecret.R

@Composable
fun Oms4WebUnlockScreen(message: String?) {
    val context = LocalContext.current
    val strOms4webCallbackUrl = androidx.compose.ui.res.stringResource(R.string.oms4web_callback_url)

    Oms4webUnlock(
        message = message,
        onUnlock = {
            if (message != null) {
                val encodedData = Uri.encode(message)
                val url = strOms4webCallbackUrl.format(encodedData)
                val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                (context as? ComponentActivity)?.finish()
            }
        }
    )
}
