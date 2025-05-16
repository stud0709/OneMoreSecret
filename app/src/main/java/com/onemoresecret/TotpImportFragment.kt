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
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.AESUtil.getAesTransformationIdx
import com.onemoresecret.crypto.AESUtil.getKeyLength
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.crypto.OneTimePassword
import com.onemoresecret.crypto.RSAUtils.getRsaTransformationIdx
import com.onemoresecret.crypto.TotpUriTransfer
import com.onemoresecret.databinding.FragmentKeyStoreListBinding
import com.onemoresecret.databinding.FragmentTotpImportBinding
import java.security.interfaces.RSAPublicKey
import java.util.Objects

class TotpImportFragment : Fragment() {
    private var binding: FragmentTotpImportBinding? = null
    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var outputFragment: OutputFragment? = null
    private val cryptographyManager = CryptographyManager()
    private val menuProvider = OtpMenuProvider()
    private var preferences: SharedPreferences? = null
    private var totpFragment: TotpFragment? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTotpImportBinding.inflate(inflater, container, false)

        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider)

        keyStoreListFragment = binding!!.fragmentContainerView.getFragment<KeyStoreListFragment>()
        outputFragment = binding!!.fragmentContainerView3.getFragment<OutputFragment>()

        totpFragment = binding!!.fragmentTotp.getFragment<TotpFragment>()
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        val message = requireArguments().getByteArray("MESSAGE")

        val otp = OneTimePassword(String(message!!))

        try {
            require(otp.isValid) { "Invalid scheme or authority" }

            totpFragment!!.init(otp, this) { code: String? ->
                val hasSelection = keyStoreListFragment!!.selectionTracker.hasSelection()
                totpFragment!!.setTotpText(code)
                if (!hasSelection) outputFragment!!.setMessage(code, "One-Time Password")
            }

            keyStoreListFragment!!.setRunOnStart { fragmentKeyStoreListBinding: FragmentKeyStoreListBinding? ->
                keyStoreListFragment!!
                    .selectionTracker
                    .addObserver(object : SelectionTracker.SelectionObserver<String?>() {
                        override fun onSelectionChanged() {
                            super.onSelectionChanged()
                            if (keyStoreListFragment!!.selectionTracker.hasSelection()) {
                                val selectedAlias =
                                    keyStoreListFragment!!.selectionTracker.selection.iterator()
                                        .next()!!
                                val encrypted = encrypt(selectedAlias, message)
                                outputFragment!!.setMessage(
                                    encrypted,
                                    "Encrypted OTP Configuration"
                                )
                            } else {
                                outputFragment!!.setMessage(null, null)
                            }
                            totpFragment!!.refresh()
                        }
                    })
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(
                context,
                Objects.requireNonNullElse(ex.message, ex.javaClass.name),
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
                    (cryptographyManager.getCertificate(alias).publicKey as RSAPublicKey),
                    getRsaTransformationIdx(preferences!!),
                    getKeyLength(preferences!!),
                    getAesTransformationIdx(preferences!!)
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

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
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
        binding = null
    }

    companion object {
        private val TAG: String = TotpImportFragment::class.java.simpleName
    }
}