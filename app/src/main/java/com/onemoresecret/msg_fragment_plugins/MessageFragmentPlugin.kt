package com.onemoresecret.msg_fragment_plugins

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.MainActivity
import com.onemoresecret.MessageFragment
import com.onemoresecret.OmsFileProvider
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import com.onemoresecret.Util
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.RsaTransformation
import java.security.KeyStoreException

abstract class MessageFragmentPlugin(
    protected val messageFragment: MessageFragment
) : BiometricPrompt.AuthenticationCallback() {

    protected val context: Context = messageFragment.requireContext()
    protected val activity: FragmentActivity = messageFragment.requireActivity()
    @JvmField
    protected var fingerprint: ByteArray? = null
    protected val preferences: SharedPreferences = activity.getPreferences(Context.MODE_PRIVATE)
    protected var rsaTransformation: RsaTransformation? = null
    protected val TAG: String = javaClass.simpleName
    protected var msgView: Fragment? = null
    protected var outView: FragmentWithNotificationBeforePause? = null

    abstract fun getMessageView(): Fragment

    open fun getOutputView(): FragmentWithNotificationBeforePause {
        if (outView == null) outView = OutputFragment()
        return outView!!
    }

    protected open fun getReference(): String? {
        return null
    }

    @Throws(KeyStoreException::class)
    open fun showBiometricPromptForDecryption() {
        val cryptographyManager = CryptographyManager()
        val keyStoreEntry = cryptographyManager.getByFingerprint(fingerprint!!, preferences)
            ?: throw NoSuchElementException(
                String.format(
                    context.getString(R.string.no_key_found),
                    Util.byteArrayToHex(fingerprint!!)
                )
            )

        val biometricPrompt = BiometricPrompt(activity, this)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(context.getString(R.string.prompt_info_title))
            .setSubtitle(String.format(context.getString(R.string.prompt_info_subtitle), keyStoreEntry.alias))
            .setDescription(getReference() ?: context.getString(R.string.prompt_info_description))
            .setNegativeButtonText(context.getString(android.R.string.cancel))
            .setConfirmationRequired(false)
            .build()

        val cipher = cryptographyManager.getInitializedMasterRsaCipher(javax.crypto.Cipher.DECRYPT_MODE)

        context.mainExecutor.execute {
            biometricPrompt.authenticate(
                promptInfo,
                BiometricPrompt.CryptoObject(cipher)
            )
        }
    }

    override fun onAuthenticationError(errCode: Int, errString: CharSequence) {
        Log.d(TAG, String.format("Authentication failed: %s (%s)", errString, errCode))
        Thread { OmsFileProvider.purgeTmp(context) }.start()
        context.mainExecutor.execute {
            Toast.makeText(context, "$errString ($errCode)", Toast.LENGTH_SHORT).show()
            Util.discardBackStack(messageFragment)
        }
    }

    override fun onAuthenticationFailed() {
        Log.d(TAG, "User biometrics rejected")

        // close socket if WiFiPairing active
        (context as MainActivity).sendReplyViaSocket(byteArrayOf(), true)

        context.mainExecutor.execute {
            Toast.makeText(context, context.getString(R.string.auth_failed), Toast.LENGTH_SHORT).show()
            Util.discardBackStack(messageFragment)
        }
    }

    open fun onDestroyView() {
    }
}
