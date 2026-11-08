package com.onemoresecret

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import com.onemoresecret.composable.ScreenshotMenu
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.UUID

internal data class UpdatedSecret(val encryptedMessage: String, val bitcoinAddress: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UpdatedFormatScreen(result: UpdatedSecret, onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = OmsPreferences.get(context)
    val omsState = LocalOmsState.current
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.updated_format_message)
    val backupFileTitle = stringResource(R.string.backup_file)
    val outputViewModel: OutputViewModel = viewModel(
        key = "updated-format-output",
        factory = OutputViewModel.Factory(preferences)
    )
    var creatingBackup by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)
    DisposableEffect(outputViewModel, result.encryptedMessage, title) {
        outputViewModel.setMessage(result.encryptedMessage, title)
        onDispose { outputViewModel.setMessage(null, "") }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            },
            actions = { ScreenshotMenu() }
        )
    }) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.updated_format_message_description))
            OutlinedTextField(
                value = result.encryptedMessage,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            result.bitcoinAddress?.let { address ->
                TextButton(enabled = !creatingBackup, onClick = {
                    creatingBackup = true
                    scope.launch {
                        try {
                            val record = withContext(Dispatchers.IO) {
                                val html = BitcoinBackupDocument.createHtml(preferences, address, result.encryptedMessage)
                                val file = OmsFileProvider.create(context, "${address}_${UUID.randomUUID()}_backup.html", true)
                                Files.write(requireNotNull(file.path), html.toByteArray(Charsets.UTF_8))
                                file
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/html"
                                putExtra(Intent.EXTRA_STREAM, record.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            omsState.isAutoLockDisarmed = true
                            context.startActivity(Intent.createChooser(intent, String.format(backupFileTitle, address)))
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            omsState.isAutoLockDisarmed = false
                            Toast.makeText(context, ex.message, Toast.LENGTH_LONG).show()
                        } finally {
                            creatingBackup = false
                        }
                    }
                }) { Text(stringResource(R.string.create_backup)) }
            }
            OutputScreen(outputViewModel = outputViewModel)
        }
    }
}
