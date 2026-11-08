package com.onemoresecret.msg_fragment_plugins

import android.net.Uri
import android.content.Intent
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OmsFileProvider
import com.onemoresecret.R
import com.onemoresecret.Util
import com.onemoresecret.composable.FileInfoScreen
import com.onemoresecret.composable.FileOutputScreen
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.EncryptedFile
import com.onemoresecret.crypto.RsaTransformation
import com.onemoresecret.crypto.AesTransformation
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Arrays
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher

class MsgPluginEncryptedFile(
    activity: FragmentActivity,
    val uri: Uri,
    onNavigateBack: () -> Unit
) : MessageFragmentPlugin(activity, onNavigateBack) {

    private val fileInfo = Util.getFileInfo(activity, uri)
    private var decryptedUri by mutableStateOf<Uri?>(null)
    private var progressText by mutableStateOf("")
    @Volatile private var destroyed = false
    private var lastProgressPrc = -1
    private var isLegacy = false
    private var recipientPublicKey = ByteArray(0)
    private var updating by mutableStateOf(false)

    init {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            OmsDataInputStream(inputStream).use { dataInputStream ->
                val rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream)
                rsaTransformation = rsaAesEnvelope.rsaTransformation
                fingerprint = rsaAesEnvelope.fingerprint
                isLegacy = rsaAesEnvelope.protocolHeader == null
            }
        } ?: throw java.io.FileNotFoundException("Could not open input stream for URI: $uri")
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        Thread {
            try {
                val manager = CryptographyManager()
                val entry = requireNotNull(manager.getByFingerprint(requireNotNull(fingerprint), preferences))
                val userRsa = manager.getInitializedUserRsaCipher(requireNotNull(result.cryptoObject?.cipher), entry, requireNotNull(rsaTransformation), Cipher.DECRYPT_MODE)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        OmsDataInputStream(input).use { data ->
                            val envelope = MessageComposer.readRsaAesEnvelope(data)
                            val aes = userRsa.cipher.doFinal(envelope.encryptedAesSecretKey)
                            try {
                                val filename = fileInfo.filename.replace(Regex("\\.oms(?:00)?$"), "")
                                val record = OmsFileProvider.create(context, filename, true)
                                val staging = Files.createTempFile(requireNotNull(record.path).parent, ".decrypt-", ".part")
                                try {
                                    FileOutputStream(staging.toFile()).use { out ->
                                        val header = envelope.protocolHeader
                                        if (header != null) org.onemoresecret.protocol.OmsProtocol.decryptFile(header, aes, data, out, { destroyed }, { n -> updateProgress(n.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) })
                                        else AESUtil.process(Cipher.DECRYPT_MODE, data, out, aes, envelope.iv, envelope.aesTransformation, { destroyed }, { n -> updateProgress(n) })
                                    }
                                    if (destroyed) throw java.io.InterruptedIOException("Decryption cancelled")
                                    Files.move(staging, record.path, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                    updateProgress(fileInfo.fileSize)
                                    activity.mainExecutor.execute {
                                        recipientPublicKey = entry.public.copyOf()
                                        decryptedUri = record.uri
                                    }
                                } finally { Files.deleteIfExists(staging) }
                            } finally { aes.fill(0) }
                        }
                    } ?: throw java.io.FileNotFoundException("Could not open encrypted file")
                } finally { userRsa.wipe.invoke() }
            } catch (e: Exception) {
                Util.printStackTrace(e)
                activity.mainExecutor.execute {
                    Toast.makeText(context, e.message ?: "Decryption failed", Toast.LENGTH_LONG).show()
                    onNavigateBack()
                }
            }
        }.start()
    }

    private fun updateProgress(bytesProcessed: Int) {
        if (fileInfo.fileSize <= 0) return
        val progressPrc = (bytesProcessed.toDouble() / fileInfo.fileSize.toDouble() * 100.0).toInt()
        if (lastProgressPrc == progressPrc) return

        lastProgressPrc = progressPrc
        val s = if (lastProgressPrc == 100) {
            context.getString(R.string.done)
        } else {
            String.format(Locale.getDefault(), context.getString(R.string.working_prc), lastProgressPrc)
        }

        activity.mainExecutor.execute {
            progressText = s
        }
    }

    override val showVisibilityButton: Boolean
        get() = false

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        FileInfoScreen(fileInfo = fileInfo)
    }

    @Composable
    override fun OutputView() {
        Column {
            FileOutputScreen(uri = decryptedUri, progressText = progressText)
            if (isLegacy && decryptedUri != null) {
                Button(onClick = { updateFormat() }, enabled = !updating, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.update_backup_format))
                }
            }
        }
    }

    private fun updateFormat() {
        val source = decryptedUri ?: return
        if (updating || destroyed || !isLegacy) return
        updating = true
        val publicKey = recipientPublicKey.copyOf()
        Thread {
            var output: java.nio.file.Path? = null
            var shared = false
            try {
                val base = fileInfo.filename.replace(Regex("\\.oms(?:00)?$"), "")
                // Keep the plaintext extension (e.g. .pdf) after removing .oms on a later decrypt.
                val basename = base.takeLast(48).dropWhile { it.isLowSurrogate() }
                val record = OmsFileProvider.create(context, UUID.randomUUID().toString() + "_" + basename + ".oms", true)
                output = requireNotNull(record.path)
                context.contentResolver.openInputStream(source)?.use { input ->
                    EncryptedFile.create(input, requireNotNull(record.path).toFile(), publicKey,
                        RsaTransformation.entries[2], 256, AesTransformation.entries[2], { destroyed }, null)
                } ?: throw java.io.FileNotFoundException("Could not open decrypted file")
                if (destroyed) throw java.io.InterruptedIOException("Format update cancelled")
                activity.mainExecutor.execute {
                    try {
                        if (!destroyed) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/octet-stream"
                                putExtra(Intent.EXTRA_STREAM, record.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.updated_format_file)))
                        }
                    } catch (ex: Exception) {
                        Toast.makeText(context, ex.message, Toast.LENGTH_LONG).show()
                    } finally { updating = false }
                }
                shared = true
            } catch (ex: Exception) {
                activity.mainExecutor.execute {
                    updating = false
                    if (!destroyed) Toast.makeText(context, ex.message, Toast.LENGTH_LONG).show()
                }
            } finally {
                publicKey.fill(0)
                if (!shared) {
                    try { output?.let { Files.deleteIfExists(it) } }
                    catch (ex: java.io.IOException) { Util.printStackTrace(ex) }
                }
            }
        }.start()
    }

    override fun onDestroyView() {
        destroyed = true
    }
}
