package com.onemoresecret

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.openUrl
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.composable.PasswordField
import com.onemoresecret.crypto.AesKeyAlgorithm
import com.onemoresecret.crypto.AesTransformation
import com.onemoresecret.crypto.PrivateKeyBackup
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil.getFingerprint
import com.onemoresecret.crypto.RSAUtil.restorePublicKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.UUID
import java.io.ByteArrayInputStream


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyImportScreen(
    message: ByteArray,
    onImportCompleted: () -> Unit,
    viewModel: KeyImportViewModel = viewModel()
) {
    val context = LocalContext.current
    val strUnknownDecryptFirst = stringResource(R.string.unknown_decrypt_first)
    val strBackupFile = stringResource(R.string.backup_file)

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.private_key_import), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { openUrl(R.string.key_import_md_url, context) }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Help, contentDescription = stringResource(R.string.help))
                    }
                }
            )
        }
    ) { innerPadding ->
        KeyImportUI(
            modifier = Modifier.padding(innerPadding),
            alias = viewModel.keyAlias,
            passphrase = viewModel.passphrase,
            fingerprint = if (viewModel.fingerprint.isNotBlank()) {
                "…%s".format(viewModel.fingerprint.takeLast(10))
            } else {
                strUnknownDecryptFirst
            },
            warning = viewModel.warning,
            saveEnabled = viewModel.saveEnabled,
            isBusy = viewModel.isBusy,
            onAliasChange = {
                viewModel.keyAlias = it
                viewModel.validateAlias(context, viewModel.decryptedFingerprintBytes)
            },
            onPassphraseChange = { viewModel.passphrase = it },
            onDecrypt = { viewModel.onDecryptClicked(context) },
            onSave = { viewModel.onSaveClicked(context, onImportCompleted) },
            onUpdateFormat = {
                viewModel.onUpdateFormatClicked(context) { uri, alias ->
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        type = "text/html"
                    }
                    context.startActivity(Intent.createChooser(intent, String.format(strBackupFile, alias)))
                }
            }
        )
    }
}

class KeyImportViewModel : ViewModel() {
    private val cryptographyManager = CryptographyManager()

    var keyAlias by mutableStateOf("")
    var passphrase by mutableStateOf("")
    var fingerprint by mutableStateOf("")
    var warning by mutableStateOf("")
    var saveEnabled by mutableStateOf(false)
    var isBusy by mutableStateOf(false)
        private set

    var decryptedPrivateKeyMaterial: ByteArray? = null
    var decryptedPublicKeyMaterial: ByteArray? = null
    var decryptedFingerprintBytes: ByteArray? = null
    private var encryptedPayload: EncryptedPayload? = null
    private var originalBackup = ByteArray(0)
    private var protocolHeader: org.onemoresecret.protocol.OmsProtocol.Header? = null

    fun init(message: ByteArray) {
        if (encryptedPayload != null) return
        originalBackup = message.copyOf()
        OmsDataInputStream(ByteArrayInputStream(message)).use { dataInputStream ->
            if (org.onemoresecret.protocol.OmsProtocol.hasMagic(message)) {
                val h = org.onemoresecret.protocol.OmsProtocol.readHeader(dataInputStream)
                require(h.kind == org.onemoresecret.protocol.OmsProtocol.PASSWORD) { "Not a key backup" }
                protocolHeader = h
                encryptedPayload = EncryptedPayload(h.salt, h.noncePrefix, dataInputStream.readBytes(), AesTransformation.AES_GCM_NO_PADDING, "PBKDF2WithHmacSHA256", 256, h.iterations)
                return
            }
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
        if (encryptedPayload == null || isBusy) return
        isBusy = true
        saveEnabled = false
        val password = passphrase.toCharArray()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = org.onemoresecret.protocol.BackupCodec.open(originalBackup, password)
                val fingerprintBytes = getFingerprint(restorePublicKey(result.publicKey))
                withContext(Dispatchers.Main) {
                    keyAlias = result.alias
                    decryptedPrivateKeyMaterial?.fill(0)
                    decryptedPrivateKeyMaterial = result.privateKey
                    decryptedPublicKeyMaterial = result.publicKey
                    decryptedFingerprintBytes = fingerprintBytes
                    fingerprint = byteArrayToHex(fingerprintBytes)
                    saveEnabled = true
                    validateAlias(context, fingerprintBytes)
                }
            } catch (ex: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "Could not decrypt or validate key backup. Wrong passphrase?", Toast.LENGTH_LONG).show() }
            } finally {
                password.fill('\u0000')
                withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { isBusy = false }
            }
        }
    }

    fun onUpdateFormatClicked(context: Context, onBackupReady: (android.net.Uri, String) -> Unit) {
        if (!saveEnabled || isBusy) return
        val alias = keyAlias.trim()
        if (alias.isEmpty()) {
            Toast.makeText(context, R.string.key_alias_may_not_be_empty, Toast.LENGTH_LONG).show()
            return
        }
        // Snapshot the validated pair before background work; screen disposal clears the originals.
        val privateKey = decryptedPrivateKeyMaterial?.copyOf() ?: return
        val publicKey = decryptedPublicKeyMaterial?.copyOf() ?: run { privateKey.fill(0); return }
        val password = passphrase.toCharArray()
        isBusy = true
        viewModelScope.launch {
            var output: java.nio.file.Path? = null
            var shared = false
            try {
                val record = withContext(Dispatchers.IO) {
                    val preferences = OmsPreferences.get(context)
                    val message = PrivateKeyBackup.create(alias, privateKey, publicKey, password,
                        com.onemoresecret.crypto.AESUtil.getKeySpecIterations(preferences))
                    val fingerprint = getFingerprint(restorePublicKey(publicKey))
                    val html = PrivateKeyBackupDocument.createHtml(preferences, alias, fingerprint, message)
                    val fingerprintText = byteArrayToHex(fingerprint).replace(" ", "_")
                    val file = OmsFileProvider.create(context, "pk_" + fingerprintText + "_" + UUID.randomUUID() + ".html", true)
                    output = requireNotNull(file.path)
                    Files.write(output, html.toByteArray(Charsets.UTF_8))
                    file
                }
                onBackupReady(requireNotNull(record.uri), alias)
                shared = true
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                printStackTrace(ex)
                Toast.makeText(context, ex.message ?: context.getString(R.string.could_not_update_backup), Toast.LENGTH_LONG).show()
            } finally {
                privateKey.fill(0)
                publicKey.fill(0)
                password.fill('\u0000')
                isBusy = false
                if (!shared) {
                    try { output?.let { Files.deleteIfExists(it) } }
                    catch (ex: java.io.IOException) { printStackTrace(ex) }
                }
            }
        }
    }

    override fun onCleared() {
        decryptedPrivateKeyMaterial?.fill(0)
        originalBackup.fill(0)
        passphrase = ""
        super.onCleared()
    }

    fun validateAlias(context: Context, fingerprintNew: ByteArray?) {
        try {
            val alias = keyAlias
            var warningText: String? = null
            val preferences = com.onemoresecret.OmsPreferences.get(context)

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
        if (!saveEnabled || isBusy) return

        val fingerprintBytes = decryptedFingerprintBytes ?: return
        val privateKeyMaterial = decryptedPrivateKeyMaterial ?: return
        val publicKeyMaterial = decryptedPublicKeyMaterial ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val preferences = com.onemoresecret.OmsPreferences.get(context)

                val alias = keyAlias.trim()

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



@Composable
fun KeyImportUI(
    modifier: Modifier = Modifier,
    alias: String,
    passphrase: String,
    fingerprint: String,
    warning: String,
    saveEnabled: Boolean,
    isBusy: Boolean,
    onAliasChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onDecrypt: () -> Unit,
    onSave: () -> Unit,
    onUpdateFormat: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .animateContentSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = stringResource(R.string.key_alias))
        OutlinedTextField(
            value = alias,
            onValueChange = onAliasChange,
            enabled = !isBusy,
            label = { Text(stringResource(R.string.key_alias)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(visible = !saveEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.transport_passphrase_required))
                PasswordField(
                    value = passphrase,
                    enabled = !saveEnabled && !isBusy,
                    onValueChange = onPassphraseChange,
                    label = stringResource(R.string.passphrase),
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onDecrypt,
                    enabled = !saveEnabled && !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.decrypt))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Fingerprint,
                    contentDescription = stringResource(id = R.string.fingerprint),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )

                Text(
                    text = fingerprint,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = onSave,
                enabled = saveEnabled && !isBusy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.save))
            }

            AnimatedVisibility(visible = saveEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onUpdateFormat,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.update_backup_format)) }
                    Text(
                        text = stringResource(R.string.update_backup_format_description),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                text = warning,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
