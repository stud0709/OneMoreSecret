package com.onemoresecret

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.onemoresecret.crypto.BTCAddress
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedCryptoCurrencyAddress
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil
import com.onemoresecret.qr.QRUtil
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

class CryptoCurrencyAddressGenerator : Fragment() {

    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var outputFragment: OutputFragment? = null
    private val cryptographyManager = CryptographyManager()
    private lateinit var preferences: SharedPreferences

    private val keyStoreListContainerId = View.generateViewId()
    private val outputContainerId = View.generateViewId()

    private var btcKeyPair: BTCAddress.BTCKeyPair? = null
    private var address by mutableStateOf("")
    private var isBackupEnabled by mutableStateOf(false)
    private var backupSupplier: (() -> String)? = null
    private var isObserverAdded = false

    private val menuProvider = object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_crypto_address_generator, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.menuItemNewAddress -> {
                    newBitcoinAddress()
                    true
                }
                R.id.menuItemCryptoAdrGenHelp -> {
                    Util.openUrl(R.string.crypto_address_generator_url, requireContext())
                    true
                }
                else -> false
            }
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
                        CryptoCurrencyAddressGeneratorScreen()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        setupChildFragments()
        newBitcoinAddress()
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
    }

    private fun setupKeyStoreObserver() {
        if (isObserverAdded) return
        keyStoreListFragment?.setRunOnStart { tracker ->
            tracker.addObserver(object : SelectionTracker.SelectionObserver<String>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()
                    val hasSelection = tracker.hasSelection()
                    isBackupEnabled = hasSelection
                    
                    if (hasSelection) {
                        val selectedAlias = tracker.selection.first()
                        encryptWif(selectedAlias)
                    } else {
                        setBitcoinAddress()
                    }
                }
            })
        }
        isObserverAdded = true
    }

    private fun newBitcoinAddress() {
        try {
            btcKeyPair = BTCAddress.newKeyPair().toBTCKeyPair()
            address = btcKeyPair!!.btcAddressBase58
            
            requireActivity().mainExecutor.execute {
                setBitcoinAddress()
                
                val tracker = keyStoreListFragment?.getSelectionTracker()
                if (tracker != null && tracker.hasSelection()) {
                    val selectedAlias = tracker.selection.first()
                    encryptWif(selectedAlias)
                }
            }
        } catch (e: Exception) {
            Util.printStackTrace(e)
            Toast.makeText(requireContext(), e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun encryptWif(alias: String) {
        val kp = btcKeyPair ?: return
        try {
            val encrypted = EncryptedCryptoCurrencyAddress(
                MessageComposer.APPLICATION_BITCOIN_ADDRESS,
                kp.wif,
                requireNotNull(cryptographyManager.getByAlias(alias, preferences)).public,
                RSAUtil.getRsaTransformation(preferences),
                AESUtil.getKeyLength(preferences),
                AESUtil.getAesTransformation(preferences)
            ).message

            outputFragment?.setMessage(MessageComposer.encodeAsOmsText(encrypted), getString(R.string.wif_encrypted))
            backupSupplier = getBackupSupplier(kp.btcAddressBase58, encrypted)
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun setBitcoinAddress() {
        outputFragment?.setMessage(address, getString(R.string.public_address))
    }

    private fun getBackupSupplier(btcAddress: String, message: ByteArray): () -> String {
        return {
            val stringBuilder = StringBuilder()
            val omsText = MessageComposer.encodeAsOmsText(message)
            try {
                val list = QRUtil.getQrSequence(
                    omsText,
                    QRUtil.getChunkSize(preferences),
                    QRUtil.getBarcodeSize(preferences)
                )

                stringBuilder
                    .append("<html><body><h1>")
                    .append("OneMoreSecret Cold Wallet")
                    .append("</h1>")
                    .append("<p>This is a hard copy of your Bitcoin Address <b>")
                    .append(btcAddress)
                    .append("</b>:</p><p>")

                ByteArrayOutputStream().use { baos ->
                    val bitmap = QRUtil.getQr(btcAddress, QRUtil.getBarcodeSize(preferences))
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                    baos.flush()
                    stringBuilder.append("<img src=\"data:image/png;base64,")
                        .append(Base64.getEncoder().encodeToString(baos.toByteArray()))
                        .append("\" style=\"width:200px;height:200px;\">")
                }

                stringBuilder.append("</p>The above QR code contains the public bitcoin address, ")
                    .append("use a regular QR code scanner to read it.</p><p>The private key is encrypted, ")
                    .append("scan the following QR code sequence <b>with OneMoreSecret</b> to access it:</p><p>")

                for (i in list.indices) {
                    val bitmap = list[i]
                    ByteArrayOutputStream().use { baos ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                        baos.flush()
                        stringBuilder.append("<table style=\"display: inline-block;\"><tr style=\"vertical-align: bottom;\"><td>")
                            .append(i + 1)
                            .append("</td><td><img src=\"data:image/png;base64,")
                            .append(Base64.getEncoder().encodeToString(baos.toByteArray()))
                            .append("\" style=\"width:200px;height:200px;\"></td></tr></table>")
                    }
                }
                stringBuilder
                    .append("</p><p>")
                    .append("The same as text:")
                    .append("&nbsp;")
                    .append("</p><p style=\"font-family:monospace;\">")

                var offset = 0

                while (offset < omsText.length) {
                    val s = omsText.substring(offset, kotlin.math.min(offset + Util.BASE64_LINE_LENGTH, omsText.length))
                    stringBuilder.append(s).append("<br>")
                    offset += Util.BASE64_LINE_LENGTH
                }

                stringBuilder.append("</p><p>")
                    .append("Message format: oms00_[base64 encoded data]")
                    .append("</p><p>")
                    .append("Data format: see https://github.com/stud0709/OneMoreSecret/blob/master/app/src/main/java/com/onemoresecret/crypto/EncryptedCryptoCurrencyAddress.java")
                    .append("</p></body></html>")

                stringBuilder.toString()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    @Composable
    private fun CryptoCurrencyAddressGeneratorScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(text = stringResource(R.string.public_address))
            
            Text(
                text = address.chunked(4).joinToString(" "),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Text(
                text = "WARNING: EXPERIMENTAL FEATURE!",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

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
                onClick = {
                    try {
                        val fileRecord = OmsFileProvider.create(requireContext(), "${address}_backup.html", true)
                        Files.write(fileRecord.path, backupSupplier?.invoke()?.toByteArray(StandardCharsets.UTF_8))
                        fileRecord.path!!.toFile().deleteOnExit()

                        val intent = Intent(Intent.ACTION_SEND).apply {
                            putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            type = "text/html"
                        }
                        startActivity(Intent.createChooser(intent, String.format(getString(R.string.backup_file), address)))
                    } catch (ex: Exception) {
                        throw RuntimeException(ex)
                    }
                },
                enabled = isBackupEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Create Backup")
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
        private val TAG = CryptoCurrencyAddressGenerator::class.java.simpleName
    }
}
