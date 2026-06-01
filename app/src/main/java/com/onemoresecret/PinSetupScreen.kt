package com.onemoresecret

import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.Util.openUrl
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.PinSetup
import com.onemoresecret.composable.PinSetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onPopBackStack: () -> Unit
) {
    val context = LocalContext.current

    val sharedPreferences = (context as? ComponentActivity)?.getPreferences(Context.MODE_PRIVATE)
        ?: context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    val viewModel: PinSetupViewModel = viewModel(
        factory = PinSetupViewModel.Factory(sharedPreferences) {
            Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show()
            onPopBackStack()
        }
    )

    OneMoreSecretTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.pin_setup), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onPopBackStack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate up"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { openUrl(R.string.pin_setup_md_url, context) }) {
                            Icon(
                                imageVector = Icons.Default.Help,
                                contentDescription = stringResource(R.string.help)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                PinSetup(viewModel = viewModel)
            }
        }
    }
}
