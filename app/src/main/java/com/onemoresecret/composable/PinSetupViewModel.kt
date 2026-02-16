package com.onemoresecret.composable

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class PinSetupViewModel(private val prefs: SharedPreferences, val onBack: () -> Unit) :
    ViewModel() {
    var state by mutableStateOf(State())
    var onAction: ((Action) -> Unit)

    init {
        onAction = onActionFactory(this::updateState) { onSave { onBack } }

        state = state.copy(
            pinEnabled = prefs.getBoolean(PIN_ENABLED, false),
            pinValue = prefs.getString(PIN_VALUE, "") ?: "",
            repeatPin = prefs.getString(PIN_VALUE, "") ?: "",
            panicPin = prefs.getString(PIN_PANIC, "") ?: "",
            repeatPanicPin = prefs.getString(PIN_PANIC, "") ?: "",
            maxAttempts = prefs.getInt(PIN_MAX_ATTEMPTS, 0)
                .let { if (it > 0) it.toString() else "" },
            requestInterval = prefs.getLong(PIN_REQUEST_INTERVAL_MINUTES, 0)
                .let { if (it > 0) it.toString() else "" }
        )
        updateState { state }
    }

    fun updateState(update: (State) -> State) {
        state = getNewState(state,update)
    }

    fun onSave(onSuccess: () -> Unit) {
        prefs.edit {
            putBoolean(PIN_ENABLED, state.pinEnabled)
            if (state.pinEnabled) {
                putString(PIN_VALUE, state.pinValue)
                if (state.panicPin.isEmpty()) remove(PIN_PANIC) else putString(
                    PIN_PANIC,
                    state.panicPin
                )

                val maxAttr = state.maxAttempts.toIntOrNull() ?: 0
                if (maxAttr > 0) {
                    putInt(PIN_MAX_ATTEMPTS, maxAttr)
                    putInt(PIN_REMAINING_ATTEMPTS, maxAttr)
                }

                val interval = state.requestInterval.toLongOrNull() ?: 0L
                if (interval > 0) putLong(PIN_REQUEST_INTERVAL_MINUTES, interval)
            } else {
                remove(PIN_ENABLED).remove(PIN_VALUE).remove(PIN_PANIC) // ... etc
            }
        }
        onSuccess()
    }

    companion object {
        const val PIN_ENABLED = "pin_enabled"
        const val PIN_VALUE = "pin_value"
        const val PIN_MAX_ATTEMPTS = "pin_max_attempts"
        const val PIN_REQUEST_INTERVAL_MINUTES = "pin_request_interval_minutes"
        const val PIN_PANIC = "pin_panic"
        const val PIN_REMAINING_ATTEMPTS = "pin_remaining_attempts"
        fun onActionFactory(
            stateUpdater: ((State) -> State) -> Unit,
            onSave: (() -> Unit) -> Unit
        ): (Action) -> Unit {
            return { action ->
                run {
                    when (action) {
                        is Action.OnPinChanged -> stateUpdater { it.copy(pinValue = action.value) }
                        is Action.OnRepeatPinChanged -> stateUpdater { it.copy(repeatPin = action.value) }
                        is Action.OnPanicPinChanged -> stateUpdater { it.copy(panicPin = action.value) }
                        is Action.OnRepeatPanicPinChanged -> stateUpdater { it.copy(repeatPanicPin = action.value) }
                        is Action.OnEnabledChanged -> stateUpdater { it.copy(pinEnabled = action.enabled) }
                        is Action.OnRequestIntervalChanged -> stateUpdater { it.copy(requestInterval = action.value) }
                        is Action.OnMaxAttemptsChanged -> stateUpdater { it.copy(maxAttempts = action.value) }
                        is Action.OnSave -> onSave {}
                    }
                }
            }
        }
        fun getNewState(oldState: State, update: (State) -> State): State {
            val newState = update(oldState)
            val isPinValid = newState.pinValue.isNotEmpty() &&
                    newState.pinValue == newState.repeatPin
            val isPanicPinValid =
                newState.panicPin == newState.repeatPanicPin &&
                        (newState.panicPin.isEmpty() || newState.panicPin != newState.pinValue)
            val canSave = !newState.pinEnabled || (isPinValid && isPanicPinValid)

            return newState.copy(
                isPinValid = isPinValid,
                isPanicPinValid = isPanicPinValid,
                canSave = canSave
            )
        }
    }

    class Factory(private val prefs: SharedPreferences, private val onBack: () -> Unit) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PinSetupViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PinSetupViewModel(prefs, onBack) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    sealed class Action {
        data class OnPinChanged(val value: String) : Action()
        data class OnRepeatPinChanged(val value: String) : Action()
        data class OnPanicPinChanged(val value: String) : Action()
        data class OnRepeatPanicPinChanged(val value: String) : Action()
        data class OnEnabledChanged(val enabled: Boolean) : Action()
        data class OnRequestIntervalChanged(val value: String) : Action()
        data class OnMaxAttemptsChanged(val value: String) : Action()
        object OnSave : Action()
    }

    data class State(
        val pinEnabled: Boolean = false,
        val pinValue: String = "",
        val repeatPin: String = "",
        val panicPin: String = "",
        val repeatPanicPin: String = "",
        val maxAttempts: String = "",
        val requestInterval: String = "",
        val isPinValid: Boolean = false,
        val isPanicPinValid: Boolean = false,
        val canSave: Boolean = false
    )
}



