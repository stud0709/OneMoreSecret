package com.onemoresecret

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.Util.openUrl
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.PinSetup
import com.onemoresecret.composable.PinSetupViewModel

@Composable
fun PinSetupScreen(
    onPopBackStack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context, lifecycleOwner) {
        val menuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_help, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.menuItemHelp) {
                    openUrl(R.string.pin_setup_md_url, context)
                    return true
                }
                return false
            }
        }

        val activity = context as? ComponentActivity
        activity?.addMenuProvider(menuProvider, lifecycleOwner, Lifecycle.State.RESUMED)

        onDispose {
            activity?.removeMenuProvider(menuProvider)
        }
    }

    val sharedPreferences = (context as? ComponentActivity)?.getPreferences(Context.MODE_PRIVATE)
        ?: context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    val viewModel: PinSetupViewModel = viewModel(
        factory = PinSetupViewModel.Factory(sharedPreferences) {
            Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show()
            onPopBackStack()
        }
    )

    OneMoreSecretTheme {
        PinSetup(viewModel = viewModel)
    }
}
