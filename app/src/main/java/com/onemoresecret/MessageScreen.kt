package com.onemoresecret

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.SecretFormatUpgrade
import com.onemoresecret.crypto.BTCAddress
import com.onemoresecret.crypto.MessageComposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    applicationId: Int?,
    messageData: ByteArray?,
    uri: Uri?,
    onNavigateBack: () -> Unit,
    upgradePublicKey: ByteArray? = null
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val viewModel: MessageViewModel = viewModel(
        factory = MessageViewModel.Factory(applicationId, messageData, uri, activity, onNavigateBack)
    )
    val hiddenState by viewModel.hiddenState.collectAsState()
    val plugin = viewModel.plugin
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var updatedMessage by remember { mutableStateOf<UpdatedSecret?>(null) }

    LaunchedEffect(Unit) {
        try {
            plugin?.showBiometricPromptForDecryption()
        } catch (ex: Exception) {
            Util.printStackTrace(ex)
            Toast.makeText(context, ex.message ?: ex.javaClass.name, Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }

    updatedMessage?.let { result ->
        UpdatedFormatScreen(result) { updatedMessage = null }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.message_fragment_label), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {
                    plugin?.TopBarActions()
                    if (uri == null && upgradePublicKey != null && messageData != null && SecretFormatUpgrade.supports(applicationId)) {
                        TextButton(enabled = !updating, onClick = {
                            updating = true
                            scope.launch {
                                val body = messageData.copyOf()
                                try {
                                    updatedMessage = withContext(Dispatchers.IO) {
                                        UpdatedSecret(
                                            encryptedMessage = SecretFormatUpgrade.encrypt(
                                                upgradePublicKey, requireNotNull(applicationId), body
                                            ),
                                            bitcoinAddress = if (applicationId == MessageComposer.APPLICATION_BITCOIN_ADDRESS) {
                                                BTCAddress.addressFromWif(body)
                                            } else null
                                        )
                                    }
                                } catch (ex: CancellationException) { throw ex }
                                catch (ex: Exception) { Toast.makeText(context, ex.message, Toast.LENGTH_LONG).show() }
                                finally { body.fill(0); updating = false }
                            }
                        }) { Text(stringResource(R.string.update_backup_format)) }
                    }
                    if (plugin?.showVisibilityButton == true) {
                        IconButton(onClick = { viewModel.toggleVisibility() }) {
                            Icon(
                                imageVector = if (hiddenState) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility"
                            )
                        }
                    }
                    IconButton(onClick = {
                        openUrl(R.string.decrypted_message_md_url, context)
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Help, contentDescription = "Help")
                    }
                    com.onemoresecret.composable.ScreenshotMenu()
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (plugin != null) {
                Column(modifier = Modifier.padding(8.dp).fillMaxWidth().weight(1f)) {
                    plugin.MessageView(hiddenState = hiddenState)
                }
                plugin.OutputView()
            }
        }
    }
}
