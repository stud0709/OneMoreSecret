package com.onemoresecret

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import com.onemoresecret.Util.openUrl
import androidx.fragment.app.viewModels
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.PinSetup
import com.onemoresecret.composable.PinSetupViewModel

class PinSetupFragment : Fragment() {
    private val menuProvider = PinMenuProvider()
    private val viewModel: PinSetupViewModel by viewModels {
        PinSetupViewModel.Factory(requireActivity().getPreferences(Context.MODE_PRIVATE), {
            Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            val viewModel: PinSetupViewModel = viewModel

            setContent {
                OneMoreSecretTheme {
                    PinSetup(viewModel = viewModel)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider);
    }

    private inner class PinMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.pin_setup_md_url, requireContext())
            } else {
                return false
            }

            return true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
    }
}