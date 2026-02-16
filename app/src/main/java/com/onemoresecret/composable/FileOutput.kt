package com.onemoresecret.composable

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Objects

@Composable
fun FileOutputScreen(
    uri: Uri?,
    progressText: String,
    onBeforePause: (() -> Unit)? = null
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Send Button
            Button(
                onClick = {
                    onBeforePause?.invoke()
                    handleFileAction(context, uri, isViewAction = false)
                },
                enabled = uri != null,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("Send")
            }

            Spacer(modifier = Modifier.width(50.dp))

            // View Button
            Button(
                onClick = {
                    onBeforePause?.invoke()
                    handleFileAction(context, uri, isViewAction = true)
                },
                enabled = uri != null,
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Text("View")
            }
        }

        Text(
            text = progressText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun handleFileAction(context: android.content.Context, uri: Uri?, isViewAction: Boolean) {
    if (uri == null) return

    val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
    val mimeType = Objects.requireNonNullElse(
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension),
        "application/octet-stream"
    )

    val intent = Intent(if (isViewAction) Intent.ACTION_VIEW else Intent.ACTION_SEND).apply {
        if (isViewAction) {
            setDataAndType(uri, mimeType)
        } else {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    context.startActivity(intent)
}