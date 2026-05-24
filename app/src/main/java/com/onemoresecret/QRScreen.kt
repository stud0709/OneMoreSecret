package com.onemoresecret

import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun QRScreen(
    onPreviewViewCreated: (PreviewView) -> Unit,
    showPairingIndicator: Boolean,
    remainingCodes: String,
    recentEntries: List<RecentEntry>,
    onRecentEntryClicked: (RecentEntry) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Camera Preview Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        onPreviewViewCreated(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (showPairingIndicator) {
                Image(
                    painter = painterResource(id = R.drawable.leak_add),
                    contentDescription = "Wi-Fi Pairing Active",
                    modifier = Modifier
                        .size(128.dp)
                        .align(Alignment.Center)
                )
            }
        }

        // Information Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.remaining_codes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = remainingCodes,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            if (recentEntries.isNotEmpty()) {
                Text(
                    text = stringResource(id = R.string.recent),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentEntries) { entry ->
                        androidx.compose.material3.OutlinedButton(
                            onClick = { onRecentEntryClicked(entry) }
                        ) {
                            androidx.compose.material3.Icon(
                                painter = painterResource(id = entry.drawableId),
                                contentDescription = "Recent Message"
                            )
                        }
                    }
                }
            }

            // Banners that require HTML link handling
            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        text = context.getText(R.string.qr_banner)
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            )

            AndroidView(
                factory = { context ->
                    TextView(context).apply {
                        text = context.getText(R.string.qr_banner1)
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                },
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            )
        }
    }
}
