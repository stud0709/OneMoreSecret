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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedMessage
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil
import java.nio.charset.StandardCharsets

class EncryptTextFragment : Fragment() {

    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var outputFragment: OutputFragment? = null
    private val cryptographyManager = CryptographyManager()
    private lateinit var preferences: SharedPreferences

    private val keyStoreListContainerId = View.generateViewId()
    private val outputContainerId = View.generateViewId()

    private var phraseText by mutableStateOf("")
    private var displayedText by mutableStateOf("")
    private var isEncrypted by mutableStateOf(false)
    private var isObserverAdded = false

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                Util.openUrl(R.string.encrypt_text_md_url, requireContext())
            } else {
                return false
            }
            return true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        EncryptTextScreen()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)

        val text = arguments?.getString(QRFragment.ARG_TEXT) ?: ""
        phraseText = text
        displayedText = text

        setupChildFragments()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        val ksf = childFragmentManager.findFragmentById(keyStoreListContainerId)
        val of = childFragmentManager.findFragmentById(outputContainerId)

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

        requireContext().mainExecutor.execute {
            updateOutput(phraseText)
        }
    }

    private fun setupKeyStoreObserver() {
        if (isObserverAdded) return
        keyStoreListFragment?.setRunOnStart { tracker ->
            tracker.addObserver(object : SelectionTracker.SelectionObserver<String>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()
                    updateOutput(phraseText)
                }
            })
        }
        isObserverAdded = true
    }

    private fun updateOutput(text: String) {
        val tracker = keyStoreListFragment?.getSelectionTracker()
        if (tracker != null && tracker.hasSelection()) {
            val selectedAlias = tracker.selection.first()
            encryptPhrase(text, selectedAlias)
        } else {
            setPhrase(text)
        }
    }

    private fun setPhrase(pwd: String) {
        isEncrypted = false
        displayedText = pwd
        outputFragment?.setMessage(pwd, "Unprotected phrase")
    }

    private fun encryptPhrase(phrase: String, alias: String) {
        try {
            val encrypted = MessageComposer.encodeAsOmsText(
                EncryptedMessage(
                    phrase.toByteArray(StandardCharsets.UTF_8),
                    requireNotNull(cryptographyManager.getByAlias(alias, preferences)).public,
                    RSAUtil.getRsaTransformation(preferences),
                    AESUtil.getKeyLength(preferences),
                    AESUtil.getAesTransformation(preferences)
                ).message
            )
            isEncrypted = true
            displayedText = encrypted
            outputFragment?.setMessage(encrypted, getString(R.string.encrypted_password))
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    @Composable
    private fun EncryptTextScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = displayedText,
                onValueChange = {
                    if (!isEncrypted) {
                        phraseText = it
                        displayedText = it
                        updateOutput(phraseText)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                enabled = !isEncrypted,
                label = { Text(stringResource(R.string.phrase_to_encrypt)) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = stringResource(R.string.encrypt_with))

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    factory = { context ->
                        FragmentContainerView(context).apply { id = keyStoreListContainerId }
                    },
                    update = { _ ->
                        if (childFragmentManager.findFragmentById(keyStoreListContainerId) == null) {
                            childFragmentManager.commit {
                                replace(keyStoreListContainerId, keyStoreListFragment!!, "keyStoreListFragment")
                            }
                        }
                        setupKeyStoreObserver()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.wrapContentHeight().fillMaxWidth()) {
                AndroidView(
                    factory = { context ->
                        FragmentContainerView(context).apply { id = outputContainerId }
                    },
                    update = { _ ->
                        if (childFragmentManager.findFragmentById(outputContainerId) == null) {
                            childFragmentManager.commit {
                                replace(outputContainerId, outputFragment!!, "outputFragment")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    companion object {
        private val TAG = EncryptTextFragment::class.java.simpleName
    }
}
