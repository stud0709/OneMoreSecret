package com.onemoresecret

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import com.google.zxing.WriterException
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.AESUtil.generateIv
import com.onemoresecret.crypto.AESUtil.generateSalt
import com.onemoresecret.crypto.AESUtil.getAesKeyAlgorithm
import com.onemoresecret.crypto.AESUtil.getAesKeyAlgorithmIdx
import com.onemoresecret.crypto.AESUtil.getAesTransformationIdx
import com.onemoresecret.crypto.AESUtil.getKeyFromPassword
import com.onemoresecret.crypto.AESUtil.getKeyLength
import com.onemoresecret.crypto.AESUtil.getKeyspecIterations
import com.onemoresecret.crypto.AESUtil.getSaltLength
import com.onemoresecret.crypto.AesEncryptedPrivateKeyTransfer
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.CryptographyManager.Companion.generateKeyPair
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.crypto.RSAUtils.getFingerprint
import com.onemoresecret.databinding.FragmentNewPrivateKeyBinding
import com.onemoresecret.qr.QRUtil.getBarcodeSize
import com.onemoresecret.qr.QRUtil.getChunkSize
import com.onemoresecret.qr.QRUtil.getQrSequence
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Objects
import kotlin.math.min

class NewPrivateKeyFragment : Fragment() {
    private var binding: FragmentNewPrivateKeyBinding? = null
    private var cryptographyManager = CryptographyManager()

    private var preferences: SharedPreferences? = null

    private val menuProvider = PrivateKeyMenuProvider()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentNewPrivateKeyBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider)
        cryptographyManager = CryptographyManager()
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        binding!!.btnCreatePrivateKey.setOnClickListener { e: View? -> createPrivateKey() }
        binding!!.checkBox.setOnCheckedChangeListener { btn: CompoundButton?, isChecked: Boolean ->
            binding!!.btnActivatePrivateKey.isEnabled =
                isChecked
        }
    }

    private fun createPrivateKey() {
        try {
            val preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

            require(binding!!.txtNewKeyAlias.text!!.isNotEmpty()) { getString(R.string.key_alias_may_not_be_empty) }

            val alias = binding!!.txtNewKeyAlias.text.toString()

            require(!cryptographyManager.keyStore!!.containsAlias(alias)) { getString(R.string.key_alias_already_exists) }

            require(binding!!.txtNewTransportPassword.text!!.length >= 10) {
                getString(
                    R.string.password_too_short
                )
            }

            require(
                binding!!.txtNewTransportPassword.text.toString() == Objects.requireNonNull(
                    binding!!.txtRepeatTransportPassword.text
                ).toString()
            ) { getString(R.string.password_mismatch) }

            val keyPair = generateKeyPair(if (binding!!.sw4096bit.isChecked) 4096 else 2048)
            val publicKey = keyPair.public as RSAPublicKey
            val fingerprint = getFingerprint(publicKey)
            val iv = generateIv()
            val salt = generateSalt(getSaltLength(preferences))
            val aesKeyLength = getKeyLength(preferences)
            val aesKeySpecIterations = getKeyspecIterations(preferences)
            val aesKeyAlgorithm = getAesKeyAlgorithm(preferences).keyAlgorithm

            val aesSecretKey = getKeyFromPassword(
                binding!!.txtNewTransportPassword.text.toString().toCharArray(),
                salt,
                aesKeyAlgorithm,
                aesKeyLength,
                aesKeySpecIterations
            )

            val message = AesEncryptedPrivateKeyTransfer(
                alias,
                keyPair,
                aesSecretKey,
                iv,
                salt,
                getAesTransformationIdx(preferences),
                getAesKeyAlgorithmIdx(preferences),
                aesKeyLength,
                aesKeySpecIterations
            ).message

            binding!!.checkBox.isEnabled = true
            binding!!.checkBox.isChecked = false

            binding!!.btnActivatePrivateKey.setOnClickListener { e: View? ->
                try {
                    cryptographyManager.importKey(
                        alias,
                        keyPair,
                        requireContext()
                    )

                    Toast.makeText(
                        requireContext(),
                        String.format(getString(R.string.key_successfully_activated), alias),
                        Toast.LENGTH_LONG
                    ).show()

                    //go back
                    discardBackStack(this)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    Toast.makeText(requireContext(), ex.message, Toast.LENGTH_LONG).show()
                }
            }

            //share HTML file
            val html = getKeyBackupHtml(alias, fingerprint, message)
            val fingerprintString = byteArrayToHex(fingerprint).replace("\\s".toRegex(), "_")
            val fileRecord = OmsFileProvider.create(
                requireContext(),
                "pk_$fingerprintString.html", true
            )
            Files.write(fileRecord!!.path, html.toByteArray(StandardCharsets.UTF_8))
            fileRecord.path.toFile().deleteOnExit()

            val intent = Intent()
            intent.setAction(Intent.ACTION_SEND)
            intent.putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setType("text/html")
            startActivity(
                Intent.createChooser(
                    intent,
                    String.format(getString(R.string.backup_file), alias)
                )
            )
        } catch (ex: Exception) {
            ex.printStackTrace()
            Toast.makeText(requireContext(), ex.message, Toast.LENGTH_LONG).show()
        }
    }

    @Throws(WriterException::class, IOException::class)
    private fun getKeyBackupHtml(
        alias: String,
        fingerprint: ByteArray,
        message: ByteArray
    ): String {
        val stringBuilder = StringBuilder()

        val list = getQrSequence(
            encodeAsOmsText(message),
            getChunkSize(preferences!!),
            getBarcodeSize(preferences!!)
        )

        stringBuilder
            .append("<html><body><h1>")
            .append("OneMoreSecret Private Key Backup")
            .append("</h1><p><b>")
            .append("Keep this file / printout in a secure location")
            .append("</b></p><p>")
            .append("This is a hard copy of your Private Key for OneMoreSecret. It can be used to import your Private Key into a new device or after a reset of OneMoreSecret App. This document is encrypted with AES, you will need your TRANSPORT PASSWORD to complete the import procedure.")
            .append("</p><h2>")
            .append("WARNING:")
            .append("</h2><p>")
            .append("DO NOT share this document with other persons.")
            .append("<br>")
            .append("DO NOT provide its content to untrusted apps, on the Internet etc.")
            .append("<br>")
            .append("If you need to restore your Key, start OneMoreSecret App on your phone BY HAND and scan the codes. DO NOT trust unexpected prompts and pop-ups.")
            .append("<br><b>")
            .append("THIS DOCUMENT IS THE ONLY WAY TO RESTORE YOUR PRIVATE KEY")
            .append("</b></p><p><b>")
            .append("Key alias:")
            .append("&nbsp;")
            .append(Html.escapeHtml(alias))
            .append("</b></p><p><b>RSA Fingerprint:")
            .append("&nbsp;")
            .append(byteArrayToHex(fingerprint))
            .append("</b></p><p>")
            .append("Scan this with your OneMoreSecret App:")
            .append("</p><p>")

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
            .append("</p><h2>")
            .append("Long-Term Backup and Technical Details")
            .append("</h2><p>")
            .append("Base64 Encoded Message:")
            .append("&nbsp;")
            .append("</p><p style=\"font-family:monospace;\">")

        val messageAsUrl = encodeAsOmsText(message)
        var offset = 0

        while (offset < messageAsUrl.length) {
            val s = messageAsUrl.substring(
                offset,
                min(
                    (offset + Util.BASE64_LINE_LENGTH).toDouble(),
                    messageAsUrl.length.toDouble()
                ).toInt()
            )
            stringBuilder.append(s).append("<br>")
            offset += Util.BASE64_LINE_LENGTH
        }

        stringBuilder.append("</p><p>")
            .append("Message format: oms00_[base64 encoded data]")
            .append("</p><p>")
            .append("Data format: see https://github.com/stud0709/OneMoreSecret/blob/master/app/src/main/java/com/onemoresecret/crypto/AesEncryptedPrivateKeyTransfer.java")
            .append("</p></body></html>")

        return stringBuilder.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
        binding = null
    }

    private inner class PrivateKeyMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.new_private_key_md_url, requireContext())
            } else {
                return false
            }
            return true
        }
    }
}