package com.onemoresecret

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.PinSetupViewModel.Companion.getNewState
import com.onemoresecret.R
import com.onemoresecret.Util.openUrl
import com.onemoresecret.composable.OneMoreSecretTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onPopBackStack: () -> Unit
) {
    val context = LocalContext.current

    val sharedPreferences = (context as? ComponentActivity)?.getPreferences(Context.MODE_PRIVATE)
        ?: context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    val viewModel: PinSetupViewModel = viewModel(
        factory = PinSetupViewModel.Factory(sharedPreferences) {
            Toast.makeText(context, "Settings Saved", Toast.LENGTH_SHORT).show()
            onPopBackStack()
        }
    )

    OneMoreSecretTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.pin_setup), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onPopBackStack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate up"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { openUrl(R.string.pin_setup_md_url, context) }) {
                            Icon(
                                imageVector = Icons.Default.Help,
                                contentDescription = stringResource(R.string.help)
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                PinSetup(viewModel = viewModel)
            }
        }
    }
}



@Preview(showSystemUi = true, name = "PIN Protection Setup")
@Composable
fun PreviewPinSetup() {
    var state by remember {
        mutableStateOf(
            PinSetupViewModel.State(
                false,
                "1234",
                "1234",
                "9999",
                "8888",
                "10",
                "3",
                isPinValid = true,
                isPanicPinValid = false,
                canSave = false
            )
        )
    }

    val actionHandler =
        PinSetupViewModel.onActionFactory({ update -> state = getNewState(state, update) }, {})

    OneMoreSecretTheme {
        PinSetupContent(
            state
        ) { action ->
            actionHandler(action)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetup(viewModel: PinSetupViewModel = viewModel()) {
    PinSetupContent(viewModel.state) { viewModel.onAction(it) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSetupContent(
    state: PinSetupViewModel.State,
    onAction: (PinSetupViewModel.Action) -> Unit,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(scrollState)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Enable PIN Protection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = state.pinEnabled,
                    onValueChange = { onAction(PinSetupViewModel.Action.OnEnabledChanged(it)) },
                    role = Role.Switch
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = state.pinEnabled,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(
                stringResource(R.string.enable_pin_protection),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        AnimatedVisibility(visible = state.pinEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // PIN Fields
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinField(
                        value = state.pinValue,
                        onValueChange = { onAction(PinSetupViewModel.Action.OnPinChanged(it)) },
                        label = stringResource(R.string.pin),
                        modifier = Modifier.weight(1f)
                    )
                    PinField(
                        value = state.repeatPin,
                        onValueChange = {
                            onAction(
                                PinSetupViewModel.Action.OnRepeatPinChanged(
                                    it
                                )
                            )
                        },
                        label = stringResource(R.string.repeat_pin),
                        modifier = Modifier.weight(1f),
                        isValid = state.isPinValid
                    )
                }

                // Advanced Settings
                Text(
                    stringResource(R.string.advanced_optional_settings),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.request_pin_entry_every),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        singleLine = true,
                        value = state.requestInterval,
                        onValueChange = {
                            onAction(PinSetupViewModel.Action.OnRequestIntervalChanged(it))
                        },
                        modifier = Modifier.width(80.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next
                        )
                    )
                    Text(
                        text = stringResource(R.string.minutes),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.delete_all_keys),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        singleLine = true,
                        value = state.maxAttempts,
                        onValueChange = {
                            onAction(PinSetupViewModel.Action.OnMaxAttemptsChanged(it))
                        },
                        modifier = Modifier.width(80.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next
                        )
                    )
                    Text(
                        text = stringResource(R.string.max_attempts),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Panic PIN Section
                Text(
                    stringResource(R.string.panic_pin_descr),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PinField(
                        value = state.panicPin,
                        onValueChange = { onAction(PinSetupViewModel.Action.OnPanicPinChanged(it)) },
                        label = stringResource(R.string.panic_pin),
                        modifier = Modifier.weight(1f)
                    )
                    PinField(
                        value = state.repeatPanicPin,
                        onValueChange = {
                            onAction(
                                PinSetupViewModel.Action.OnRepeatPanicPinChanged(
                                    it
                                )
                            )
                        },
                        label = stringResource(R.string.repeat_panic_pin),
                        modifier = Modifier.weight(1f),
                        isValid = state.isPanicPinValid
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onAction(PinSetupViewModel.Action.OnSave) },
            enabled = state.canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.save))
        }
    }
}

@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isValid: Boolean? = null
) {
    OutlinedTextField(
        singleLine = true,
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = ImeAction.Next
        ),
        modifier = modifier,
        trailingIcon = {
            if (isValid != null && value.isNotEmpty()) {
                val icon = if (isValid) Icons.Default.CheckCircle else Icons.Default.Cancel
                val color = if (isValid) Color.Green else Color.Red
                Icon(icon, contentDescription = null, tint = color)
            }
        }
    )
}



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



