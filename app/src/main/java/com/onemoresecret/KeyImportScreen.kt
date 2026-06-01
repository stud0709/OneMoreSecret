package com.onemoresecret

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.composable.KeyImportScreen as KeyImportUI
import com.onemoresecret.crypto.AESUtil.getAesKeyMaterialFromPassword
import com.onemoresecret.crypto.AESUtil.process
import com.onemoresecret.crypto.AesKeyAlgorithm
import com.onemoresecret.crypto.AesTransformation
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil.getFingerprint
import com.onemoresecret.crypto.RSAUtil.restorePublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Arrays
import javax.crypto.Cipher

@Composable
fun KeyImportScreen(
    message: ByteArray,
    onImportCompleted: () -> Unit,
    viewModel: KeyImportViewModel = viewModel()
) {
    val context = LocalContext.current
    val strUnknownDecryptFirst = androidx.compose.ui.res.stringResource(R.string.unknown_decrypt_first)

    LaunchedEffect(message) {
        try {
            viewModel.init(message)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: e.javaClass.name,
                Toast.LENGTH_LONG
            ).show()
            onImportCompleted()
        }
    }

    KeyImportUI(
        alias = viewModel.keyAlias,
        passphrase = viewModel.passphrase,
        fingerprint = if (viewModel.fingerprint.isNotBlank()) {
            "…%s".format(viewModel.fingerprint.takeLast(10))
        } else {
            strUnknownDecryptFirst
        },
        warning = viewModel.warning,
        saveEnabled = viewModel.saveEnabled,
        onAliasChange = {
            viewModel.keyAlias = it
            viewModel.validateAlias(context, viewModel.decryptedFingerprintBytes)
        },
        onPassphraseChange = { viewModel.passphrase = it },
        onDecrypt = { viewModel.onDecryptClicked(context) },
        onSave = { viewModel.onSaveClicked(context, onImportCompleted) }
    )
}

class KeyImportViewModel : ViewModel() {
    private val cryptographyManager = CryptographyManager()

    var keyAlias by mutableStateOf("")
    var passphrase by mutableStateOf("")
    var fingerprint by mutableStateOf("")
    var warning by mutableStateOf("")
    var saveEnabled by mutableStateOf(false)

    var decryptedPrivateKeyMaterial: ByteArray? = null
    var decryptedPublicKeyMaterial: ByteArray? = null
    var decryptedFingerprintBytes: ByteArray? = null
    private var encryptedPayload: EncryptedPayload? = null

    fun init(message: ByteArray) {
        if (encryptedPayload != null) return
        OmsDataInputStream(ByteArrayInputStream(message)).use { dataInputStream ->
            val applicationId = dataInputStream.readUnsignedShort()
            require(applicationId == MessageComposer.APPLICATION_AES_ENCRYPTED_PRIVATE_KEY_TRANSFER) { "wrong applicationId: $applicationId" }

            keyAlias = dataInputStream.readString()

            val salt = dataInputStream.readByteArray()
            val iv = dataInputStream.readByteArray()
            val aesTransformation = AesTransformation.entries[dataInputStream.readUnsignedShort()]
            val aesKeyAlg = AesKeyAlgorithm.entries[dataInputStream.readUnsignedShort()].keyAlgorithm
            val aesKeyLength = dataInputStream.readUnsignedShort()
            val iterations = dataInputStream.readUnsignedShort()
            val cipherText = dataInputStream.readByteArray()

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
    }

    fun onDecryptClicked(context: Context) {
        val payload = encryptedPayload ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
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

                OmsDataInputStream(ByteArrayInputStream(bArr)).use { dataInputStream ->
                    val privateKeyMaterial = dataInputStream.readByteArray()
                    val publicKeyMaterial = dataInputStream.readByteArray()

                    val publicKey = restorePublicKey(publicKeyMaterial)
                    val fingerprintBytes = getFingerprint(publicKey)
                    val fingerprintString = byteArrayToHex(fingerprintBytes)

                    withContext(Dispatchers.Main) {
                        decryptedPrivateKeyMaterial = privateKeyMaterial
                        decryptedPublicKeyMaterial = publicKeyMaterial
                        decryptedFingerprintBytes = fingerprintBytes
                        fingerprint = fingerprintString
                        saveEnabled = true
                        validateAlias(context, fingerprintBytes)
                    }
                }
            } catch (ex: Exception) {
                printStackTrace(ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Could not decrypt. Wrong passphrase?",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    fun validateAlias(context: Context, fingerprintNew: ByteArray?) {
        try {
            val alias = keyAlias
            var warningText: String? = null
            val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

            cryptographyManager.getByAlias(alias, preferences)?.let {
                if (!it.fingerprint.contentEquals(fingerprintNew)) {
                    warningText = context.getString(R.string.warning_alias_exists)
                        .format(
                            byteArrayToHex(it.fingerprint).takeLast(10)
                        )
                }
            }

            fingerprintNew?.let {
                val sameFingerprint = cryptographyManager.getByFingerprint(it, preferences)
                if (sameFingerprint != null) {
                    warningText = context.getString(R.string.warning_same_fingerprint).format(
                        sameFingerprint.alias
                    )
                }
            }

            warning = warningText ?: ""
        } catch (e: Exception) {
            printStackTrace(e)
        }
    }

    fun onSaveClicked(context: Context, onImportCompleted: () -> Unit) {
        if (!saveEnabled) return

        val fingerprintBytes = decryptedFingerprintBytes ?: return
        val privateKeyMaterial = decryptedPrivateKeyMaterial ?: return
        val publicKeyMaterial = decryptedPublicKeyMaterial ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

                val sameFingerprint = cryptographyManager.getByFingerprint(
                    fingerprintBytes,
                    preferences
                )
                if (sameFingerprint != null) {
                    cryptographyManager.deleteKey(sameFingerprint.alias, preferences)
                }

                val alias = keyAlias.trim { it <= ' ' }

                cryptographyManager.deleteKey(alias, preferences)

                cryptographyManager.importRsaKey(
                    preferences,
                    privateKeyMaterial,
                    publicKeyMaterial,
                    alias
                )

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Private key '$alias' successfully imported",
                        Toast.LENGTH_LONG
                    ).show()
                    onImportCompleted()
                }
            } catch (ex: Exception) {
                printStackTrace(ex)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        ex.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    data class EncryptedPayload(
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
