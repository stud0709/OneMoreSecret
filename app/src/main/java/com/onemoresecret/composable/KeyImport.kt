package com.onemoresecret.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onemoresecret.R

@Composable
fun KeyImportScreen(
    alias: String,
    passphrase: String,
    fingerprint: String,
    warning: String,
    saveEnabled: Boolean,
    onAliasChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onDecrypt: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = stringResource(R.string.key_alias))
        OutlinedTextField(
            value = alias,
            onValueChange = onAliasChange,
            label = { Text(stringResource(R.string.key_alias)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text(text = stringResource(R.string.transport_passphrase_required))
        OutlinedTextField(
            value = passphrase,
            onValueChange = onPassphraseChange,
            label = { Text(stringResource(R.string.passphrase)) },
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = onDecrypt, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.decrypt))
        }

        Text(text = stringResource(R.string.fingerprint))
        Text(
            text = fingerprint,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth()
        )

        if (warning.isNotBlank()) {
            Text(
                text = warning,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = onSave,
            enabled = saveEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save))
        }
    }
}
