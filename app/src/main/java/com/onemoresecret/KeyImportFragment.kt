package com.onemoresecret

import android.content.Context
import android.content.SharedPreferences
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
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.crypto.AESUtil.getAesKeyMaterialFromPassword
import com.onemoresecret.crypto.AESUtil.process
import com.onemoresecret.crypto.AesKeyAlgorithm
import com.onemoresecret.crypto.AesTransformation
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil.getFingerprint
import com.onemoresecret.crypto.RSAUtil.restorePublicKey
import com.onemoresecret.databinding.FragmentKeyImportBinding
import java.io.ByteArrayInputStream
import java.io.IOException
import java.security.interfaces.RSAPublicKey
import java.util.Arrays
import java.util.Objects
import javax.crypto.Cipher

/**
 * Key import Fragment.
 */
class KeyImportFragment : Fragment() {
    private lateinit var binding: FragmentKeyImportBinding
    private val cryptographyManager = CryptographyManager()
    private val menuProvider = KeyImportMenu()
    private var preferences: SharedPreferences? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        requireActivity().addMenuProvider(menuProvider)

        try {
            OmsDataInputStream(ByteArrayInputStream(requireArguments().getByteArray("MESSAGE"))).use { dataInputStream ->
                // (1) Application ID
                val applicationId = dataInputStream.readUnsignedShort()
                require(applicationId == MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER) { "wrong applicationId: $applicationId" }

                // (2) alias
                val alias = dataInputStream.readString()
                Log.d(TAG, "alias: $alias")
                binding.editTextKeyAlias.setText(alias)

                // --- AES parameter ---

                // (3) salt
                val salt = dataInputStream.readByteArray()
                Log.d(TAG, "salt: " + byteArrayToHex(salt))

                // (4) IV
                val iv = dataInputStream.readByteArray()
                Log.d(TAG, "IV: " + byteArrayToHex(iv))

                // (5) AES transformation index
                val aesTransformation: AesTransformation =
                    AesTransformation.entries[dataInputStream.readUnsignedShort()]
                Log.d(TAG, "cipher algorithm: $aesTransformation")

                // (6) key algorithm index
                val aesKeyAlg: String = AesKeyAlgorithm.entries[dataInputStream.readUnsignedShort()].keyAlgorithm
                Log.d(TAG, "AES key algorithm: $aesKeyAlg")

                // (7) key length
                val aesKeyLength = dataInputStream.readUnsignedShort()
                Log.d(TAG, "AES key length: $aesKeyLength")

                // (8) AES iterations
                val iterations = dataInputStream.readUnsignedShort()
                Log.d(TAG, "iterations: " + iterations)

                // --- Encrypted part ---

                // (9) cipher text
                val cipherText = dataInputStream.readByteArray()
                Log.d(TAG, cipherText.size.toString() + " bytes cipher text read")

                binding.editTextKeyAlias.setText(alias)
                binding.btnDecrypt.setOnClickListener { _: View? ->
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
            printStackTrace(ex)
            Toast.makeText(
                getContext(),
                Objects.requireNonNullElse<String?>(ex.message, ex.javaClass.getName()),
                Toast.LENGTH_LONG
            ).show()
            discardBackStack(this)
        }
    }

    private fun onPasswordEntry(
        salt: ByteArray,
        iv: ByteArray,
        cipherText: ByteArray,
        aesTransformation: AesTransformation,
        keyAlg: String,
        keyLength: Int,
        iterations: Int
    ) {
        try {
            //try decrypt
            val aesKeyMaterial = getAesKeyMaterialFromPassword(
                binding.editTextPassphrase.getText().toString().toCharArray(),
                salt,
                keyAlg,
                keyLength,
                iterations
            )

            val bArr = process(
                Cipher.DECRYPT_MODE,
                cipherText,
                aesKeyMaterial,
                iv,
                aesTransformation
            )

            Arrays.fill(aesKeyMaterial, 0.toByte())

            try {
                OmsDataInputStream(ByteArrayInputStream(bArr)).use { dataInputStream ->
                    // (9.1) - private key material
                    val privateKeyMaterial = dataInputStream.readByteArray()

                    // (9.2) - public key material
                    val publicKeyMaterial = dataInputStream.readByteArray()

                    val publicKey = restorePublicKey(publicKeyMaterial)
                    val fingerprintBytes = getFingerprint(publicKey)
                    val fingerprint = byteArrayToHex(fingerprintBytes)
                    requireContext().mainExecutor.execute {
                        binding.textFingerprintData.text = fingerprint
                        //check alias
                        binding.editTextKeyAlias.addTextChangedListener(
                            getTextWatcher(
                                fingerprintBytes
                            )
                        )

                        validateAlias(fingerprintBytes)

                        binding.btnSave.setEnabled(true)
                        binding.btnSave.setOnClickListener { _: View? ->
                            Thread {
                                try {
                                    //delete other keys with the same fingerprint
                                    val sameFingerprint = cryptographyManager.getByFingerprint(
                                        fingerprintBytes,
                                        preferences!!
                                    )
                                    if (sameFingerprint != null) cryptographyManager.deleteKey(
                                        sameFingerprint.alias,
                                        preferences!!
                                    )

                                    val keyAlias: String =
                                        binding.editTextKeyAlias.getText().toString()
                                            .trim { it <= ' ' }
                                    cryptographyManager.importRsaKey(
                                        preferences!!,
                                        privateKeyMaterial,
                                        publicKeyMaterial,
                                        keyAlias
                                    )

                                    //neuer Keystore
                                    cryptographyManager.importRsaKey(
                                        requireActivity().getPreferences(Context.MODE_PRIVATE),
                                        privateKeyMaterial,
                                        publicKeyMaterial,
                                        keyAlias
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
                                    printStackTrace(ex)
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
                printStackTrace(ex)
            }
        } catch (ex: Exception) {
            printStackTrace(ex)
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
        return binding.getRoot()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
    }

    private fun validateAlias(fingerprintNew: ByteArray) {
        try {
            val alias = binding.editTextKeyAlias.getText().toString()
            Log.d(TAG, "alias: $alias")
            var warning: String? = null

            if (cryptographyManager.keyStore.containsAlias(alias)) {
                val publicKey =
                    cryptographyManager.keyStore.getCertificate(alias)?.publicKey as RSAPublicKey
                val fingerprint = getFingerprint(publicKey)
                if (!fingerprint.contentEquals(fingerprintNew)) {
                    warning = String.format(
                        getString(R.string.warning_alias_exists),
                        byteArrayToHex(fingerprint)
                    )
                }
            }

            val sameFingerprint =
                cryptographyManager.getByFingerprint(fingerprintNew, preferences!!)
            if (sameFingerprint != null) {
                warning = String.format(
                    getString(R.string.warning_same_fingerprint),
                    sameFingerprint.alias
                )
            }

            requireContext().mainExecutor.execute {
                binding.txtWarnings.text = warning ?: ""
                binding.txtWarnings.visibility = if (warning == null) View.GONE else View.VISIBLE
            }
        } catch (e: Exception) {
            printStackTrace(e)
        }
    }

    private fun getTextWatcher(fingerprintBytes: ByteArray): TextWatcher {
        return object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable?) {
                validateAlias(fingerprintBytes)
            }
        }
    }

    private inner class KeyImportMenu : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.getItemId() == R.id.menuItemHelp) {
                openUrl(R.string.key_import_md_url, requireContext())
            } else {
                return false
            }
            return true
        }
    }

    companion object {
        private val TAG: String = KeyImportFragment::class.java.getSimpleName()
    }
}