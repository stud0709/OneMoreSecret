package com.onemoresecret.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentContainerView
import com.onemoresecret.R

    @Composable
    fun MainScreen() {
        val viewModel = CryptoAddressGeneratorViewModel()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.public_address),
                modifier = Modifier.align(Alignment.Start)
            )

            Text(
                text = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "WARNING: EXPERIMENTAL FEATURE!",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(id = R.string.encrypt_with)
            )

            Spacer(modifier = Modifier.height(8.dp))

            AndroidView(
                factory = { context ->
                    FragmentContainerView(context).apply {
                        id = R.id.fragmentContainerView
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            Button(
                onClick = { /* Handle backup click */ },
                modifier = Modifier.padding(vertical = 8.dp),
                enabled = viewModel.btnBackupEnabled.value
            ) {
                Text(text = "Create Backup")
            }

            AndroidView(
                factory = { context ->
                    FragmentContainerView(context).apply {
                        id = R.id.fragmentContainerView4
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }