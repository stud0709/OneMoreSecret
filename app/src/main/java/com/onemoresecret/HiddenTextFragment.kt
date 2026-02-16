package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.onemoresecret.composable.HiddenTextScreen
import com.onemoresecret.composable.OneMoreSecretTheme

class HiddenTextFragment : Fragment() {
    private var message by mutableStateOf("")

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        message = getString(R.string.hidden_text)
        return ComposeView(requireContext()).apply {
            setContent {
                OneMoreSecretTheme {
                HiddenTextScreen(message = message)
                }
            }
        }
    }

    fun setText(text: String) {
        message = text
    }
}