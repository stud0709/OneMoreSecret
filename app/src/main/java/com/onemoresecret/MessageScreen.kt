package com.onemoresecret

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.msg_fragment_plugins.MessageFragmentPlugin
import com.onemoresecret.Util.openUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(
    applicationId: Int?,
    messageData: ByteArray?,
    uri: Uri?,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val viewModel: MessageViewModel = viewModel(
        factory = MessageViewModel.Factory(applicationId, messageData, uri, activity, onNavigateBack)
    )
    val hiddenState by viewModel.hiddenState.collectAsState()
    val plugin = viewModel.plugin

    LaunchedEffect(Unit) {
        try {
            plugin?.showBiometricPromptForDecryption()
        } catch (ex: Exception) {
            Util.printStackTrace(ex)
            Toast.makeText(context, ex.message ?: ex.javaClass.name, Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.message_fragment_label), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {
                    plugin?.TopBarActions()
                    IconButton(onClick = { viewModel.toggleVisibility() }) {
                        Icon(
                            imageVector = if (hiddenState) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle Visibility"
                        )
                    }
                    IconButton(onClick = {
                        openUrl(R.string.decrypted_message_md_url, context)
                    }) {
                        Icon(imageVector = Icons.Default.Help, contentDescription = "Help")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (plugin != null) {
                Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    plugin.MessageView(hiddenState = hiddenState)
                }
                plugin.OutputView()
            }
        }
    }
}
