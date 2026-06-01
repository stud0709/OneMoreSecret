package com.onemoresecret

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import com.onemoresecret.crypto.*
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptTextScreen(
    initialText: String = "",
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
    
    val outputViewModel: OutputViewModel = viewModel(
        factory = OutputViewModel.Factory(preferences)
    )

    var phraseText by remember { mutableStateOf(initialText) }
    var displayedText by remember { mutableStateOf(initialText) }
    var isEncrypted by remember { mutableStateOf(false) }
    var selectedAlias by remember { mutableStateOf<String?>(null) }
    
    val cryptographyManager = remember { CryptographyManager() }

    val strEncryptedPassword = stringResource(R.string.encrypted_password)

    LaunchedEffect(phraseText, selectedAlias) {
        if (selectedAlias != null) {
            try {
                val keyStoreEntry = cryptographyManager.getByAlias(selectedAlias!!, preferences)
                if (keyStoreEntry != null) {
                    val encryptedMessage = EncryptedMessage(
                        phraseText.toByteArray(StandardCharsets.UTF_8),
                        keyStoreEntry.public,
                        RSAUtil.getRsaTransformation(preferences),
                        AESUtil.getKeyLength(preferences),
                        AESUtil.getAesTransformation(preferences)
                    ).message
                    val encrypted = MessageComposer.encodeAsOmsText(encryptedMessage)
                    isEncrypted = true
                    displayedText = encrypted
                    outputViewModel.setMessage(encrypted, strEncryptedPassword)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            isEncrypted = false
            displayedText = phraseText
            outputViewModel.setMessage(phraseText, "Unprotected phrase")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.encrypt_text), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {

                    IconButton(onClick = {
                        Util.openUrl(R.string.encrypt_text_md_url, context)
                    }) {
                        Icon(Icons.Default.Help, contentDescription = "Help")
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
            OutlinedTextField(
                value = displayedText,
                onValueChange = {
                    if (!isEncrypted) {
                        phraseText = it
                        displayedText = it
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                enabled = !isEncrypted,
                label = { Text(stringResource(R.string.phrase_to_encrypt)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.encrypt_with))

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                KeyStoreListScreen(
                    onSelectionChanged = { alias ->
                        selectedAlias = alias
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.wrapContentHeight().fillMaxWidth()) {
                OutputScreen(outputViewModel = outputViewModel)
            }
        }
    }
}
