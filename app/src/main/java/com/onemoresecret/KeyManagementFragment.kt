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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.crypto.CryptographyManager
import java.util.Base64

class KeyManagementFragment : Fragment() {
    private val menuProvider = KeyEntryMenuProvider()
    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var outputFragment: OutputFragment? = null
    private val cryptographyManager = CryptographyManager()

    // Dialog state
    private var aliasToDelete by mutableStateOf<String?>(null)

    // Selection state for menu updates
    private var hasSelection by mutableStateOf(false)
    private var isObserverAdded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        KeyManagementScreen()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        setupChildFragments()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // Remove child fragments to prevent crashes when the FragmentManager tries to restore them
        // before ComposeView has inflated the AndroidView containers
        val ksf = childFragmentManager.findFragmentById(R.id.keyStoreListContainer)
        val of = childFragmentManager.findFragmentById(R.id.outputContainer)
        
        if (ksf != null || of != null) {
            val tx = childFragmentManager.beginTransaction()
            if (ksf != null) tx.remove(ksf)
            if (of != null) tx.remove(of)
            tx.commitNowAllowingStateLoss()
        }
    }

    private fun setupChildFragments() {
        keyStoreListFragment = childFragmentManager.findFragmentByTag("keyStoreListFragment") as? KeyStoreListFragment
            ?: KeyStoreListFragment()

        outputFragment = childFragmentManager.findFragmentByTag("outputFragment") as? OutputFragment
            ?: OutputFragment()
    }

    private fun setupKeyStoreObserver() {
        if (isObserverAdded) return
        keyStoreListFragment?.setRunOnStart { tracker ->
            tracker.addObserver(object : SelectionTracker.SelectionObserver<String>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()
                    hasSelection = tracker.hasSelection()
                    requireActivity().invalidateOptionsMenu()
                    
                    if (hasSelection) {
                        val alias = getSelectedAlias()
                        if (alias != null) {
                            outputFragment?.setMessage(
                                getPublicKeyMessage(alias),
                                getString(R.string.share_public_key_title, alias)
                            )
                        }
                    } else {
                        outputFragment?.setMessage(null, null)
                    }
                }
            })
        }
        isObserverAdded = true
    }

    private fun getSelectedAlias(): String? {
        return keyStoreListFragment?.getSelectionTracker()?.selection?.firstOrNull()
    }

    private fun getPublicKeyMessage(alias: String): String {
        try {
            val keyStoreEntry = cryptographyManager.getByAlias(alias, requireActivity().getPreferences(Context.MODE_PRIVATE))
            requireNotNull(keyStoreEntry)
            return Base64.getEncoder().encodeToString(keyStoreEntry.public)
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    @Composable
    private fun KeyManagementScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { context ->
                        FragmentContainerView(context).apply {
                            id = R.id.keyStoreListContainer
                        }
                    },
                    update = { _ ->
                        if (childFragmentManager.findFragmentById(R.id.keyStoreListContainer) == null) {
                            childFragmentManager.commit {
                                replace(R.id.keyStoreListContainer, keyStoreListFragment!!, "keyStoreListFragment")
                            }
                        }
                        setupKeyStoreObserver()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Box(modifier = Modifier.wrapContentHeight()) {
                AndroidView(
                    factory = { context ->
                        FragmentContainerView(context).apply {
                            id = R.id.outputContainer
                        }
                    },
                    update = { _ ->
                        if (childFragmentManager.findFragmentById(R.id.outputContainer) == null) {
                            childFragmentManager.commit {
                                replace(R.id.outputContainer, outputFragment!!, "outputFragment")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            DeleteKeyDialog()
        }
    }

    @Composable
    private fun DeleteKeyDialog() {
        aliasToDelete?.let { alias ->
            AlertDialog(
                onDismissRequest = { aliasToDelete = null },
                title = { Text(getString(R.string.delete_private_key)) },
                text = { Text(getString(R.string.ok_to_delete, alias)) },
                icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                confirmButton = {
                    TextButton(onClick = {
                        cryptographyManager.deleteKey(alias, requireActivity().getPreferences(Context.MODE_PRIVATE))
                        Toast.makeText(context, getString(R.string.key_deleted, alias), Toast.LENGTH_LONG).show()
                        keyStoreListFragment?.onItemRemoved(alias)
                        aliasToDelete = null
                    }) {
                        Text(getString(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { aliasToDelete = null }) {
                        Text(getString(android.R.string.cancel))
                    }
                }
            )
        }
    }

    private inner class KeyEntryMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_key_management, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemNewPrivateKey) {
                NavHostFragment.findNavController(this@KeyManagementFragment)
                    .navigate(R.id.action_keyManagementFragment_to_newPrivateKeyFragment, null)
                return true
            }

            if (!hasSelection) return false

            val alias = getSelectedAlias() ?: return false

            return when (menuItem.itemId) {
                R.id.menuItemDeleteKeyEntry -> {
                    aliasToDelete = alias
                    true
                }
                R.id.menuItemKeyMgtHelp -> {
                    Util.openUrl(R.string.key_management_md_url, requireContext())
                    true
                }
                else -> false
            }
        }

        override fun onPrepareMenu(menu: Menu) {
            menu.setGroupVisible(R.id.group_key_all, hasSelection)
            super.onPrepareMenu(menu)
        }
    }
}
