package com.onemoresecret

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.onemoresecret.crypto.CryptographyManager
import kotlinx.coroutines.launch
import java.security.KeyStoreException
import androidx.core.content.edit
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.PinEntryDialog

class PinEntryFragment(
    private val runOnSuccess: Runnable?,
    private var runOnCancel: Runnable?,
    private val runOnPanic: Runnable?
) : DialogFragment() {
    private var preferences: SharedPreferences? = null
    private var context: Context? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    PinEntryDialog(
                        onDismissRequest = { dismiss() },
                        onUnlock = { enteredPin -> tryUnlock(enteredPin) }
                    )
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        /* Remember the context. Will need it in onDismiss to purge tmp files. */
        this.context = context
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
    }

    private fun tryUnlock(enteredPin: String): Boolean {
        preferences?.let { preferences ->
            val panicPin = preferences.getString(PinSetupFragment.PROP_PANIC_PIN, null)
            val correctPin = preferences.getString(PinSetupFragment.PROP_PIN_VALUE, null)

            val panicPinEntered = (enteredPin == panicPin)

            if (panicPinEntered) {
                //panic pin
                panic()
            }

            if (panicPinEntered || (enteredPin == correctPin)) {
                //pin entered correctly
                Toast.makeText(
                    requireContext(),
                    R.string.pin_accepted,
                    Toast.LENGTH_SHORT
                )
                    .show()
                requireContext().mainExecutor.execute(runOnSuccess)
                runOnCancel = null
                dismiss()
                return true
            } else {
                //wrong pin
                var remainingAttempts = preferences.getInt(
                    PinSetupFragment.PROP_REMAINING_ATTEMPTS,
                    Int.Companion.MAX_VALUE
                )
                remainingAttempts--

                preferences.edit {
                    putInt(PinSetupFragment.PROP_REMAINING_ATTEMPTS, remainingAttempts)
                }

                if (remainingAttempts <= 0) panic()

                Toast.makeText(
                    requireContext(),
                    R.string.wrong_pin,
                    Toast.LENGTH_LONG
                ).show()

            }
        }
        return false
    }

    private fun panic() {
        viewLifecycleOwner
            .lifecycleScope
            .launch { OmsFileProvider.purgeTmp(requireContext()) }

        val cryptographyManager = CryptographyManager()

        //delete all keys from Android KeyStore
        try {
            val aliasesEnum = cryptographyManager.keyStore.aliases()
            while (aliasesEnum.hasMoreElements()) {
                cryptographyManager.keyStore.deleteEntry(aliasesEnum.nextElement())
            }
        } catch (e: KeyStoreException) {
            throw RuntimeException(e)
        }

        //delete all sensitive information from SharedPreferences
        preferences!!.edit {
            remove(QRFragment.PROP_RECENT_ENTRIES)
            remove(QRFragment.PROP_PRESETS)
            remove(CryptographyManager.PROP_KEYSTORE)
            remove((CryptographyManager.MASTER_KEY_ALIAS))
        }

        runOnPanic?.run()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        viewLifecycleOwner
            .lifecycleScope
            .launch { OmsFileProvider.purgeTmp(requireContext()) }

        if (this.runOnCancel != null) {
            requireContext().mainExecutor.execute(runOnCancel)
        }
    }
}