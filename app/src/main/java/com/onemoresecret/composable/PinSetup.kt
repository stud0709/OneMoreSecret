package com.onemoresecret.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.onemoresecret.R
import com.onemoresecret.composable.PinSetupViewModel.Companion.getNewState

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
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Enable PIN Protection
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.pinEnabled,
                    onCheckedChange = { onAction(PinSetupViewModel.Action.OnEnabledChanged(it)) }
                )
                Text(stringResource(R.string.enable_pin_protection))
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
                        style = MaterialTheme.typography.labelLarge
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.request_pin_entry_every))
                        OutlinedTextField(
                            singleLine = true,
                            value = state.requestInterval,
                            onValueChange = {
                                onAction(
                                    PinSetupViewModel.Action.OnRequestIntervalChanged(
                                        it
                                    )
                                )
                            },
                            modifier = Modifier
                                .width(80.dp)
                                .padding(horizontal = 8.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Next
                            )
                        )
                        Text(stringResource(R.string.minutes))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.delete_all_keys))
                        OutlinedTextField(
                            singleLine = true,
                            value = state.maxAttempts,
                            onValueChange = {
                                onAction(
                                    PinSetupViewModel.Action.OnMaxAttemptsChanged(
                                        it
                                    )
                                )
                            },
                            modifier = Modifier
                                .width(80.dp)
                                .padding(horizontal = 8.dp),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Next
                            )
                        )
                        Text(stringResource(R.string.max_attempts))
                    }

                    // Panic PIN Section
                    Text(
                        stringResource(R.string.panic_pin_descr),
                        style = MaterialTheme.typography.bodySmall
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
        label = { Text(label) },
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

