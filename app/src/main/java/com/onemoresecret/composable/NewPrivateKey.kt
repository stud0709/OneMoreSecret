package com.onemoresecret.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.R

@Composable
fun NewPrivateKey(viewModel: NewPrivateKeyViewModel = viewModel()) {
    NewPrivateKeyScreen(
        state = viewModel.state,
        onAction = viewModel::onAction,
        onCreate = viewModel::createPrivateKey,
        onActivate = viewModel.onActivate
    )
}

@Composable
fun NewPrivateKeyScreen(
    state: NewPrivateKeyViewModel.State,
    onAction: (NewPrivateKeyViewModel.Action) -> Unit,
    onCreate: () -> Unit,
    onActivate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Alias Input
        OutlinedTextField(
            value = state.alias,
            readOnly = state.isKeyCreated,
            onValueChange = { onAction(NewPrivateKeyViewModel.Action.OnAliasChanged(it)) },
            label = { Text(stringResource(R.string.new_key_alias)) },
            modifier = Modifier.fillMaxWidth()
        )

        androidx.compose.animation.AnimatedContent(
            targetState = state.isKeyCreated,
            label = "KeyCreationTransition"
        ) { isCreated ->
            if (isCreated) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Consent Checkbox (Enabled only after key creation)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = state.isConsentChecked,
                            onCheckedChange = {
                                onAction(
                                    NewPrivateKeyViewModel.Action.OnBackupConsentChanged(
                                        it
                                    )
                                )
                            }
                        )
                        Text(stringResource(R.string.private_key_consent))
                    }

                    // Activate Button
                    Button(
                        onClick = onActivate,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.activate_private_key))
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Password Input
                    PasswordField(
                        value = state.password,
                        onValueChange = {
                            onAction(
                                NewPrivateKeyViewModel.Action.OnPasswordChanged(
                                    it
                                )
                            )
                        },
                        label = stringResource(R.string.new_transport_password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Repeat Password Input
                    PasswordField(
                        value = state.repeatPassword,
                        onValueChange = {
                            onAction(
                                NewPrivateKeyViewModel.Action.OnRepeatPasswordChanged(
                                    it
                                )
                            )
                        },
                        label = stringResource(R.string.repeat_transport_password),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 4096-bit Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            stringResource(R.string.rsa_key_length_4096_bit),
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = state.is4096Bit,
                            onCheckedChange = {
                                onAction(
                                    NewPrivateKeyViewModel.Action.On4096BitChanged(
                                        it
                                    )
                                )
                            })
                    }

                    // Create Button
                    Button(
                        onClick = onCreate,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(stringResource(R.string.create))
                    }
                }
            }
        }
    }
}