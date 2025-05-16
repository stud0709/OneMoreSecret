package com.onemoresecret.compose

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import com.onemoresecret.MainActivity
import com.onemoresecret.R
import androidx.core.content.edit

class PinSetupViewModel(application: Application, val resourceProvider: ResourceProvider) :
    AndroidViewModel(application) {
    private val preferences: SharedPreferences =
        application.applicationContext.getSharedPreferences(
            MainActivity.SPARED_PREF_NAME,
            Context.MODE_PRIVATE
        )
    internal val snackbarMessage = mutableStateOf<String?>(null)
    internal val navBack = mutableStateOf(false)

    internal val panicPin = mutableStateOf(preferences.getString(PROP_PANIC_PIN, "")!!)
    internal val isPinEnabled = mutableStateOf(preferences.getBoolean(PROP_PIN_ENABLED, false))
    internal val pin = mutableStateOf(preferences.getString(PROP_PIN_VALUE, "")!!)
    internal val saveEnabled = mutableStateOf(false)
    internal val repeatPanicPin = mutableStateOf(preferences.getString(PROP_PANIC_PIN, "")!!)
    internal val repeatPin = mutableStateOf(preferences.getString(PROP_PIN_VALUE, "")!!)
    internal val maxFailedAttempts = mutableIntStateOf(preferences.getInt(PROP_MAX_ATTEMPTS, 0))
    internal val requestInterval =
        mutableIntStateOf(preferences.getInt(PROP_REQUEST_INTERVAL_MINUTES, 0))

    internal val imgResourcePin = mutableIntStateOf(R.drawable.baseline_cancel_24)
    internal val imgResourcePanicPin = mutableIntStateOf(R.drawable.baseline_cancel_24)

    //private val menuProvider = PinMenuProvider()

    //requireActivity().addMenuProvider(menuProvider)

    internal fun onSave() {
        preferences.edit() {

            putBoolean(PROP_PIN_ENABLED, isPinEnabled.value)

            if (isPinEnabled.value) {
                putString(PROP_PIN_VALUE, pin.value)

                if (repeatPanicPin.value.isEmpty()) {
                    remove(PROP_PANIC_PIN)
                } else {
                    putString(PROP_PANIC_PIN, panicPin.value)
                }

                if (maxFailedAttempts.intValue > 0) {
                    putInt(PROP_MAX_ATTEMPTS, maxFailedAttempts.intValue)
                    putInt(PROP_REMAINING_ATTEMPTS, maxFailedAttempts.intValue)
                } else {
                    remove(PROP_MAX_ATTEMPTS)
                    remove(PROP_REMAINING_ATTEMPTS)
                }

                if (requestInterval.intValue > 0) {
                    putInt(PROP_REQUEST_INTERVAL_MINUTES, requestInterval.intValue)
                } else {
                    remove(PROP_REQUEST_INTERVAL_MINUTES)
                }
            } else {
                remove(PROP_PIN_VALUE)
                    .remove(PROP_PANIC_PIN)
                    .remove(PROP_MAX_ATTEMPTS)
                    .remove(PROP_REQUEST_INTERVAL_MINUTES)
            }
        }

        snackbarMessage.value = resourceProvider.getString(R.string.pin_preferences_saved)
        navBack.value = true
    }

    /**
     * Check if the form data is valid and it is OK to save it
     */
    internal fun validateForm() {
        saveEnabled.value = !isPinEnabled.value || (isPinValid && isPanicPinValid)
    }

    internal fun afterPinChanged() {
        imgResourcePin.intValue =
            if (isPinValid) R.drawable.baseline_check_circle_24 else R.drawable.baseline_cancel_24
        validateForm()
    }

    internal fun afterPanicPinChanged() {
        imgResourcePanicPin.intValue =
            if (isPanicPinValid) R.drawable.baseline_check_circle_24 else R.drawable.baseline_cancel_24
        validateForm()
    }

    private val isPinValid: Boolean
        get() {
            var b = !pin.value.isEmpty() && (pin.value == repeatPin.value)

            if (b && !panicPin.value.isEmpty() && panicPin.value == pin.value) {
                snackbarMessage.value =
                    resourceProvider.getString(R.string.panic_pin_should_not_match_pin)
                b = false
            }

            return b
        }

    private val isPanicPinValid: Boolean
        get() {
            var b = panicPin.value == repeatPanicPin.value

            if (b && !pin.value.isEmpty() && panicPin.value == pin.value) {
                snackbarMessage.value =
                    resourceProvider.getString(R.string.panic_pin_should_not_match_pin)
                b = false
            }

            return b
        }
/*
    private inner class PinMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.pin_setup_md_url, requireContext())
            } else {
                return false
            }

            return true
        }
    }

 */
    /*
        override fun onDestroyView() {
            super.onDestroyView()
            requireActivity().removeMenuProvider(menuProvider)
            binding = null
        }
    */

    companion object {
        const val PROP_PIN_ENABLED: String = "pin_enabled"
        const val PROP_PIN_VALUE: String = "pin_value"
        const val PROP_PANIC_PIN: String = "pin_panic"
        const val PROP_MAX_ATTEMPTS: String = "pin_max_attempts"
        const val PROP_REQUEST_INTERVAL_MINUTES: String = "pin_request_interval_minutes"
        const val PROP_REMAINING_ATTEMPTS: String = "pin_remaining_attempts"
    }
}