package com.onemoresecret.msg_fragment_plugins

import android.net.Uri
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import com.onemoresecret.FileInfoFragment
import com.onemoresecret.FileOutputFragment
import com.onemoresecret.MessageFragment
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OmsFileProvider
import com.onemoresecret.R
import com.onemoresecret.Util
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Arrays
import java.util.Locale
import javax.crypto.Cipher

class MsgPluginEncryptedFile(
    messageFragment: MessageFragment,
    private val uri: Uri
) : MessageFragmentPlugin(messageFragment) {

    private val fileInfo: Util.UriFileInfo = Util.getFileInfo(context, uri)
    private var lastProgressPrc: Int = -1
    @Volatile
    private var destroyed: Boolean = false

    init {
        context.contentResolver.openInputStream(this.uri)?.use { `is` ->
            OmsDataInputStream(`is`).use { dataInputStream ->
                // read file header
                val rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream)
                rsaTransformation = rsaAesEnvelope.rsaTransformation
                fingerprint = rsaAesEnvelope.fingerprint
            }
        }
    }

    override fun getMessageView(): Fragment {
        if (msgView == null) {
            val fileInfoFragment = FileInfoFragment()
            msgView = fileInfoFragment
            fileInfoFragment.fileinfo = fileInfo
        }
        return msgView!!
    }

    override fun getOutputView(): FragmentWithNotificationBeforePause {
        if (outView == null) {
            outView = FileOutputFragment()
        }
        return outView!!
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        Thread {
            val masterRsaCipher = requireNotNull(result.cryptoObject).cipher
            assert(masterRsaCipher != null)

            val keyStoreEntry = CryptographyManager().getByFingerprint(fingerprint!!, preferences)
            assert(keyStoreEntry != null)

            val userRsaCipherBox = CryptographyManager().getInitializedUserRsaCipher(
                masterRsaCipher!!,
                keyStoreEntry!!,
                rsaTransformation!!,
                Cipher.DECRYPT_MODE
            )

            try {
                context.contentResolver.openInputStream(uri)?.use { `is` ->
                    OmsDataInputStream(`is`).use { dataInputStream ->

                        // re-read header to get to the start position of the encrypted data
                        val rsaAesEnvelope = MessageComposer.readRsaAesEnvelope(dataInputStream)

                        val aesSecretKeyData = userRsaCipherBox.cipher.doFinal(rsaAesEnvelope.encryptedAesSecretKey)
                        userRsaCipherBox.wipe.invoke() // wipe User RSA key data

                        lastProgressPrc = -1

                        try {
                            val fileRecord = OmsFileProvider.create(
                                context,
                                fileInfo.filename.substring(
                                    0,
                                    fileInfo.filename.length - (MessageComposer.OMS_FILE_TYPE.length + 1 /* the dot */)
                                ),
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
                                    { updateProgress(it) }
                                )

                                Arrays.fill(aesSecretKeyData, 0.toByte()) // wipe AES key data
                            }

                            if (destroyed) {
                                Files.delete(fileRecord.path)
                            } else {
                                updateProgress(fileInfo.fileSize) // 100%
                                (outView as FileOutputFragment).uri = fileRecord.uri
                            }
                        } catch (ex: Exception) {
                            Util.printStackTrace(ex)
                            activity.mainExecutor.execute {
                                Toast.makeText(
                                    context,
                                    String.format("%s: %s", ex.javaClass.simpleName, ex.message),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        // requireActivity().invalidateOptionsMenu()
                    }
                }
            } catch (e: Exception) {
                Util.printStackTrace(e)
                context.mainExecutor.execute {
                    Toast.makeText(
                        context,
                        e.message ?: String.format(context.getString(R.string.authentication_failed_s), e.javaClass.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    Util.discardBackStack(messageFragment)
                }
            }
        }.start()
    }

    private fun updateProgress(value: Int?) {
        var s = ""
        if (value != null) {
            val progressPrc = (value.toDouble() / fileInfo.fileSize.toDouble() * 100.0).toInt()
            if (lastProgressPrc == progressPrc) return

            lastProgressPrc = progressPrc
            s = if (lastProgressPrc == 100) {
                context.getString(R.string.done)
            } else {
                String.format(Locale.getDefault(), context.getString(R.string.working_prc), lastProgressPrc)
            }
        }

        (outView as FileOutputFragment).progressText = s
    }

    override fun onDestroyView() {
        destroyed = true
    }
}
