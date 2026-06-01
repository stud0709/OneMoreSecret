package com.onemoresecret

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.onemoresecret.composable.FileInfo
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedFile
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileEncryptionScreen(
    uri: Uri,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
    val cryptographyManager = remember { CryptographyManager() }
    val coroutineScope = rememberCoroutineScope()

    var fileInfo by remember { mutableStateOf<Util.UriFileInfo?>(null) }
    var encryptionRunning by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var selectedAlias by remember { mutableStateOf<String?>(null) }
    var lastProgressPrc by remember { mutableIntStateOf(-1) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val info = Util.getFileInfo(context, uri)
            withContext(Dispatchers.Main) {
                fileInfo = info
            }
        }
    }
    
    val isEncryptEnabled = selectedAlias != null
    
    fun updateProgress(value: Int?) {
        if (value != null) {
            val fileInfoVal = fileInfo ?: return
            val progressPrc = (value.toDouble() / fileInfoVal.fileSize.toDouble() * 100.0).toInt()
            if (lastProgressPrc == progressPrc) return

            lastProgressPrc = progressPrc
            progressText = if (lastProgressPrc == 100) {
                context.getString(R.string.done)
            } else {
                String.format(Locale.getDefault(), context.getString(R.string.working_prc), lastProgressPrc)
            }
        } else {
            progressText = ""
        }
    }

    val encrypt = {
        if (encryptionRunning) {
            // Cancel encryption
            encryptionRunning = false
        } else if (selectedAlias != null && fileInfo != null) {
            val fileInfoVal = fileInfo!!
            val alias = selectedAlias!!

            encryptionRunning = true
            lastProgressPrc = -1
            progressText = ""

            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val fileRecord = OmsFileProvider.create(
                        context,
                        fileInfoVal.filename + "." + MessageComposer.OMS_FILE_TYPE,
                        true
                    )

                    EncryptedFile.create(
                        requireNotNull(context.contentResolver.openInputStream(uri)),
                        fileRecord.path!!.toFile(),
                        requireNotNull(cryptographyManager.getByAlias(alias, preferences)).public,
                        RSAUtil.getRsaTransformation(preferences),
                        AESUtil.getKeyLength(preferences),
                        AESUtil.getAesTransformation(preferences),
                        { !encryptionRunning },
                        { value -> 
                            coroutineScope.launch(Dispatchers.Main) {
                                updateProgress(value)
                            }
                        }
                    )

                    withContext(Dispatchers.Main) {
                        if (encryptionRunning) {
                            updateProgress(fileInfoVal.fileSize.toInt()) // 100%
                            
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }

                            context.startActivity(intent)
                            onNavigateBack()
                        } else {
                            // operation has been cancelled
                            Files.delete(fileRecord.path)
                            Toast.makeText(
                                context,
                                R.string.operation_has_been_cancelled,
                                Toast.LENGTH_SHORT
                            ).show()
                            updateProgress(null)
                        }
                        encryptionRunning = false
                    }
                } catch (ex: Exception) {
                    Util.printStackTrace(ex)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            String.format("%s: %s", ex.javaClass.simpleName, ex.message),
                            Toast.LENGTH_LONG
                        ).show()
                        encryptionRunning = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.encrypt_file), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { Util.openUrl(R.string.encrypt_file_md_url, context) }) {
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            fileInfo?.let { FileInfo(it) }

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.encrypt_with))

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                KeyStoreListScreen(
                    onSelectionChanged = { alias ->
                        selectedAlias = alias
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = encrypt,
                enabled = isEncryptEnabled || encryptionRunning
            ) {
                Text(text = if (encryptionRunning) stringResource(R.string.cancel) else stringResource(R.string.encrypt))
            }

            if (progressText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = progressText)
            }
        }
    }
}
