package com.onemoresecret.composable

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HiddenTextScreen(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Text(
            text = message,
            modifier = modifier.fillMaxSize()
        )
    }
}