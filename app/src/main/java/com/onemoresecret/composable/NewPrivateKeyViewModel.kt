package com.onemoresecret.composable

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.text.Html
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.zxing.WriterException
import com.onemoresecret.OmsFileProvider.Companion.create
import com.onemoresecret.R
import com.onemoresecret.Util
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.crypto.AESUtil.generateSalt
import com.onemoresecret.crypto.AESUtil.getAesKeyAlgorithm
import com.onemoresecret.crypto.AESUtil.getAesKeyMaterialFromPassword
import com.onemoresecret.crypto.AESUtil.getAesTransformation
import com.onemoresecret.crypto.AESUtil.getKeyLength
import com.onemoresecret.crypto.AESUtil.getKeySpecIterations
import com.onemoresecret.crypto.AESUtil.getSaltLength
import com.onemoresecret.crypto.AesEncryptedPrivateKeyTransfer
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.CryptographyManager.Companion.generateKeyPair
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.crypto.RSAUtil.getFingerprint
import com.onemoresecret.crypto.RSAUtil.restorePublicKey
import com.onemoresecret.qr.QRUtil
import com.onemoresecret.qr.QRUtil.getQrSequence
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import kotlin.math.min

class NewPrivateKeyViewModel(
    private val preferences: SharedPreferences, private val cryptographyManager: CryptographyManager
) : ViewModel() {
    var state by mutableStateOf(State())
    private val _fragmentEvent = MutableSharedFlow<(Fragment) -> Unit>()
    val fragmentEvent = _fragmentEvent.asSharedFlow()
    var onActivate: () -> Unit = {}

    fun onAction(action: Action) {
        state = when (action) {
            is Action.OnAliasChanged -> state.copy(alias = action.value)
            is Action.OnPasswordChanged -> state.copy(password = action.value)
            is Action.OnRepeatPasswordChanged -> state.copy(repeatPassword = action.value)
            is Action.On4096BitChanged -> state.copy(is4096Bit = action.enabled)
            is Action.OnBackupConsentChanged -> state.copy(isConsentChecked = action.enabled)
        }
    }

    fun createPrivateKey() {
        viewModelScope.launch {
            try {
                if (state.alias.isEmpty()) {
                    _fragmentEvent.emit { fragment ->
                        Toast.makeText(
                            fragment.context,
                            fragment.getString(R.string.key_alias_may_not_be_empty),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                if (cryptographyManager.keyStore.containsAlias(state.alias)) {
                    _fragmentEvent.emit { fragment ->
                        Toast.makeText(
                            fragment.context,
                            fragment.getString(R.string.key_alias_already_exists),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                if (state.password.length < 10) {
                    _fragmentEvent.emit { fragment ->
                        Toast.makeText(
                            fragment.context,
                            fragment.getString(R.string.password_too_short),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                if (state.password != state.repeatPassword) {
                    _fragmentEvent.emit { fragment ->
                        Toast.makeText(
                            fragment.context,
                            fragment.getString(R.string.password_mismatch),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val keyMaterial = generateKeyPair(if (state.is4096Bit) 4096 else 2048)
                val publicKeyMaterial = keyMaterial.first
                val privateKeyMaterial = keyMaterial.second
                val publicKey = restorePublicKey(publicKeyMaterial)
                val fingerprint = getFingerprint(publicKey)
                val ivSize = getAesTransformation(preferences).ivLength
                val iv = ByteArray(ivSize)
                SecureRandom().nextBytes(iv)
                val salt = generateSalt(getSaltLength(preferences))
                val aesKeyLength = getKeyLength(preferences)
                val aesKeySpecIterations = getKeySpecIterations(preferences)
                val aesKeyAlgorithm = getAesKeyAlgorithm(preferences).keyAlgorithm

                val aesKeyMaterial = getAesKeyMaterialFromPassword(
                    state.password.toCharArray(),
                    salt,
                    aesKeyAlgorithm,
                    aesKeyLength,
                    aesKeySpecIterations
                )

                val message = AesEncryptedPrivateKeyTransfer(
                    state.alias,
                    privateKeyMaterial,
                    publicKeyMaterial,
                    aesKeyMaterial,
                    iv,
                    salt,
                    getAesTransformation(preferences),
                    getAesKeyAlgorithm(preferences),
                    aesKeyLength,
                    aesKeySpecIterations
                ).message

                Arrays.fill(aesKeyMaterial, 0.toByte())

                onActivate = {
                    viewModelScope.launch {
                        try {
                            cryptographyManager.importRsaKey(
                                preferences, privateKeyMaterial, publicKeyMaterial, state.alias
                            )

                            Arrays.fill(privateKeyMaterial, 0.toByte()) //wipe private key data
                            _fragmentEvent.emit { fragment ->
                                Toast.makeText(
                                    fragment.context,
                                    fragment.getString(R.string.key_successfully_activated),
                                    Toast.LENGTH_LONG
                                ).show()
                                //go back
                                discardBackStack(fragment)
                            }
                        } catch (ex: Exception) {
                            printStackTrace(ex)
                            _fragmentEvent.emit { fragment ->
                                Toast.makeText(
                                    fragment.context, ex.message, Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                state = state.copy(isKeyCreated = true)

                //share HTML file
                val html = getKeyBackupHtml(state.alias, fingerprint, message)
                val fingerprintString = byteArrayToHex(fingerprint).replace("\\s".toRegex(), "_")

                _fragmentEvent.emit { fragment ->
                    val fileRecord =
                        create(fragment.requireContext(), "pk_$fingerprintString.html", true)
                    Files.write(fileRecord.path, html.toByteArray(StandardCharsets.UTF_8))
                    fileRecord.path!!.toFile().deleteOnExit()

                    val intent = Intent()
                    intent.setAction(Intent.ACTION_SEND)
                    intent.putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.setType("text/html")
                    fragment.startActivity(
                        Intent.createChooser(
                            intent,
                            String.format(fragment.getString(R.string.backup_file), state.alias)
                        )
                    )
                }
            } catch (ex: Exception) {
                printStackTrace(ex)
                _fragmentEvent.emit { fragment ->
                    Toast.makeText(
                        fragment.context, ex.message ?: ex.toString(), Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    @Throws(WriterException::class, IOException::class)
    private fun getKeyBackupHtml(
        alias: String?, fingerprint: ByteArray, message: ByteArray
    ): String = buildString {
        val list = getQrSequence(
            encodeAsOmsText(message),
            QRUtil.getChunkSize(preferences),
            QRUtil.getBarcodeSize(preferences)
        )
        append(
            """
            <html><body><h1>OneMoreSecret Private Key Backup</h1>            
            <p><b>Keep this file / printout in a secure location</b></p><p>
            This is a hard copy of your Private Key for OneMoreSecret. 
            It can be used to import your Private Key into a new device or after a reset of OneMoreSecret App.
            This document is encrypted with AES, you will need your TRANSPORT PASSWORD to complete the import procedure.</p>            
            <h2>WARNING:</h2>            
            <p>DO NOT share this document with other persons.<br>
            DO NOT provide its content to untrusted apps, on the Internet etc.<br>
            If you need to restore your Key, start OneMoreSecret App on your phone BY HAND and scan the codes. 
            DO NOT trust unexpected prompts and pop-ups.<br>            
            <b>THIS DOCUMENT IS THE ONLY WAY TO RESTORE YOUR PRIVATE KEY</b></p>            
            <p><b>Key alias:&nbsp;${Html.escapeHtml(alias)}</b></p>            
            <p><b>RSA Fingerprint:&nbsp;${byteArrayToHex(fingerprint)}</b></p>            
            <p>Scan this with your OneMoreSecret App:</p><p>
            """.trimIndent()
        )

        for (i in list.indices) {
            val bitmap = list[i]
            ByteArrayOutputStream().use { baos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.flush()
                append(
                    """
                    <table style="display: inline-block;">                
                    <tr style="vertical-align: bottom;">                
                    <td>${(i + 1)}</td>                
                    <td>                
                    <img src="data:image/png;base64,${
                        Base64.getEncoder().encodeToString(baos.toByteArray())
                    }" style="width:200px;height:200px;">                    
                    </td>                    
                    </tr>                
                    </table>
                    """.trimIndent()
                )
            }
        }
        append(
            """
            </p>                
            <h2>Long-Term Backup and Technical Details</h2>                
            <p>Base64 Encoded Message:&nbsp;</p>                
            <p style="font-family:monospace;">
            """.trimIndent()
        )

        val messageAsUrl = encodeAsOmsText(message)
        var offset = 0

        while (offset < messageAsUrl.length) {
            val s = messageAsUrl.substring(
                offset, min(offset + Util.BASE64_LINE_LENGTH, messageAsUrl.length)
            )
            append(s).append("<br>")
            offset += Util.BASE64_LINE_LENGTH
        }

        append(
            """
            </p>            
            <p>Message format: oms00_[base64 encoded data]"</p>            
            <p>Data format: see https://github.com/stud0709/OneMoreSecret/blob/master/app/src/main/java/com/onemoresecret/crypto/AesEncryptedPrivateKeyTransfer.java</p>
            </body>            
            </html>
            """.trimIndent()
        )
    }

    sealed class Action {
        data class OnAliasChanged(val value: String) : Action()
        data class OnPasswordChanged(val value: String) : Action()
        data class OnRepeatPasswordChanged(val value: String) : Action()
        data class On4096BitChanged(val enabled: Boolean) : Action()
        data class OnBackupConsentChanged(val enabled: Boolean) : Action()
    }

    data class State(
        val alias: String = "",
        val password: String = "",
        val repeatPassword: String = "",
        val is4096Bit: Boolean = false,
        val isConsentChecked: Boolean = false,
        val isKeyCreated: Boolean = false
    )

    class Factory(private val preferences: SharedPreferences, private val cryptographyManager: CryptographyManager) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NewPrivateKeyViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return NewPrivateKeyViewModel(preferences, cryptographyManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}