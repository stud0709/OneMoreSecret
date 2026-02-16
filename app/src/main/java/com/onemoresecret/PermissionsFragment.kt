package com.onemoresecret

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.onemoresecret.Util.discardBackStack
import androidx.core.content.edit
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.PermissionsScreen

class PermissionsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    PermissionsScreen(onProceed = { result ->
                        val preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
                        preferences.edit { putBoolean(PROP_PERMISSIONS_REQUESTED, true) }
                        Log.d(TAG, "Granted permissions: $result")
                        discardBackStack(this@PermissionsFragment)
                    })
                }
            }
        }
    }

    companion object {
        const val PROP_PERMISSIONS_REQUESTED: String = "permissions_requested"
        private val TAG: String = PermissionsFragment::class.java.getSimpleName()

        @JvmStatic
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
}