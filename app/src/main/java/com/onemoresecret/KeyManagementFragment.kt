package com.onemoresecret

import android.app.AlertDialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.databinding.FragmentKeyManagementBinding
import com.onemoresecret.databinding.FragmentKeyStoreListBinding
import java.security.KeyStoreException
import java.util.Base64

class KeyManagementFragment : Fragment() {
    private var binding: FragmentKeyManagementBinding? = null
    private val menuProvider = KeyEntryMenuProvider()
    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var outputFragment: OutputFragment? = null
    private val cryptographyManager = CryptographyManager()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentKeyManagementBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider)
        keyStoreListFragment = binding!!.fragmentContainerView.getFragment<KeyStoreListFragment>()
        outputFragment = binding!!.fragmentContainerView2.getFragment<OutputFragment>()
        keyStoreListFragment!!.setRunOnStart { fragmentKeyStoreListBinding: FragmentKeyStoreListBinding? ->
            keyStoreListFragment!!.selectionTracker
                .addObserver(object : SelectionTracker.SelectionObserver<String?>() {
                    override fun onSelectionChanged() {
                        super.onSelectionChanged()
                        requireActivity().invalidateOptionsMenu()
                        if (keyStoreListFragment!!.selectionTracker.hasSelection()) {
                            val alias: String = this@KeyManagementFragment.selectedAlias
                            outputFragment!!.setMessage(
                                getPublicKeyMessage(alias),
                                String.format(
                                    getString(R.string.share_public_key_title),
                                    alias
                                )
                            )
                        } else {
                            outputFragment!!.setMessage(null, null)
                        }
                    }
                })
        }
    }

    private val selectedAlias: String
        get() = keyStoreListFragment!!.selectionTracker.selection.iterator().next().toString()

    private fun getPublicKeyMessage(alias: String): String {
        try {
            val bArr = cryptographyManager.getCertificate(alias).publicKey.encoded
            return Base64.getEncoder().encodeToString(bArr)
        } catch (ex: Exception) {
            throw RuntimeException(ex)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
        binding = null
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

            if (!keyStoreListFragment!!.selectionTracker.hasSelection()) return false

            val alias: String = this@KeyManagementFragment.selectedAlias

            if (menuItem.itemId == R.id.menuItemDeleteKeyEntry) {
                AlertDialog.Builder(context)
                    .setTitle(R.string.delete_private_key)
                    .setMessage(String.format(getString(R.string.ok_to_delete), alias))
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(
                        android.R.string.ok
                    ) { dialog: DialogInterface?, which: Int ->
                        try {
                            cryptographyManager.deleteKey(alias)
                            Toast.makeText(
                                context,
                                String.format(getString(R.string.key_deleted), alias),
                                Toast.LENGTH_LONG
                            ).show()
                            keyStoreListFragment!!.onItemRemoved(alias)
                        } catch (e: KeyStoreException) {
                            e.printStackTrace()
                            Toast.makeText(
                                context,
                                if (e.message == null) String.format(
                                    requireContext().getString(R.string.operation_failed_s),
                                    e.javaClass.simpleName
                                ) else e.message,
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }.setNegativeButton(android.R.string.cancel, null).show()
            } else if (menuItem.itemId == R.id.menuItemKeyMgtHelp) {
                openUrl(R.string.key_management_md_url, requireContext())
            } else {
                return false
            }

            return true
        }

        override fun onPrepareMenu(menu: Menu) {
            menu.setGroupVisible(
                R.id.group_key_all,
                keyStoreListFragment!!.selectionTracker.hasSelection()
            )
            super.onPrepareMenu(menu)
        }
    }
}