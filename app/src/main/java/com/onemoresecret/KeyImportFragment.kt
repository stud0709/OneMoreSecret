package com.onemoresecret

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.AESUtil.getKeyFromPassword
import com.onemoresecret.crypto.AESUtil.process
import com.onemoresecret.crypto.AesKeyAlgorithm
import com.onemoresecret.crypto.AesTransformation
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.CryptographyManager.Companion.restoreKeyPair
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtils.getFingerprint
import com.onemoresecret.databinding.FragmentKeyImportBinding
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.KeyStoreException
import java.security.interfaces.RSAPublicKey
import java.util.Objects
import java.util.function.Consumer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

/**
 * Key import Fragment.
 */
class KeyImportFragment : Fragment() {
    private var binding: FragmentKeyImportBinding? = null
    private val cryptographyManager = CryptographyManager()

    private val menuProvider = KeyImportMenu()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider)

        checkNotNull(arguments)

        try {
            OmsDataInputStream(ByteArrayInputStream(arguments!!.getByteArray("MESSAGE"))).use { dataInputStream ->
                // (1) Application ID
                val applicationId = dataInputStream.readUnsignedShort()
                require(applicationId == MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER) { "wrong applicationId: $applicationId" }

                // (2) alias
                val alias = dataInputStream.readString()
                Log.d(TAG, "alias: $alias")
                binding!!.editTextKeyAlias.setText(alias)

                // --- AES parameter ---

                // (3) salt
                val salt = dataInputStream.readByteArray()
                Log.d(TAG, "salt: " + byteArrayToHex(salt))

                // (4) IV
                val iv = dataInputStream.readByteArray()
                Log.d(TAG, "IV: " + byteArrayToHex(iv))

                // (5) AES transformation index
                val aesTransformation =
                    AesTransformation.entries[dataInputStream.readUnsignedShort()].transformation
                Log.d(
                    TAG,
                    "cipher algorithm: $aesTransformation"
                )

                // (6) key algorithm index
                val aesKeyAlg =
                    AesKeyAlgorithm.entries[dataInputStream.readUnsignedShort()].keyAlgorithm
                Log.d(TAG, "AES key algorithm: $aesKeyAlg")

                // (7) key length
                val aesKeyLength = dataInputStream.readUnsignedShort()
                Log.d(TAG, "AES key length: $aesKeyLength")

                // (8) AES iterations
                val iterations = dataInputStream.readUnsignedShort()
                Log.d(TAG, "iterations: $iterations")

                // --- Encrypted part ---

                // (9) cipher text
                val cipherText = dataInputStream.readByteArray()
                Log.d(TAG, cipherText.size.toString() + " bytes cipher text read")

                binding!!.editTextKeyAlias.setText(alias)
                binding!!.btnDecrypt.setOnClickListener { e: View? ->
                    Thread {
                        onPasswordEntry(
                            salt,
                            iv,
                            cipherText,
                            aesTransformation,
                            aesKeyAlg,
                            aesKeyLength,
                            iterations
                        )
                    }.start()
                }
            }
        } catch (ex: IOException) {
            ex.printStackTrace()
            Toast.makeText(
                context,
                Objects.requireNonNullElse(ex.message, ex.javaClass.name),
                Toast.LENGTH_LONG
            ).show()
            discardBackStack(this)
        }
    }

    private fun onPasswordEntry(
        salt: ByteArray,
        iv: ByteArray,
        cipherText: ByteArray,
        aesTransformation: String,
        keyAlg: String,
        keyLength: Int,
        iterations: Int
    ) {
        try {
            //try decrypt
            val secretKey = getKeyFromPassword(
                binding!!.editTextPassphrase.text.toString().toCharArray(),
                salt,
                keyAlg,
                keyLength,
                iterations
            )

            val bArr = process(
                Cipher.DECRYPT_MODE,
                cipherText,
                secretKey,
                IvParameterSpec(iv),
                aesTransformation
            )

            try {
                OmsDataInputStream(ByteArrayInputStream(bArr)).use { dataInputStream ->
                    // (9.1) - private key material
                    val privateKeyMaterial = dataInputStream.readByteArray()

                    // (9.2) - public key material
                    val publicKeyMaterial = dataInputStream.readByteArray()

                    val keyPair = restoreKeyPair(privateKeyMaterial, publicKeyMaterial)

                    val publicKey = keyPair.public as RSAPublicKey

                    val fingerprintBytes = getFingerprint(publicKey)
                    val fingerprint = byteArrayToHex(fingerprintBytes)
                    requireContext().mainExecutor.execute {
                        binding!!.textFingerprintData.text = fingerprint
                        //check alias
                        binding!!.editTextKeyAlias.addTextChangedListener(
                            getTextWatcher(
                                fingerprintBytes
                            )
                        )

                        validateAlias(fingerprintBytes)

                        binding!!.btnSave.isEnabled = true
                        binding!!.btnSave.setOnClickListener { e: View? ->
                            Thread {
                                try {
                                    //delete other keys with the same fingerprint
                                    val sameFingerprint =
                                        cryptographyManager.getByFingerprint(fingerprintBytes)
                                    sameFingerprint.forEach(Consumer { a: String? ->
                                        try {
                                            cryptographyManager.deleteKey(a)
                                        } catch (ex: KeyStoreException) {
                                            ex.printStackTrace()
                                        }
                                    })

                                    val keyAlias: String = binding!!
                                        .editTextKeyAlias
                                        .getText()
                                        .toString().trim()

                                    cryptographyManager.importKey(
                                        keyAlias,
                                        keyPair,
                                        requireContext()
                                    )
                                    requireContext().mainExecutor.execute {
                                        Toast.makeText(
                                            this.context,
                                            "Private key '$keyAlias' successfully imported",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        discardBackStack(this)
                                    }
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                    requireContext().mainExecutor.execute {
                                        Toast.makeText(
                                            this.context,
                                            ex.message,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                }
                            }.start()
                        }
                    }
                }
            } catch (ex: IOException) {
                ex.printStackTrace()
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            requireContext().mainExecutor.execute {
                Toast.makeText(
                    this.context,
                    "Could not decrypt. Wrong passphrase?",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentKeyImportBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
        binding = null
    }

    private fun validateAlias(fingerprintNew: ByteArray) {
        try {
            val alias = binding!!.editTextKeyAlias.text.toString()
            Log.d(TAG, "alias: $alias")
            var warning: String? = null

            if (cryptographyManager.keyStore!!.containsAlias(alias)) {
                val publicKey = cryptographyManager.getCertificate(alias).publicKey as RSAPublicKey
                val fingerprint = getFingerprint(publicKey)
                if (!fingerprint.contentEquals(fingerprintNew)) {
                    warning = String.format(
                        getString(R.string.warning_alias_exists),
                        byteArrayToHex(fingerprint)
                    )
                }
            }

            val sameFingerprint = cryptographyManager.getByFingerprint(fingerprintNew)
            if (!sameFingerprint.isEmpty()) {
                warning = String.format(
                    getString(R.string.warning_same_fingerprint),
                    sameFingerprint[0]
                )
            }

            val _warning = warning

            requireContext().mainExecutor.execute {
                binding!!.txtWarnings.text = _warning ?: ""
                binding!!.txtWarnings.visibility =
                    if (_warning == null) View.GONE else View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getTextWatcher(fingerprintBytes: ByteArray): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                validateAlias(fingerprintBytes)
            }
        }
    }

    private inner class KeyImportMenu : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.key_import_md_url, requireContext())
            } else {
                return false
            }
            return true
        }
    }

    companion object {
        private val TAG: String = KeyImportFragment::class.simpleName!!
    }
}