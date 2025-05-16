package com.onemoresecret

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.AESUtil.getAesTransformationIdx
import com.onemoresecret.crypto.AESUtil.getKeyLength
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedMessage
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.crypto.RSAUtils.getRsaTransformationIdx
import com.onemoresecret.databinding.FragmentEncryptTextBinding
import com.onemoresecret.databinding.FragmentKeyStoreListBinding
import java.nio.charset.StandardCharsets
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

class EncryptTextFragment : Fragment() {
    private var binding: FragmentEncryptTextBinding? = null
    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var outputFragment: OutputFragment? = null
    private val cryptographyManager = CryptographyManager()
    private var encryptPhrase: Consumer<String>? = null
    private var setPhrase: Runnable? = null
    private val textChangeListenerActive = AtomicBoolean(true)
    private var preferences: SharedPreferences? = null
    private val menuProvider = EncMenuProvider()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentEncryptTextBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        keyStoreListFragment = binding!!.fragmentContainerView.getFragment<KeyStoreListFragment>()
        outputFragment = binding!!.fragmentContainerView5.getFragment<OutputFragment>()
        requireActivity().addMenuProvider(menuProvider)

        //based on pre-launch test
        //java.lang.IllegalStateException: Fragment EncryptTextFragment does not have any arguments.
        val text = if (arguments == null) "" else requireArguments().getString(QRFragment.ARG_TEXT)

        binding!!.editTextPhrase.setText(text)

        setPhrase = getSetPhrase(text)
        encryptPhrase = getEncryptPhrase(text!!)

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
                            if (encryptPhrase != null) encryptPhrase!!.accept(selectedAlias)
                        } else {
                            if (setPhrase != null) setPhrase!!.run()
                        }
                    }
                })
        }


        binding!!.editTextPhrase.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                if (!textChangeListenerActive.get()) return

                setPhrase = getSetPhrase(s.toString())
                encryptPhrase = getEncryptPhrase(s.toString())
            }
        })
    }

    private fun getSetPhrase(pwd: String?): Runnable {
        return Runnable {
            textChangeListenerActive.set(false)
            binding!!.editTextPhrase.setText(pwd)
            textChangeListenerActive.set(true)

            binding!!.editTextPhrase.isEnabled = true
            outputFragment!!.setMessage(pwd, "Unprotected phrase")
        }
    }

    private fun getEncryptPhrase(phrase: String): Consumer<String> {
        return Consumer { alias: String? ->
            try {
                val encrypted = encodeAsOmsText(
                    EncryptedMessage(
                        phrase.toByteArray(StandardCharsets.UTF_8),
                        (cryptographyManager.getCertificate(alias).publicKey as RSAPublicKey),
                        getRsaTransformationIdx(preferences!!),
                        getKeyLength(preferences!!),
                        getAesTransformationIdx(preferences!!)
                    ).message
                )

                textChangeListenerActive.set(false)
                binding!!.editTextPhrase.setText(encrypted)
                textChangeListenerActive.set(true)

                binding!!.editTextPhrase.isEnabled = false
                outputFragment!!.setMessage(encrypted, getString(R.string.encrypted_password))
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
        binding = null
    }

    private inner class EncMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.encrypt_text_md_url, requireContext())
            } else {
                return false
            }

            return true
        }
    }
}