package com.onemoresecret.composable

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onemoresecret.R

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
    Column(
        modifier = Modifier
            .fillMaxSize()
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