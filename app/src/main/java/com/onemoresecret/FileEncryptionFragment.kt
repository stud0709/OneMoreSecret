package com.onemoresecret

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedFile
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil
import com.onemoresecret.composable.OneMoreSecretTheme
import java.nio.file.Files
import java.util.Locale

class FileEncryptionFragment : Fragment() {

    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var fileInfoFragment: FileInfoFragment? = null
    private val cryptographyManager = CryptographyManager()
    private lateinit var preferences: SharedPreferences
    private var uri: Uri? = null
    private var fileInfo: Util.UriFileInfo? = null

    private var encryptionRunning by mutableStateOf(false)
    private var progressText by mutableStateOf("")
    private var isEncryptEnabled by mutableStateOf(false)
    private var lastProgressPrc = -1
    private var navBackIfPaused = false

    private var isObserverAdded = false

    private val fileInfoContainerId = View.generateViewId()
    private val keyStoreListContainerId = View.generateViewId()

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                Util.openUrl(R.string.encrypt_file_md_url, requireContext())
            } else {
                return false
            }
            return true
        }
    }

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
                        FileEncryptionScreen()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        uri = requireArguments().getParcelable(QRFragment.ARG_URI)
        fileInfo = Util.getFileInfo(requireContext(), uri!!)

        setupChildFragments()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        val ksf = childFragmentManager.findFragmentById(keyStoreListContainerId)
        val fif = childFragmentManager.findFragmentById(fileInfoContainerId)

        if (ksf != null || fif != null) {
            val tx = childFragmentManager.beginTransaction()
            if (ksf != null) tx.remove(ksf)
            if (fif != null) tx.remove(fif)
            tx.commitNowAllowingStateLoss()
        }
    }

    private fun setupChildFragments() {
        keyStoreListFragment = childFragmentManager.findFragmentByTag("keyStoreListFragment") as? KeyStoreListFragment
            ?: KeyStoreListFragment()

        fileInfoFragment = childFragmentManager.findFragmentByTag("fileInfoFragment") as? FileInfoFragment
            ?: FileInfoFragment()
            
        requireContext().mainExecutor.execute {
            fileInfo?.let { fileInfoFragment?.fileinfo = it }
        }
    }

    private fun setupKeyStoreObserver() {
        if (isObserverAdded) return
        keyStoreListFragment?.setRunOnStart { tracker ->
            tracker.addObserver(object : SelectionTracker.SelectionObserver<String>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()
                    isEncryptEnabled = tracker.hasSelection()
                }
            })
        }
        isObserverAdded = true
    }

    @Composable
    private fun FileEncryptionScreen() {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.wrapContentHeight().fillMaxWidth()) {
                AndroidView(
                    factory = { context ->
                        FragmentContainerView(context).apply { id = fileInfoContainerId }
                    },
                    update = { _ ->
                        if (childFragmentManager.findFragmentById(fileInfoContainerId) == null) {
                            childFragmentManager.commit {
                                replace(fileInfoContainerId, fileInfoFragment!!, "fileInfoFragment")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

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

            Button(
                onClick = { encrypt() },
                enabled = isEncryptEnabled || encryptionRunning
            ) {
                Text(text = if (encryptionRunning) stringResource(R.string.cancel) else stringResource(R.string.encrypt))
            }

            if (progressText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = progressText)
            }
        }
    }

    private fun encrypt() {
        val selectionTracker = keyStoreListFragment?.getSelectionTracker() ?: return
        if (!selectionTracker.hasSelection() && !encryptionRunning) return

        if (encryptionRunning) {
            // cancel encryption
            encryptionRunning = false
        } else {
            val selectedAlias = selectionTracker.selection.first()

            // start encryption
            encryptionRunning = true
            lastProgressPrc = -1
            progressText = ""

            Thread {
                try {
                    val fileInfoVal = fileInfo ?: return@Thread
                    val uriVal = uri ?: return@Thread

                    val fileRecord = OmsFileProvider.create(
                        requireContext(),
                        fileInfoVal.filename + "." + MessageComposer.OMS_FILE_TYPE,
                        true
                    )

                    EncryptedFile.create(
                        requireNotNull(requireContext().contentResolver.openInputStream(uriVal)),
                        fileRecord.path!!.toFile(),
                        requireNotNull(cryptographyManager.getByAlias(selectedAlias, preferences)).public,
                        RSAUtil.getRsaTransformation(preferences),
                        AESUtil.getKeyLength(preferences),
                        AESUtil.getAesTransformation(preferences),
                        { !encryptionRunning },
                        { updateProgress(it) }
                    )

                    if (encryptionRunning) {
                        updateProgress(fileInfoVal.fileSize.toInt()) // 100%
                        navBackIfPaused = true

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/octet-stream"
                            putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }

                        startActivity(intent)
                    } else {
                        // operation has been cancelled
                        Files.delete(fileRecord.path)
                        requireContext().mainExecutor.execute {
                            Toast.makeText(
                                requireContext(),
                                R.string.operation_has_been_cancelled,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        updateProgress(null)
                    }
                    encryptionRunning = false
                } catch (ex: Exception) {
                    Util.printStackTrace(ex)
                    requireActivity().mainExecutor.execute {
                        Toast.makeText(
                            requireContext(),
                            String.format("%s: %s", ex.javaClass.simpleName, ex.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    encryptionRunning = false
                }
            }.start()
        }
    }

    private fun updateProgress(value: Int?) {
        val s: String
        if (value != null) {
            val fileInfoVal = fileInfo ?: return
            val progressPrc = (value.toDouble() / fileInfoVal.fileSize.toDouble() * 100.0).toInt()
            if (lastProgressPrc == progressPrc) return

            lastProgressPrc = progressPrc
            s = if (lastProgressPrc == 100) {
                getString(R.string.done)
            } else {
                String.format(Locale.getDefault(), getString(R.string.working_prc), lastProgressPrc)
            }
        } else {
            s = ""
        }

        requireContext().mainExecutor.execute {
            progressText = s
        }
    }

    override fun onPause() {
        super.onPause()
        if (!navBackIfPaused) return
        val navController = NavHostFragment.findNavController(this)
        if (navController.currentDestination != null
            && navController.currentDestination!!.id != R.id.fileEncryptionFragment
        ) {
            Log.d(TAG, String.format("Already navigating to %s", navController.currentDestination))
            return
        }
        Log.d(TAG, "onPause: going backward")
        Util.discardBackStack(this)
    }

    companion object {
        private val TAG = FileEncryptionFragment::class.java.simpleName
    }
}
