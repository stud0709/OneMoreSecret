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
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.getFileInfo
import com.onemoresecret.crypto.AESUtil.process
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.MessageComposer.Companion.readRsaAesEnvelope
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Locale
import java.util.Objects
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class MsgPluginEncryptedFile(
    messageFragment: MessageFragment,
    private val uri: Uri
) : MessageFragmentPlugin(messageFragment) {
    private val fileInfo = getFileInfo(context, uri)
    private var lastProgressPrc = 0
    private var destroyed = false

    init {
        context.contentResolver.openInputStream(this.uri).use { `is` ->
            OmsDataInputStream(`is`).use { dataInputStream ->
                //read file header
                val rsaAesEnvelope = readRsaAesEnvelope(dataInputStream)
                rsaTransformation = rsaAesEnvelope.rsaTransormation
                fingerprint = rsaAesEnvelope.fingerprint
            }
        }
    }

    override fun getMessageView(): Fragment? {
        if (messageView == null) {
            val fileInfoFragment = FileInfoFragment()
            messageView = fileInfoFragment
            context.mainExecutor.execute {
                fileInfoFragment.setValues(
                    fileInfo.filename,
                    fileInfo.fileSize
                )
            }
        }
        return messageView
    }

    override fun getOutputView(): FragmentWithNotificationBeforePause? {
        if (outputView == null) outputView = FileOutputFragment()

        return outputView
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        Thread {
            val cipher = result.cryptoObject!!.cipher
            try {
                context.contentResolver.openInputStream(uri).use { `is` ->
                    OmsDataInputStream(`is`).use { dataInputStream ->
                        checkNotNull(cipher)
                        //re-read header to get to the start position of the encrypted data
                        val rsaAesEnvelope = readRsaAesEnvelope(dataInputStream)

                        val aesSecretKeyData = cipher.doFinal(rsaAesEnvelope.encryptedAesSecretKey)
                        val aesSecretKey = SecretKeySpec(aesSecretKeyData, "AES")
                        lastProgressPrc = -1
                        try {
                            val fileRecord = OmsFileProvider.create(
                                context,
                                fileInfo.filename.substring(
                                    0,
                                    fileInfo.filename.length - (MessageComposer.OMS_FILE_TYPE.length + 1 /*the dot*/)
                                ),
                                true
                            )

                            FileOutputStream(fileRecord!!.path.toFile()).use { fos ->
                                process(
                                    Cipher.DECRYPT_MODE, dataInputStream,
                                    fos,
                                    aesSecretKey,
                                    IvParameterSpec(rsaAesEnvelope.iv),
                                    rsaAesEnvelope.aesTransformation,
                                    { destroyed },
                                    { value: Int? -> this.updateProgress(value) })
                            }
                            if (destroyed) {
                                Files.delete(fileRecord.path)
                            } else {
                                updateProgress(fileInfo.fileSize) //100%
                                (outputView as FileOutputFragment).setUri(fileRecord.uri)
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                            activity.mainExecutor.execute {
                                Toast.makeText(
                                    context,
                                    String.format(
                                        "%s: %s",
                                        ex.javaClass.simpleName,
                                        ex.message
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                context.mainExecutor.execute {
                    Toast.makeText(
                        context,
                        if (e.message == null) String.format(
                            context.getString(R.string.authentication_failed_s),
                            e.javaClass.name
                        ) else e.message,
                        Toast.LENGTH_SHORT
                    ).show()
                    discardBackStack(messageFragment)
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
            s = if (lastProgressPrc == 100) context.getString(R.string.done) else String.format(
                Locale.getDefault(), context.getString(R.string.working_prc), lastProgressPrc
            )
        }

        (outputView as FileOutputFragment).setProgress(s)
    }

    override fun onDestroyView() {
        destroyed = true
    }
}
