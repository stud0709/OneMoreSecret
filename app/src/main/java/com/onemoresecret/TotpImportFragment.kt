package com.onemoresecret

import android.content.Context
import android.content.SharedPreferences
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
import androidx.lifecycle.Observer
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.openUrl
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.KeyStoreEntry
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.crypto.OneTimePassword
import com.onemoresecret.crypto.RSAUtil
import com.onemoresecret.crypto.TotpUriTransfer
import com.onemoresecret.databinding.FragmentKeyStoreListBinding
import com.onemoresecret.databinding.FragmentTotpImportBinding
import java.util.Objects
import java.util.function.Consumer

class TotpImportFragment : Fragment() {
    private lateinit var binding: FragmentTotpImportBinding
    private lateinit var keyStoreListFragment: KeyStoreListFragment
    private lateinit var outputFragment: OutputFragment
    private val cryptographyManager = CryptographyManager()
    private val menuProvider = OtpMenuProvider()
    private lateinit var preferences: SharedPreferences
    private lateinit var totpFragment: TotpFragment

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTotpImportBinding.inflate(inflater, container, false)

        return binding.getRoot()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider)

        keyStoreListFragment = binding.fragmentContainerView.getFragment()
        outputFragment = binding.fragmentContainerView3.getFragment()

        totpFragment = binding.fragmentTotp.getFragment()
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        val message = requireArguments().getByteArray("MESSAGE")

        val otp = OneTimePassword(String(message!!))

        try {
            require(otp.valid) { "Invalid scheme or authority" }

            totpFragment.init(otp, this, Observer { code: String? ->
                val hasSelection = keyStoreListFragment.selectionTracker.hasSelection()
                totpFragment.setTotpText(code!!)
                if (!hasSelection) outputFragment.setMessage(code, "One-Time Password")
            })

            keyStoreListFragment.setRunOnStart { _: FragmentKeyStoreListBinding? ->
                keyStoreListFragment
                    .selectionTracker
                    .addObserver(object : SelectionTracker.SelectionObserver<String?>() {
                        override fun onSelectionChanged() {
                            super.onSelectionChanged()
                            if (keyStoreListFragment.selectionTracker.hasSelection()) {
                                val selectedAlias =
                                    keyStoreListFragment.selectionTracker.getSelection()
                                        .iterator().next()
                                val encrypted = encrypt(selectedAlias, message)
                                outputFragment.setMessage(
                                    encrypted,
                                    "Encrypted OTP Configuration"
                                )
                            } else {
                                outputFragment.setMessage(null, null)
                            }
                            totpFragment.refresh()
                        }
                    })
            }
        } catch (ex: Exception) {
            printStackTrace(ex)
            Toast.makeText(
                getContext(),
                Objects.requireNonNullElse<String?>(ex.message, ex.javaClass.getName()),
                Toast.LENGTH_LONG
            ).show()
            discardBackStack(this)
        }
    }

    private fun encrypt(alias: String, message: ByteArray): String {
        try {
            return encodeAsOmsText(
                TotpUriTransfer(
                    message,
                    Objects.requireNonNull<KeyStoreEntry>(
                        cryptographyManager.getByAlias(
                            alias,
                            preferences
                        )
                    ).public,
                    RSAUtil.getRsaTransformation(preferences),
                    AESUtil.getKeyLength(preferences),
                    AESUtil.getAesTransformation(preferences)
                ).message
            )
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private inner class OtpMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.totp_import_md_url, requireContext())
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

    companion object {
        private val TAG: String = TotpImportFragment::class.java.getSimpleName()
    }
}