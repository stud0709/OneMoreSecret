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
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.WiFiPairing

class WiFiPairingFragment : Fragment() {
    private var requestIdState by mutableStateOf("")
    private var responseCodeState by mutableStateOf("")
    private var isPairingAcceptedState by mutableStateOf(false)
    private var onConfirmState: () -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                OneMoreSecretTheme {
                    WiFiPairing(
                        requestId = requestIdState,
                        responseCode = responseCodeState,
                        isPairingAccepted = isPairingAcceptedState,
                        onConfirm = {
                            isPairingAcceptedState = true
                            onConfirmState()
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isPairingAcceptedState = false
    }

    fun setData(requestId: String, responseCode: String, onConfirm: Runnable) {
        requestIdState = requestId
        responseCodeState = responseCode
        isPairingAcceptedState = false
        onConfirmState = { onConfirm.run() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        onConfirmState = {}
    }
}
