package com.onemoresecret

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.onemoresecret.composable.FileOutputScreen
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause

class FileOutputFragment : FragmentWithNotificationBeforePause() {
    var uri: Uri? by mutableStateOf(null)
    var progressText by mutableStateOf("")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    FileOutputScreen(
                        uri = uri,
                        progressText = progressText
                    )
                }
            }
        }
    }
}