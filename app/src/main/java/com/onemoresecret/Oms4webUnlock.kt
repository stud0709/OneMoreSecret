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
import androidx.compose.runtime.getValue
import androidx.core.view.MenuProvider
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause
import androidx.core.net.toUri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.onemoresecret.composable.Oms4webUnlockContent
import com.onemoresecret.composable.OneMoreSecretTheme

class Oms4webUnlock : FragmentWithNotificationBeforePause() {
    private val menuProvider: Oms4webMenuProvider = Oms4webMenuProvider()
    private var messageState by mutableStateOf<String?>(null)

    fun setMessage(message: String) {
        this.messageState = message
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            // Cleans up the composition when the fragment view is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    Oms4webUnlockContent(
                        message = messageState,
                        onUnlock = { handleUnlock() }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner)
    }

    private fun handleUnlock() {
        val msg = messageState ?: return
        val encodedData = Uri.encode(msg)
        val url = requireContext().getString(R.string.oms4web_callback_url).format(encodedData)
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        requireContext().startActivity(intent)
        beforePause?.run()
        requireActivity().finish()
    }

    private fun copyValue() {
        val msg = messageState ?: return
        val clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("oneMoreSecret", msg)
        val persistableBundle = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipData.description.extras = persistableBundle
        clipboardManager.setPrimaryClip(clipData)
    }

    companion object {
        const val TAG = "Oms4webUnlock"
    }

    inner class Oms4webMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_oms4web_unlock, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            menu.setGroupVisible(R.id.menuGroupOms4webAll, messageState != null)
            super.onPrepareMenu(menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (!isAdded || context == null) return true //https://github.com/stud0709/OneMoreSecret/issues/10

            return when (menuItem.itemId) {
                R.id.menuItemOms4webCopy -> {
                    copyValue()
                    true
                }
                else -> false
            }
        }
    }
}