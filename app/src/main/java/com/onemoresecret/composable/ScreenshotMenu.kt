package com.onemoresecret.composable

import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.R

@Composable
fun ScreenshotMenu() {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return
    var expanded by remember { mutableStateOf(false) }
    var allowScreenshots by remember { 
        mutableStateOf(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0) 
    }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.allow_screenshots)) },
                onClick = {
                    allowScreenshots = !allowScreenshots
                    if (allowScreenshots) {
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    expanded = false
                },
                trailingIcon = {
                    if (allowScreenshots) {
                        Icon(Icons.Default.Check, contentDescription = null)
                    }
                }
            )
        }
    }
}
