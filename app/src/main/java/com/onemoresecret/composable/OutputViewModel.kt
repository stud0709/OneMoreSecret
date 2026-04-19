package com.onemoresecret.composable

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.onemoresecret.bt.layout.KeyboardLayout
import java.util.Arrays
import java.util.Comparator
import java.util.Objects

class OutputViewModel(private val prefs: SharedPreferences) : ViewModel() {
    var state by mutableStateOf(State())
        private set

    private var keyboardLayouts: List<KeyboardLayout> = emptyList()

    fun initializeKeyboardLayouts() {
        if (keyboardLayouts.isNotEmpty()) return

        keyboardLayouts = Arrays.stream(KeyboardLayout.knownSubclasses)
            .map { clazz -> clazz!!.getDeclaredConstructor().newInstance() as KeyboardLayout }
            .filter { Objects.nonNull(it) }
            .sorted(Comparator.comparing { obj -> obj.toString() })
            .toList()

        val selectedClassName = prefs.getString(PROP_LAST_SELECTED_KEYBOARD_LAYOUT, null)
        val selectedLayout = keyboardLayouts.firstOrNull { it.javaClass.name == selectedClassName }
            ?: keyboardLayouts.firstOrNull()

        state = state.copy(
            keyboardLayouts = keyboardLayouts.map {
                KeyboardLayoutItem(it.javaClass.name, it.toString())
            },
            selectedKeyboardLayoutClassName = selectedLayout?.javaClass?.name
        )
    }

    fun setShareTitle(shareTitle: String) {
        state = state.copy(typingText = if (state.isTyping) state.typingText else shareTitle)
    }

    fun onBluetoothTargetSelected(address: String) {
        prefs.edit { putString(PROP_LAST_SELECTED_BT_TARGET, address) }
        state = state.copy(selectedBluetoothAddress = address)
    }

    fun onKeyboardLayoutSelected(className: String) {
        prefs.edit { putString(PROP_LAST_SELECTED_KEYBOARD_LAYOUT, className) }
        state = state.copy(selectedKeyboardLayoutClassName = className)
    }

    fun onDelayedStrokesChanged(enabled: Boolean) {
        state = state.copy(delayedStrokes = enabled)
    }

    fun getSelectedKeyboardLayout(): KeyboardLayout? {
        return keyboardLayouts.firstOrNull { it.javaClass.name == state.selectedKeyboardLayoutClassName }
    }

    fun getSelectedBluetoothDevice(): BluetoothTargetItem? {
        return state.bluetoothTargets.firstOrNull { it.address == state.selectedBluetoothAddress }
    }

    fun updateUiState(
        bluetoothAvailable: Boolean,
        bluetoothTargets: List<BluetoothTargetItem>,
        bluetoothStatusText: String,
        bluetoothStatusIcon: Int,
        discoverableEnabled: Boolean,
        keyboardLayoutEnabled: Boolean,
        bluetoothTargetEnabled: Boolean,
        typeButtonEnabled: Boolean,
        delayedStrokesEnabled: Boolean,
        typingText: String,
        isTyping: Boolean
    ) {
        val selectedBluetoothAddress = when {
            state.selectedBluetoothAddress in bluetoothTargets.map { it.address } -> state.selectedBluetoothAddress
            bluetoothTargets.isNotEmpty() -> bluetoothTargets.first().address
            else -> null
        }

        state = state.copy(
            bluetoothAvailable = bluetoothAvailable,
            bluetoothTargets = bluetoothTargets,
            selectedBluetoothAddress = selectedBluetoothAddress,
            bluetoothStatusText = bluetoothStatusText,
            bluetoothStatusIcon = bluetoothStatusIcon,
            discoverableEnabled = discoverableEnabled,
            keyboardLayoutEnabled = keyboardLayoutEnabled,
            bluetoothTargetEnabled = bluetoothTargetEnabled,
            typeButtonEnabled = typeButtonEnabled,
            delayedStrokesEnabled = delayedStrokesEnabled,
            typingText = typingText,
            isTyping = isTyping
        )
    }

    class Factory(private val prefs: SharedPreferences) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(OutputViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return OutputViewModel(prefs) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

    data class State(
        val bluetoothAvailable: Boolean = false,
        val bluetoothTargets: List<BluetoothTargetItem> = emptyList(),
        val selectedBluetoothAddress: String? = null,
        val keyboardLayouts: List<KeyboardLayoutItem> = emptyList(),
        val selectedKeyboardLayoutClassName: String? = null,
        val bluetoothStatusText: String = "",
        val bluetoothStatusIcon: Int = 0,
        val discoverableEnabled: Boolean = false,
        val keyboardLayoutEnabled: Boolean = false,
        val bluetoothTargetEnabled: Boolean = false,
        val typeButtonEnabled: Boolean = false,
        val delayedStrokes: Boolean = false,
        val delayedStrokesEnabled: Boolean = false,
        val typingText: String = "",
        val isTyping: Boolean = false
    )

    data class BluetoothTargetItem(val address: String, val label: String, val bluetoothDevice: android.bluetooth.BluetoothDevice? = null)
    data class KeyboardLayoutItem(val className: String, val label: String)

    companion object {
        private const val PROP_LAST_SELECTED_KEYBOARD_LAYOUT = "last_selected_kbd_layout"
        private const val PROP_LAST_SELECTED_BT_TARGET = "last_selected_bt_target"
    }
}
