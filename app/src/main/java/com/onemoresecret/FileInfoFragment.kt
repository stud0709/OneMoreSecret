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
import androidx.fragment.app.Fragment
import com.onemoresecret.composable.FileInfo
import com.onemoresecret.composable.OneMoreSecretTheme

class FileInfoFragment : Fragment() {
    var fileinfo by mutableStateOf<Util.UriFileInfo?>(null)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply({
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                OneMoreSecretTheme {
                    FileInfo(
                        fileinfo
                    )
                }
            }
        })
    }
}