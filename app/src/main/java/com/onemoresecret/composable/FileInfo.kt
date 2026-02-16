package com.onemoresecret.composable

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onemoresecret.R
import java.util.Locale

@Composable
fun FileInfo(fileInfo: com.onemoresecret.Util.UriFileInfo?) {
    Surface(
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Filename Label
            Text(
                text = stringResource(id = R.string.filename),
                style = MaterialTheme.typography.labelMedium
            )
            // Filename Value
            Text(
                text = fileInfo?.filename ?: "(filename)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            // Size Row
            Row {
                Text(text = stringResource(id = R.string.size))
                Text(
                    text = String.format(Locale.getDefault(), " %.3f KB", (fileInfo?.fileSize ?: 0) / 1024.0),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}