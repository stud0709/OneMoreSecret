package com.onemoresecret

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.onemoresecret.Util.openUrl
import com.onemoresecret.composable.NewPrivateKey
import com.onemoresecret.composable.NewPrivateKeyViewModel
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.crypto.CryptographyManager
import kotlinx.coroutines.launch
import kotlin.getValue

class NewPrivateKeyFragment : Fragment() {
    private val menuProvider = PrivateKeyMenuProvider()
    private val viewModel: NewPrivateKeyViewModel by viewModels {
        NewPrivateKeyViewModel.Factory(
            requireActivity().getPreferences(Context.MODE_PRIVATE),
            CryptographyManager()
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme() {
                    NewPrivateKey(viewModel)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(
            menuProvider,
            viewLifecycleOwner,
            Lifecycle.State.RESUMED
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fragmentEvent.collect { event ->
                    event.invoke(this@NewPrivateKeyFragment)
                }
            }
        }
    }

    private inner class PrivateKeyMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.new_private_key_md_url, requireContext())
            } else {
                return false
            }
            return true
        }
    }
}