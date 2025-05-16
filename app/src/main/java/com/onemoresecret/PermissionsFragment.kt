package com.onemoresecret

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.databinding.FragmentPermissionsBinding
import java.util.Arrays
import androidx.core.content.edit

class PermissionsFragment : Fragment() {
    var binding: FragmentPermissionsBinding? = null

    var activityResultLauncher: ActivityResultLauncher<Array<String>>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        preferences.edit() { putBoolean(PROP_PERMISSIONS_REQUESTED, true) }
        activityResultLauncher = registerForActivityResult(
            RequestMultiplePermissions()
        ) { result: Map<String, Boolean> ->
            Log.d(TAG, String.format("Granted permissions: %s", result))
            discardBackStack(this@PermissionsFragment)
        }

        //request all app permissions
        binding!!.btnProceed.setOnClickListener { v: View? ->
            activityResultLauncher!!.launch(
                REQUIRED_PERMISSIONS
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    companion object {
        const val PROP_PERMISSIONS_REQUESTED: String = "permissions_requested"
        private val TAG: String = PermissionsFragment::class.java.simpleName

        val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.CAMERA
        )

        fun isAllPermissionsGranted(
            tag: String?,
            ctx: Context,
            vararg permissions: String
        ): Boolean {
            if (Arrays.stream(permissions).allMatch { p: String? ->
                    ctx.checkSelfPermission(
                        p!!
                    ) == PackageManager.PERMISSION_GRANTED
                }) return true

            Log.d(tag, "Granted permissions:")

            Arrays.stream(permissions).forEach { p: String ->
                val check = ContextCompat.checkSelfPermission(ctx, p)
                Log.d(
                    tag,
                    p + ": " + (check == PackageManager.PERMISSION_GRANTED) + " (" + check + ")"
                )
            }

            return false
        }
    }
}