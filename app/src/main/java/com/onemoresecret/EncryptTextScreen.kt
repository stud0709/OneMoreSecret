package com.onemoresecret

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedMessage
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptTextScreen(
    initialText: String = "",
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferences = com.onemoresecret.OmsPreferences.get(context)
    
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
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help")
                    }
                    com.onemoresecret.composable.ScreenshotMenu()
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
