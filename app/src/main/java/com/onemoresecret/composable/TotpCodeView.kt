package com.onemoresecret.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TotpCodeView(
    code: String,
    remaining: String,
    modifier: Modifier = Modifier,
    nameIssuer: String? = null,
    hiddenState: Boolean = false
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!nameIssuer.isNullOrEmpty()) {
            Text(
                text = nameIssuer,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 64.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = if (hiddenState) "●".repeat(code.length) else code,
                style = MaterialTheme.typography.displaySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            if (remaining.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    modifier = Modifier.alignByBaseline(),
                    text = remaining,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}
