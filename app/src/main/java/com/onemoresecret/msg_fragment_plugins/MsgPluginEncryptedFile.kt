package com.onemoresecret.msg_fragment_plugins

import android.net.Uri
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
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
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Arrays
import java.util.Locale
import javax.crypto.Cipher

class MsgPluginEncryptedFile(
    activity: FragmentActivity,
    val uri: Uri,
    onNavigateBack: () -> Unit
) : MessageFragmentPlugin(activity, onNavigateBack) {

    private val fileInfo = Util.getFileInfo(activity, uri)
    private var decryptedUri by mutableStateOf<Uri?>(null)
    private var progressText by mutableStateOf("")
    private var destroyed = false
    private var lastProgressPrc = -1

    init {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            OmsDataInputStream(inputStream).use { dataInputStream ->
                val rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream)
                rsaTransformation = rsaAesEnvelope.rsaTransformation
                fingerprint = rsaAesEnvelope.fingerprint
            }
        } ?: throw java.io.FileNotFoundException("Could not open input stream for URI: $uri")
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        Thread {
            try {
                val masterRsaCipher = result.cryptoObject!!.cipher
                assert(masterRsaCipher != null)

                val keyStoreEntry = CryptographyManager().getByFingerprint(fingerprint!!, preferences)
                assert(keyStoreEntry != null)

                val userRsaCipherBox = CryptographyManager().getInitializedUserRsaCipher(
                    masterRsaCipher!!,
                    keyStoreEntry!!,
                    rsaTransformation!!,
                    Cipher.DECRYPT_MODE
                )

                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    OmsDataInputStream(inputStream).use { dataInputStream ->
                        // Re-read header to get to the start position of the encrypted data
                        val rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream)

                        val aesSecretKeyData = userRsaCipherBox.cipher.doFinal(rsaAesEnvelope.encryptedAesSecretKey)
                        userRsaCipherBox.wipe.invoke() // wipe User RSA key data

                        lastProgressPrc = -1

                        try {
                            val dotIndex = fileInfo.filename.lastIndexOf('.')
                            val nameWithoutExtension = if (dotIndex > 0) fileInfo.filename.substring(0, dotIndex) else fileInfo.filename
                            val fileRecord = OmsFileProvider.create(
                                context,
                                nameWithoutExtension,
                                true
                            )

                            FileOutputStream(fileRecord.path!!.toFile()).use { fos ->
                                AESUtil.process(
                                    Cipher.DECRYPT_MODE,
                                    dataInputStream,
                                    fos,
                                    aesSecretKeyData,
                                    rsaAesEnvelope.iv,
                                    rsaAesEnvelope.aesTransformation,
                                    { destroyed },
                                    { progress -> updateProgress(progress) }
                                )

                                Arrays.fill(aesSecretKeyData, 0.toByte()) // wipe AES key data
                            }

                            if (destroyed) {
                                Files.delete(fileRecord.path)
                            } else {
                                updateProgress(fileInfo.fileSize) // 100%
                                activity.mainExecutor.execute {
                                    decryptedUri = fileRecord.uri
                                }
                            }
                        } catch (ex: Exception) {
                            Util.printStackTrace(ex)
                            activity.mainExecutor.execute {
                                Toast.makeText(
                                    context,
                                    "${ex.javaClass.simpleName}: ${ex.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Util.printStackTrace(e)
                activity.mainExecutor.execute {
                    val errMsg = e.message ?: String.format(context.getString(R.string.authentication_failed_s), e.javaClass.name)
                    Toast.makeText(context, errMsg, Toast.LENGTH_SHORT).show()
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
        FileOutputScreen(uri = decryptedUri, progressText = progressText)
    }

    override fun onDestroyView() {
        destroyed = true
    }
}
