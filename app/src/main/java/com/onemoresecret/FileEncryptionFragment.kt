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
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.Util.UriFileInfo
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.getFileInfo
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.AESUtil.getAesTransformationIdx
import com.onemoresecret.crypto.AESUtil.getKeyLength
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedFile.create
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtils.getRsaTransformationIdx
import com.onemoresecret.databinding.FragmentFileEncryptionBinding
import com.onemoresecret.databinding.FragmentKeyStoreListBinding
import java.nio.file.Files
import java.security.interfaces.RSAPublicKey
import java.util.Locale

class FileEncryptionFragment : Fragment() {
    private var binding: FragmentFileEncryptionBinding? = null
    private var keyStoreListFragment: KeyStoreListFragment? = null
    private val cryptographyManager = CryptographyManager()
    private var preferences: SharedPreferences? = null
    private var uri: Uri? = null
    private var fileInfo: UriFileInfo? = null
    private var encryptionRunning = false
    private var lastProgressPrc = -1
    private var navBackIfPaused: Boolean = false

    private val menuProvider = FileEncryptionMenuProvider()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentFileEncryptionBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
        binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider)

        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        binding!!.btnEncrypt.isEnabled = false
        keyStoreListFragment = binding!!.fragmentContainerView.getFragment<KeyStoreListFragment>()

        uri = requireArguments().getParcelable(QRFragment.ARG_URI)
        fileInfo = getFileInfo(requireContext(), uri!!)

        requireContext().mainExecutor.execute {
            (binding!!.fragmentContainerView6.getFragment<Fragment>() as FileInfoFragment).setValues(
                fileInfo!!.filename,
                fileInfo!!.fileSize
            )
        }


        keyStoreListFragment!!.setRunOnStart { fragmentKeyStoreListBinding: FragmentKeyStoreListBinding? ->
            keyStoreListFragment!!
                .selectionTracker
                .addObserver(object : SelectionTracker.SelectionObserver<String?>() {
                    override fun onSelectionChanged() {
                        super.onSelectionChanged()
                        binding!!.btnEncrypt.isEnabled =
                            keyStoreListFragment!!.selectionTracker.hasSelection()
                    }
                })
        }

        binding!!.btnEncrypt.setOnClickListener { v: View? -> encrypt() }
        binding!!.txtProgress.text = ""
    }

    private fun encrypt() {
        val selectedAlias = keyStoreListFragment!!.selectionTracker
            .selection
            .iterator()
            .next()!!

        if (encryptionRunning) {
            //cancel encryption
            binding!!.btnEncrypt.setText(R.string.encrypt)
            encryptionRunning = false
        } else {
            //start encryption
            binding!!.btnEncrypt.setText(R.string.cancel)
            encryptionRunning = true
            lastProgressPrc = -1

            Thread {
                try {
                    val fileRecord = OmsFileProvider.create(
                        requireContext(),
                        fileInfo!!.filename + "." + MessageComposer.OMS_FILE_TYPE,
                        true
                    )

                    create(
                        requireContext().contentResolver.openInputStream(uri!!)!!,
                        fileRecord!!.path.toFile(),
                        (cryptographyManager.getCertificate(selectedAlias).publicKey as RSAPublicKey),
                        getRsaTransformationIdx(preferences!!),
                        getKeyLength(preferences!!),
                        getAesTransformationIdx(preferences!!),
                        { binding == null || !encryptionRunning },
                        { value: Int? -> this.updateProgress(value) }
                    )

                    if (encryptionRunning) {
                        updateProgress(fileInfo!!.fileSize) //100%
                        requireContext().mainExecutor.execute {
                            if (binding == null) return@execute
                            binding!!.btnEncrypt.setText(R.string.encrypt)
                        }
                        navBackIfPaused = true

                        val intent = Intent(Intent.ACTION_SEND)
                        intent.setType("application/octet-stream")
                        intent.putExtra(Intent.EXTRA_STREAM, fileRecord!!.uri)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                        startActivity(intent)
                    } else {
                        //operation has been cancelled
                        Files.delete(fileRecord!!.path)
                        requireContext().mainExecutor.execute {
                            if (binding == null) return@execute
                            binding!!.btnEncrypt.setText(R.string.encrypt)
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
                    ex.printStackTrace()
                    requireActivity().mainExecutor.execute {
                        Toast.makeText(
                            requireContext(),
                            String.format("%s: %s", ex.javaClass.simpleName, ex.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }
    }

    private fun updateProgress(value: Int?) {
        var s = ""
        if (value != null) {
            val progressPrc = (value.toDouble() / fileInfo!!.fileSize.toDouble() * 100.0).toInt()
            if (lastProgressPrc == progressPrc) return

            lastProgressPrc = progressPrc
            s = if (lastProgressPrc == 100) getString(R.string.done) else String.format(
                Locale.getDefault(),
                getString(R.string.working_prc),
                lastProgressPrc
            )
        }
        val fs = s

        requireContext().mainExecutor.execute {
            if (binding == null) return@execute
            binding!!.txtProgress.text = fs
        }
    }

    private inner class FileEncryptionMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.encrypt_file_md_url, requireContext())
            } else {
                return false
            }

            return true
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
        discardBackStack(this)
    }

    companion object {
        private val TAG: String = FileEncryptionFragment::class.java.simpleName
    }
}