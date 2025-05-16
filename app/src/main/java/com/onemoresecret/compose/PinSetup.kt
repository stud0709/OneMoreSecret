package com.onemoresecret.compose

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.onemoresecret.R
import kotlinx.coroutines.launch

@Composable
fun PinSetupScreen(navController: NavController?, snackbarHostState: SnackbarHostState?) {
    val viewModel = PinSetupViewModel(
        LocalContext.current.applicationContext as Application,
        ResourceProvider(LocalContext.current)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = viewModel.isPinEnabled.value,
                onCheckedChange = { viewModel.validateForm() }
            )
            Text(stringResource(id = R.string.enable_pin_protection))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                enabled = viewModel.isPinEnabled.value,
                value = viewModel.pin.value,
                onValueChange = { viewModel.afterPinChanged() },
                label = { Text(stringResource(id = R.string.pin)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            OutlinedTextField(
                enabled = viewModel.isPinEnabled.value,
                value = viewModel.repeatPin.value,
                onValueChange = { viewModel.afterPinChanged() },
                label = { Text(stringResource(id = R.string.repeat_pin)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.weight(1f)
            )
            if(viewModel.isPinEnabled.value) {
                Spacer(modifier = Modifier.width(8.dp))
                Image(
                    painter = painterResource(id = viewModel.imgResourcePin.intValue),
                    contentDescription = "Pin Match Status",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(stringResource(id = R.string.advanced_optional_settings), fontWeight = FontWeight.Bold)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(id = R.string.request_pin_entry_every))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                enabled = viewModel.isPinEnabled.value,
                value = viewModel.requestInterval.intValue.toString(),
                onValueChange = { viewModel.requestInterval.intValue = it.toIntOrNull() ?: 0 },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(60.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.minutes))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(id = R.string.delete_all_keys))
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                enabled = viewModel.isPinEnabled.value,
                value = viewModel.maxFailedAttempts.intValue.toString(),
                onValueChange = { viewModel.maxFailedAttempts.intValue = it.toIntOrNull() ?: 0 },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(60.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(id = R.string.failed_attempts))
        }

        Text(stringResource(id = R.string.panic_pin_descr))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                enabled = viewModel.isPinEnabled.value,
                value = viewModel.panicPin.value,
                onValueChange = { viewModel.afterPanicPinChanged() },
                label = { Text(stringResource(id = R.string.panic_pin)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )

            OutlinedTextField(
                enabled = viewModel.isPinEnabled.value,
                value = viewModel.repeatPanicPin.value,
                onValueChange = { viewModel.afterPanicPinChanged() },
                label = { Text(stringResource(id = R.string.repeat_panic_pin)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            if(viewModel.isPinEnabled.value) {
                Spacer(modifier = Modifier.width(8.dp))
                Image(
                    painter = painterResource(id = viewModel.imgResourcePanicPin.intValue),
                    contentDescription = "Panic Pin Match Status",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Button(
            enabled = viewModel.saveEnabled.value,
            onClick = { viewModel.onSave() },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(stringResource(id = R.string.save))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.validateForm()
    }

    //go back to the previous screen
    LaunchedEffect(viewModel.navBack.value) {
        if (!viewModel.navBack.value) return@LaunchedEffect
        navController?.popBackStack()
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.snackbarMessage.value) {
        if (viewModel.snackbarMessage.value == null) return@LaunchedEffect
        val msg = viewModel.snackbarMessage.value!!
        viewModel.snackbarMessage.value = null

        coroutineScope.launch { snackbarHostState?.showSnackbar(msg) }
    }
}


@Preview(showBackground = true)
@Composable
fun PinSetupScreenPreview() {
    PinSetupScreen(null, null)
}
