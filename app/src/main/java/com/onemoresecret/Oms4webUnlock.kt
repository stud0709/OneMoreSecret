package com.onemoresecret

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PersistableBundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import com.onemoresecret.databinding.FragmentOms4webUnlockBinding
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause
import androidx.core.net.toUri

class Oms4webUnlock : FragmentWithNotificationBeforePause() {
    private var binding: FragmentOms4webUnlockBinding? = null
    private val menuProvider: Oms4webMenuProvider = Oms4webMenuProvider()
    private var copyValue: (()->Unit) = {}
    private var message: String? = null
    private var clipboardManager: ClipboardManager? = null

    fun setMessage(message: String) {
        this.message = message
        binding?.btnUnlock?.isEnabled = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentOms4webUnlockBinding.inflate(inflater, container, false)
        return binding!!.getRoot()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        clipboardManager =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        copyValue = {
            val extraIsSensitive =
                "android.content.extra.IS_SENSITIVE" /* replace with  ClipDescription.EXTRA_IS_SENSITIVE for API Level 33+ */
            val clipData = ClipData.newPlainText("oneMoreSecret", message)
            val persistableBundle = PersistableBundle()
            persistableBundle.putBoolean(extraIsSensitive, true)
            clipData.description.extras = persistableBundle
            clipboardManager!!.setPrimaryClip(clipData)
        }

        binding?.btnUnlock?.setOnClickListener {
            val encodedData = Uri.encode(message)
            val url = requireContext().getString(R.string.oms4web_callback_url).format(encodedData)
            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            requireContext().startActivity(intent)
            beforePause?.run()
            requireActivity().finish()
        }

        requireActivity().addMenuProvider(menuProvider)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
        copyValue = {}
        binding = null
    }

    companion object {
        const val TAG = "Oms4webUnlock"
    }

    inner class Oms4webMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_oms4web_unlock, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            menu.setGroupVisible(R.id.menuGroupOms4webAll, message != null)
            super.onPrepareMenu(menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (!isAdded || context == null) return true //https://github.com/stud0709/OneMoreSecret/issues/10

            if (menuItem.itemId == R.id.menuItemOms4webCopy) {
                copyValue()
            } else {
                return false
            }

            return true
        }
    }
}