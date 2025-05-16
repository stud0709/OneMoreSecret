package com.onemoresecret.msg_fragment_plugins

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricPrompt.PromptInfo
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.MainActivity
import com.onemoresecret.MessageFragment
import com.onemoresecret.OmsFileProvider
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.crypto.CryptographyManager
import java.security.KeyStoreException
import java.util.Objects

abstract class MessageFragmentPlugin(@JvmField protected val messageFragment: MessageFragment) :
    BiometricPrompt.AuthenticationCallback() {
    @JvmField
    protected val context: Context = messageFragment.requireContext()
    @JvmField
    protected val activity: FragmentActivity = messageFragment.requireActivity()
    @JvmField
    protected var fingerprint: ByteArray? = null
    @JvmField
    protected val preferences: SharedPreferences =
        activity.getPreferences(Context.MODE_PRIVATE)
    @JvmField
    protected var rsaTransformation: String? = null
    protected val TAG: String = javaClass.simpleName
    @JvmField
    protected var messageView: Fragment? = null
    @JvmField
    protected var outputView: FragmentWithNotificationBeforePause? = null

    abstract fun getMessageView(): Fragment?

    open fun getOutputView(): FragmentWithNotificationBeforePause? {
        if (outputView == null) outputView = OutputFragment()
        return outputView
    }

    protected open val reference: String?
        get() = null

    @Throws(KeyStoreException::class)
    open fun showBiometricPromptForDecryption() {
        val cryptographyManager = CryptographyManager()
        val aliases = cryptographyManager.getByFingerprint(fingerprint!!)

        if (aliases.isEmpty()) throw NoSuchElementException(
            String.format(
                context.getString(R.string.no_key_found),
                byteArrayToHex(fingerprint!!)
            )
        )

        if (aliases.size > 1) throw NoSuchElementException(context.getString(R.string.multiple_keys_found))

        val biometricPrompt = BiometricPrompt(activity, this)
        val alias = aliases[0]

        val promptInfo = PromptInfo.Builder()
            .setTitle(context.getString(R.string.prompt_info_title))
            .setSubtitle(String.format(context.getString(R.string.prompt_info_subtitle), alias))
            .setDescription(
                Objects.requireNonNullElse(
                    reference,
                    context.getString(R.string.prompt_info_description)
                )
            )
            .setNegativeButtonText(context.getString(android.R.string.cancel))
            .setConfirmationRequired(false)
            .build()

        val cipher = CryptographyManager().getInitializedCipherForDecryption(
            alias, rsaTransformation
        )

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
            discardBackStack(messageFragment)
        }
    }

    override fun onAuthenticationFailed() {
        Log.d(
            TAG,
            "User biometrics rejected"
        )

        //close socket if WiFiPairing active
        (context as MainActivity).sendReplyViaSocket(byteArrayOf(), true)

        context.getMainExecutor().execute {
            Toast.makeText(context, context.getString(R.string.auth_failed), Toast.LENGTH_SHORT)
                .show()
            discardBackStack(messageFragment)
        }
    }

    /**
     * Logic at [Fragment.onDestroyView]
     */
    open fun onDestroyView() {
    }
}
