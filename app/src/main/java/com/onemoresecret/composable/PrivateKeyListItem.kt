package com.onemoresecret.composable

import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onemoresecret.R

@Composable
fun PrivateKeyListItem(alias: String, fingerprint: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_key_24),
                contentDescription = stringResource(id = R.string.key_icon),
                tint = colorResource(id = android.R.color.tertiary_text_dark),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp)
            )

            Text(
                text = alias,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.fingerprint),
                modifier = Modifier.padding(start = 40.dp, end = 8.dp)
            )

            Text(
                text = fingerprint,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

fun createPrivateKeyListItemComposeView(parent: ViewGroup): ComposeView {
    return ComposeView(parent.context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        setBackgroundResource(R.drawable.list_selector)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }
}

fun bindPrivateKeyListItem(composeView: ComposeView, alias: String, fingerprint: String) {
    composeView.setContent {
        OneMoreSecretTheme {
            PrivateKeyListItem(alias = alias, fingerprint = fingerprint)
        }
    }
}
