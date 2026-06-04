package com.onemoresecret

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import com.onemoresecret.crypto.CryptographyManager
import java.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyManagementScreen(
    onNavigateToNewPrivateKey: () -> Unit
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
    val cryptographyManager = remember { CryptographyManager() }
    val keyDeleted = stringResource(R.string.key_deleted)

    var selectedAlias by remember { mutableStateOf<String?>(null) }
    var aliasToDelete by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    val outputViewModel: OutputViewModel = viewModel(
        factory = OutputViewModel.Factory(preferences)
    )

    val strSharePublicKeyTitle = stringResource(R.string.share_public_key_title, selectedAlias ?: "")

    val publicKeyMessage = remember(selectedAlias, refreshTrigger) {
        selectedAlias?.let { alias ->
            try {
                val keyStoreEntry = cryptographyManager.getByAlias(alias, preferences)
                if (keyStoreEntry != null) {
                    Base64.getEncoder().encodeToString(keyStoreEntry.public)
                } else null
            } catch (ex: Exception) {
                ex.printStackTrace()
                null
            }
        }
    }

    LaunchedEffect(publicKeyMessage) {
        if (publicKeyMessage != null) {
            outputViewModel.setMessage(publicKeyMessage, strSharePublicKeyTitle)
        } else {
            outputViewModel.setMessage(null, "")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.key_management), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = onNavigateToNewPrivateKey) {
                        Icon(Icons.Filled.Add, contentDescription = "New Private Key")
                    }
                    if (selectedAlias != null) {
                        IconButton(onClick = { aliasToDelete = selectedAlias }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Key")
                        }
                    }

                    IconButton(onClick = { Util.openUrl(R.string.key_management_md_url, context) }) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                key(refreshTrigger) {
                    KeyStoreListScreen(
                        onSelectionChanged = { alias ->
                            selectedAlias = alias
                        }
                    )
                }
            }

            if (publicKeyMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = publicKeyMessage,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.public_key)) },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutputScreen(outputViewModel = outputViewModel)
            }
        }
    }

    aliasToDelete?.let { alias ->
        AlertDialog(
            onDismissRequest = { aliasToDelete = null },
            title = { Text(stringResource(R.string.delete_private_key)) },
            text = { Text(stringResource(R.string.ok_to_delete, alias)) },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            confirmButton = {
                TextButton(onClick = {
                    cryptographyManager.deleteKey(alias, preferences)
                    Toast.makeText(
                        context,
                        keyDeleted.format( alias),
                        Toast.LENGTH_LONG
                    ).show()
                    selectedAlias = null
                    aliasToDelete = null
                    refreshTrigger++
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { aliasToDelete = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
