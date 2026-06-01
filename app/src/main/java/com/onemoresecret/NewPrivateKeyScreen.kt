package com.onemoresecret

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.composable.NewPrivateKey
import com.onemoresecret.composable.NewPrivateKeyViewModel
import com.onemoresecret.crypto.CryptographyManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPrivateKeyScreen(
    onPopBackStack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val preferences = activity?.getPreferences(Context.MODE_PRIVATE)
        ?: context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    val viewModel: NewPrivateKeyViewModel = viewModel(
        factory = NewPrivateKeyViewModel.Factory(
            preferences,
            CryptographyManager()
        )
    )

    val strBackupFile = stringResource(R.string.backup_file)

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is NewPrivateKeyViewModel.Event.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is NewPrivateKeyViewModel.Event.PopBackStack -> {
                    onPopBackStack()
                }
                is NewPrivateKeyViewModel.Event.ShareFile -> {
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, event.fileRecordUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        type = "text/html"
                    }
                    context.startActivity(
                        Intent.createChooser(
                            intent,
                            String.format(strBackupFile, event.alias)
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.new_private_key), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = {
                        Util.openUrl(R.string.new_private_key_md_url, context)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "Help"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NewPrivateKey(viewModel)
        }
    }
}
