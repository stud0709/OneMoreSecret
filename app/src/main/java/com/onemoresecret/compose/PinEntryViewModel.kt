package com.onemoresecret.compose

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onemoresecret.MainActivity
import com.onemoresecret.OmsFileProvider
import com.onemoresecret.PinSetupFragment
import com.onemoresecret.QRFragment
import com.onemoresecret.R
import com.onemoresecret.crypto.CryptographyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.KeyStoreException

class PinEntryViewModel(application: Application) : AndroidViewModel(application) {
    val pinText = mutableStateOf("")

    val preferences: SharedPreferences = application.applicationContext.getSharedPreferences(
        MainActivity.SPARED_PREF_NAME,
        Context.MODE_PRIVATE
    )

    private var onSuccess: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null
    private var onPanic: (() -> Unit)? = null
    private var onDismiss: (() -> Unit)? = null

    fun configure(onSuccess: (() -> Unit)? = null, onCancel: (() -> Unit)? = null, onPanic: (() -> Unit)? = null, onDismiss: () -> Unit) {
        this.onSuccess = onSuccess
        this.onCancel = onCancel
        this.onPanic = onPanic
        this.onDismiss = onDismiss
    }

    internal fun tryUnlock() {
        val context = getApplication<Application>().applicationContext

        val panicPinEntered = pinText.value == preferences.getString(PinSetupFragment.PROP_PANIC_PIN, null)

        if (panicPinEntered) {
            //panic pin
            panic()
        }

        if (panicPinEntered ||
            (pinText.value == preferences.getString(PinSetupFragment.PROP_PIN_VALUE, null))
        ) {
            //pin entered correctly
            Toast.makeText(context, R.string.pin_accepted, Toast.LENGTH_SHORT).show()
            viewModelScope.launch(Dispatchers.Main) { onSuccess?.invoke() }
            onCancel = null
            onDismiss?.invoke()
        } else {
            //wrong pin
            var remainingAttempts =
                preferences.getInt(PinSetupFragment.PROP_REMAINING_ATTEMPTS, Int.MAX_VALUE)

            remainingAttempts--

            preferences.edit() {
                putInt(PinSetupFragment.PROP_REMAINING_ATTEMPTS, remainingAttempts)
            }

            if (remainingAttempts <= 0) panic()

            Toast.makeText(context, R.string.wrong_pin, Toast.LENGTH_LONG).show()
            pinText.value = ""
        }
    }

    private fun panic() {
        val context = getApplication<Application>().applicationContext

        viewModelScope.launch { OmsFileProvider.purgeTmp(context) }

        val cryptographyManager = CryptographyManager()

        try {
            val aliasesEnum = cryptographyManager.keyStore!!.aliases()

            while (aliasesEnum.hasMoreElements()) {
                cryptographyManager.deleteKey(aliasesEnum.nextElement())
            }
        } catch (e: KeyStoreException) {
            throw RuntimeException(e)
        }

        preferences.edit() {
            if (preferences.contains(QRFragment.PROP_RECENT_ENTRIES)) remove(QRFragment.PROP_RECENT_ENTRIES)
            if (preferences.contains(QRFragment.PROP_PRESETS)) remove(QRFragment.PROP_PRESETS)
        }

        onPanic?.invoke()
    }
}