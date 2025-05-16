package com.onemoresecret.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onemoresecret.R
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PinEntryScreen(onSuccess: (() -> Unit)? = null, onCancel: (() -> Unit)? = null, onPanic: (() -> Unit)? = null, onDismiss: () -> Unit) {
    val viewModel: PinEntryViewModel = viewModel()

    viewModel.configure(onSuccess, onCancel, onPanic, onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(id = R.string.please_enter_your_pin),
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = viewModel.pinText.value,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        val buttons = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("0", "⌫", "🔓")
        )

        buttons.forEach { row ->
            Row(
                modifier = Modifier.padding(4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                row.forEach { label ->
                    Button(
                        onClick = {
                            when (label) {
                                "⌫" -> if (viewModel.pinText.value.isNotEmpty()) viewModel.pinText.value = viewModel.pinText.value.dropLast(1)
                                "🔓" -> { viewModel.tryUnlock() }
                                else -> if (viewModel.pinText.value.length < 4) viewModel.pinText.value += label
                            }
                        },
                        modifier = Modifier
                            .size(50.dp)
                            .padding(4.dp)
                    ) {
                        if (label == "⌫" || label == "🔓") {
                            Icon(
                                painter = painterResource(
                                    id = if (label == "⌫") R.drawable.baseline_backspace_24 else R.drawable.baseline_lock_open_24
                                ),
                                contentDescription = null
                            )
                        } else {
                            Text(text = label)
                        }
                    }
                }
            }
        }
    }
}

