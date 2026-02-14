package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import com.onemoresecret.composable.KeyRequestPairingContent
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KeyRequestPairingFragment : FragmentWithNotificationBeforePause() {
    var replyState by mutableStateOf<ByteArray?>(null)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                OneMoreSecretTheme {
                    KeyRequestPairingContent(
                        reply = replyState,
                        onSendKeyClicked = ::sendKey
                    )
                }
            }
        }
    }

    private fun sendKey() {
        beforePause?.run()

        val activity = requireActivity() as MainActivity

        replyState?.let { replyData ->
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                activity.sendReplyViaSocket(replyData, true)
            }
        }

        Util.discardBackStack(this)
    }

    override fun setBeforePause(r: Runnable?) {
        //not necessary
    }

    companion object {
        private val TAG = KeyRequestPairingFragment::class.simpleName
    }
}