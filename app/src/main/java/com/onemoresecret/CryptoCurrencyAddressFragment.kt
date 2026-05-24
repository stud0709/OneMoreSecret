package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.onemoresecret.composable.OneMoreSecretTheme

class CryptoCurrencyAddressFragment : Fragment() {

    private var address by mutableStateOf("")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        CryptoCurrencyAddressScreen()
                    }
                }
            }
        }
    }

    fun setValue(publicAddress: String?) {
        address = publicAddress ?: ""
    }

    @Composable
    private fun CryptoCurrencyAddressScreen() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.public_address),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = address.chunked(4).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
