package com.onemoresecret

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.Permissions

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

    fun isAllPermissionsGranted(
        tag: String,
        ctx: Context,
        vararg permissions: String
    ): Boolean {
        if (permissions.all { p -> ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED }) return true

        Log.d(tag, "Granted permissions:")

        permissions.forEach { p ->
            val isGranted = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
            Log.d(tag, "$p: $isGranted")
        }

        return false
    }
}
