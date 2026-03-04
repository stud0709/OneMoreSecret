package com.onemoresecret

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.openUrl
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.composable.KeyImportScreen
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.crypto.AESUtil.getAesKeyMaterialFromPassword
import com.onemoresecret.crypto.AESUtil.process
import com.onemoresecret.crypto.AesKeyAlgorithm
import com.onemoresecret.crypto.AesTransformation
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.CryptographyManager.Companion.PROP_KEYSTORE
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil.getFingerprint
import com.onemoresecret.crypto.RSAUtil.restorePublicKey
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
    private val cryptographyManager = CryptographyManager()
    private val menuProvider = KeyImportMenu()
    private lateinit var preferences: SharedPreferences

    private var keyAlias by mutableStateOf("")
    private var passphrase by mutableStateOf("")
    private var fingerprint by mutableStateOf("")
    private var warning by mutableStateOf("")
    private var saveEnabled by mutableStateOf(false)

    private var decryptedPrivateKeyMaterial: ByteArray? = null
    private var decryptedPublicKeyMaterial: ByteArray? = null
    private var decryptedFingerprintBytes: ByteArray? = null
    private var encryptedPayload: EncryptedPayload? = null

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
                keyAlias = alias

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
                val aesKeyAlg: String =
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

                encryptedPayload = EncryptedPayload(
                    salt = salt,
                    iv = iv,
                    cipherText = cipherText,
                    aesTransformation = aesTransformation,
                    keyAlg = aesKeyAlg,
                    keyLength = aesKeyLength,
                    iterations = iterations
                )
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

    private fun onDecryptClicked() {
        val payload = encryptedPayload ?: return
        Thread {
            onPasswordEntry(payload)
        }.start()
    }

    private fun onPasswordEntry(payload: EncryptedPayload) {
        try {
            //try decrypt
            val aesKeyMaterial = getAesKeyMaterialFromPassword(
                passphrase.toCharArray(),
                payload.salt,
                payload.keyAlg,
                payload.keyLength,
                payload.iterations
            )

            val bArr = process(
                Cipher.DECRYPT_MODE,
                payload.cipherText,
                aesKeyMaterial,
                payload.iv,
                payload.aesTransformation
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
                    val fingerprintString = byteArrayToHex(fingerprintBytes)
                    requireContext().mainExecutor.execute {
                        decryptedPrivateKeyMaterial = privateKeyMaterial
                        decryptedPublicKeyMaterial = publicKeyMaterial
                        decryptedFingerprintBytes = fingerprintBytes
                        fingerprint = fingerprintString
                        saveEnabled = true
                        validateAlias(fingerprintBytes)
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

    private fun onSaveClicked() {
        if (!saveEnabled) return

        val fingerprintBytes = decryptedFingerprintBytes ?: return
        val privateKeyMaterial = decryptedPrivateKeyMaterial ?: return
        val publicKeyMaterial = decryptedPublicKeyMaterial ?: return

        Thread {
            try {
                //delete other keys with the same fingerprint
                val sameFingerprint = cryptographyManager.getByFingerprint(
                    fingerprintBytes,
                    preferences
                )
                if (sameFingerprint != null) {
                    cryptographyManager.deleteKey(sameFingerprint.alias, preferences)
                }

                val alias = keyAlias.trim { it <= ' ' }
                cryptographyManager.importRsaKey(
                    preferences,
                    privateKeyMaterial,
                    publicKeyMaterial,
                    alias
                )

                //neuer Keystore
                cryptographyManager.importRsaKey(
                    requireActivity().getPreferences(Context.MODE_PRIVATE),
                    privateKeyMaterial,
                    publicKeyMaterial,
                    alias
                )

                requireContext().mainExecutor.execute {
                    Toast.makeText(
                        this.context,
                        "Private key '$alias' successfully imported",
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    KeyImportScreen(
                        alias = keyAlias,
                        passphrase = passphrase,
                        fingerprint = if (fingerprint.isNotBlank()) {
                            "…%s".format(fingerprint.takeLast(10))
                        } else {
                            getString(R.string.unknown_decrypt_first)
                        },
                        warning = warning,
                        saveEnabled = saveEnabled,
                        onAliasChange = {
                            keyAlias = it
                            validateAlias(decryptedFingerprintBytes)
                        },
                        onPassphraseChange = { passphrase = it },
                        onDecrypt = { onDecryptClicked() },
                        onSave = { onSaveClicked() }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
    }

    private fun validateAlias(fingerprintNew: ByteArray?) {
        try {
            val alias = keyAlias
            Log.d(TAG, "alias: $alias")
            var warningText: String? = null

            cryptographyManager.getByAlias(alias, preferences)?.let {
                if (!it.fingerprint.contentEquals(fingerprintNew)) {
                    warningText = getString(R.string.warning_alias_exists)
                        .format(
                            byteArrayToHex(it.fingerprint).takeLast(
                                10
                            )
                        )
                }
            }

            fingerprintNew?.let {
                val sameFingerprint =
                    cryptographyManager.getByFingerprint(it, preferences)
                if (sameFingerprint != null) {
                    warningText = getString(R.string.warning_same_fingerprint).format(
                        sameFingerprint.alias
                    )
                }
            }

            requireContext().mainExecutor.execute {
                warning = warningText ?: ""
            }
        } catch (e: Exception) {
            printStackTrace(e)
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
        private val TAG: String = KeyImportFragment::class.java.getSimpleName()
    }

    private data class EncryptedPayload(
        val salt: ByteArray,
        val iv: ByteArray,
        val cipherText: ByteArray,
        val aesTransformation: AesTransformation,
        val keyAlg: String,
        val keyLength: Int,
        val iterations: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EncryptedPayload) return false

            if (keyLength != other.keyLength) return false
            if (iterations != other.iterations) return false
            if (!salt.contentEquals(other.salt)) return false
            if (!iv.contentEquals(other.iv)) return false
            if (!cipherText.contentEquals(other.cipherText)) return false
            if (aesTransformation != other.aesTransformation) return false
            if (keyAlg != other.keyAlg) return false

            return true
        }

        override fun hashCode(): Int {
            var result = keyLength
            result = 31 * result + iterations
            result = 31 * result + salt.contentHashCode()
            result = 31 * result + iv.contentHashCode()
            result = 31 * result + cipherText.contentHashCode()
            result = 31 * result + aesTransformation.hashCode()
            result = 31 * result + keyAlg.hashCode()
            return result
        }
    }
}
