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
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.AESUtil.getAesTransformationIdx
import com.onemoresecret.crypto.AESUtil.getKeyLength
import com.onemoresecret.crypto.BTCAddress.BTCKeyPair
import com.onemoresecret.crypto.BTCAddress.newKeyPair
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedCryptoCurrencyAddress
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.crypto.RSAUtils.getRsaTransformationIdx
import com.onemoresecret.databinding.FragmentCryptoCurrencyAddressGeneratorBinding
import com.onemoresecret.databinding.FragmentKeyStoreListBinding
import com.onemoresecret.qr.QRUtil.getBarcodeSize
import com.onemoresecret.qr.QRUtil.getChunkSize
import com.onemoresecret.qr.QRUtil.getQr
import com.onemoresecret.qr.QRUtil.getQrSequence
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.math.min

class CryptoCurrencyAddressGenerator : Fragment() {
    private var binding: FragmentCryptoCurrencyAddressGeneratorBinding? = null
    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var preferences: SharedPreferences? = null
    private var outputFragment: OutputFragment? = null
    private val menuProvider = BitcoinAddressMenuProvider()
    private val cryptographyManager = CryptographyManager()

    private var encryptWif: Consumer<String>? = null
    private var backupSupplier: Supplier<String>? = null
    private var address: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCryptoCurrencyAddressGeneratorBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider)
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        binding!!.btnBackup.isEnabled = false

        keyStoreListFragment = binding!!.fragmentContainerView.getFragment<KeyStoreListFragment>()
        outputFragment = binding!!.fragmentContainerView4.getFragment<OutputFragment>()

        binding!!.btnBackup.setOnClickListener { btn: View? ->
            try {
                val fileRecord =
                    OmsFileProvider.create(requireContext(), address + "_backup.html", true)
                Files.write(
                    fileRecord!!.path,
                    backupSupplier!!.get().toByteArray(StandardCharsets.UTF_8)
                )
                fileRecord.path.toFile().deleteOnExit()

                val intent = Intent()
                intent.setAction(Intent.ACTION_SEND)
                intent.putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.setType("text/html")
                startActivity(
                    Intent.createChooser(
                        intent,
                        String.format(getString(R.string.backup_file), address)
                    )
                )
            } catch (ex: Exception) {
                throw RuntimeException(ex)
            }
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
                            encryptWif!!.accept(selectedAlias)
                        } else {
                            setBitcoinAddress()
                        }

                        binding!!.btnBackup.isEnabled =
                            keyStoreListFragment!!.selectionTracker.hasSelection()
                    }
                })
        }

        newBitcoinAddress()
    }

    private fun newBitcoinAddress() {
        try {
            val btcKeyPair = newKeyPair().toBTCKeyPair()
            address = btcKeyPair.btcAddressBase58
            encryptWif = getEncryptWif(btcKeyPair)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                requireContext(),
                e.message,
                Toast.LENGTH_LONG
            ).show()
        }

        requireActivity().mainExecutor.execute {
            setBitcoinAddress()
            if (keyStoreListFragment!!.selectionTracker.hasSelection()) {
                val selectedAlias =
                    keyStoreListFragment!!.selectionTracker.selection.iterator().next()!!
                encryptWif!!.accept(selectedAlias)
            }
        }
    }

    private fun getEncryptWif(btcKeyPair: BTCKeyPair): Consumer<String> {
        return Consumer { alias: String? ->
            try {
                val encrypted = EncryptedCryptoCurrencyAddress(
                    MessageComposer.APPLICATION_BITCOIN_ADDRESS,
                    btcKeyPair.wif,
                    cryptographyManager.getCertificate(alias).publicKey as RSAPublicKey,
                    getRsaTransformationIdx(preferences!!),
                    getKeyLength(preferences!!),
                    getAesTransformationIdx(preferences!!)
                ).message

                outputFragment!!.setMessage(
                    encodeAsOmsText(encrypted),
                    getString(R.string.wif_encrypted)
                )
                backupSupplier = getBackupSupplier(btcKeyPair.btcAddressBase58, encrypted)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    private fun getBackupSupplier(btcAddress: String, message: ByteArray): Supplier<String> {
        return Supplier {
            val stringBuilder = StringBuilder()
            val omsText = encodeAsOmsText(message)
            try {
                val list = getQrSequence(
                    omsText,
                    getChunkSize(preferences!!),
                    getBarcodeSize(preferences!!)
                )

                stringBuilder
                    .append("<html><body><h1>")
                    .append("OneMoreSecret Cold Wallet")
                    .append("</h1>")
                    .append("<p>This is a hard copy of your Bitcoin Address <b>")
                    .append(btcAddress)
                    .append("</b>:</p><p>")

                ByteArrayOutputStream().use { baos ->
                    val bitmap = getQr(
                        btcAddress, getBarcodeSize(
                            preferences!!
                        )
                    )
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
                    val s = omsText.substring(
                        offset,
                        min(
                            (offset + Util.BASE64_LINE_LENGTH).toDouble(),
                            omsText.length.toDouble()
                        ).toInt()
                    )
                    stringBuilder.append(s).append("<br>")
                    offset += Util.BASE64_LINE_LENGTH
                }

                stringBuilder.append("</p><p>")
                    .append("Message format: oms00_[base64 encoded data]")
                    .append("</p><p>")
                    .append("Data format: see https://github.com/stud0709/OneMoreSecret/blob/master/app/src/main/java/com/onemoresecret/crypto/EncryptedCryptoCurrencyAddress.java")
                    .append("</p></body></html>")

                return@Supplier stringBuilder.toString()
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    private fun setBitcoinAddress() {
        binding!!.textViewAddress.text = address
        outputFragment!!.setMessage(address, getString(R.string.public_address))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
        binding = null
    }

    private inner class BitcoinAddressMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_crypto_address_generator, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemNewAddress) {
                newBitcoinAddress()
            } else if (menuItem.itemId == R.id.menuItemCryptoAdrGenHelp) {
                openUrl(R.string.crypto_address_generator_url, requireContext())
            } else {
                return false
            }

            return true
        }
    }
}