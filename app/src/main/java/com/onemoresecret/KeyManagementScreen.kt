package com.onemoresecret

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
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
    val clipboardManager = LocalClipboardManager.current

    var selectedAlias by remember { mutableStateOf<String?>(null) }
    var aliasToDelete by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.key_management)) },
                actions = {
                    IconButton(onClick = onNavigateToNewPrivateKey) {
                        Icon(Icons.Filled.Add, contentDescription = "New Private Key")
                    }
                    if (selectedAlias != null) {
                        IconButton(onClick = { aliasToDelete = selectedAlias }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete Key")
                        }
                        if (publicKeyMessage != null) {
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(publicKeyMessage))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.copied_to_clipboard),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Public Key")
                            }
                            IconButton(onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, publicKeyMessage)
                                    putExtra(
                                        Intent.EXTRA_TITLE,
                                        context.getString(R.string.share_public_key_title, selectedAlias)
                                    )
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share Public Key")
                            }
                        }
                    }
                    IconButton(onClick = { Util.openUrl(R.string.key_management_md_url, context) }) {
                        Icon(Icons.Filled.Help, contentDescription = "Help")
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
                        context.getString(R.string.key_deleted, alias),
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
