package com.onemoresecret.composable

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.onemoresecret.R

@Composable
fun Oms4WebUnlockScreen(message: String?) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, message) {
        val activity = context as? ComponentActivity
        val menuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_oms4web_unlock, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.setGroupVisible(R.id.menuGroupOms4webAll, message != null)
                super.onPrepareMenu(menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (message == null) return true

                return when (menuItem.itemId) {
                    R.id.menuItemOms4webCopy -> {
                        val clipboardManager =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = ClipData.newPlainText("oneMoreSecret", message)
                        val persistableBundle = PersistableBundle().apply {
                            putBoolean("android.content.extra.IS_SENSITIVE", true)
                        }
                        clipData.description.extras = persistableBundle
                        clipboardManager.setPrimaryClip(clipData)
                        true
                    }

                    else -> false
                }
            }
        }

        activity?.addMenuProvider(
            menuProvider,
            lifecycleOwner,
            Lifecycle.State.RESUMED
        )

        onDispose {
            activity?.removeMenuProvider(menuProvider)
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.material3.Button(onClick = {
            if (message != null) {
                val encodedData = Uri.encode(message)
                val url = context.getString(R.string.oms4web_callback_url).format(encodedData)
                val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                (context as? ComponentActivity)?.finish()
            }
        }) {
            androidx.compose.material3.Text("Unlock")
        }
    }
}
