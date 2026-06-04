package com.onemoresecret

import android.Manifest
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.onemoresecret.composable.OneMoreSecretTheme


@Composable
fun PermissionsScreen(onProceed: () -> Unit) {
    val context = LocalContext.current

    OneMoreSecretTheme {
        Permissions(onProceed = { result ->
            val activity = context as? Activity
            val preferences = activity?.getPreferences(Context.MODE_PRIVATE)
            preferences?.edit { putBoolean(PermissionsScreen.PROP_PERMISSIONS_REQUESTED, true) }
            Log.d(PermissionsScreen.TAG, "Granted permissions: $result")
            onProceed()
        })
    }
}

object PermissionsScreen {
    const val PROP_PERMISSIONS_REQUESTED: String = "permissions_requested"
    val TAG: String = PermissionsScreen::class.java.simpleName
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun Permissions(
    onProceed: (Map<String, Boolean>) -> Unit
) {
    val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.CAMERA
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> onProceed(result) }
    
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Introductory text
                Text(
                    text = stringResource(id = R.string.permissions1),
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Bulleted list section
                val permissions = listOf(
                    stringResource(id = R.string.permission_text_camera_access),
                    stringResource(id = R.string.permission_text_discoverable_BT),
                    stringResource(id = R.string.permission_text_communicate_BT)
                )

                permissions.forEach { permissionText ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.Top // Bullet stays at the top of multi-line text
                    ) {
                        // Column 1: The Bullet Icon
                        Text(
                            text = "👉",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.width(32.dp) // Fixed width for the bullet column
                        )

                        // Column 2: The Permission Description
                        Text(
                            text = permissionText,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f) // Takes remaining horizontal space
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer text
                Text(
                    text = stringResource(id = R.string.permissions2),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            // Action Button
            Button(
                onClick = { launcher.launch(requiredPermissions) },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 24.dp)
            ) {
                Text(text = stringResource(id = R.string.proceed))
            }
        }
    }
}