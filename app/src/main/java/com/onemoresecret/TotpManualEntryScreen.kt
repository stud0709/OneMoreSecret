package com.onemoresecret

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Timelapse
import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import com.onemoresecret.crypto.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpManualEntryScreen() {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    val outputViewModel: OutputViewModel = viewModel(
        factory = OutputViewModel.Factory(preferences)
    )

    var secretText by remember { mutableStateOf("") }
    var selectedAlgorithm by remember { mutableStateOf(OneTimePassword.ALGORITHM[0]) }
    var selectedDigits by remember { mutableStateOf(OneTimePassword.DIGITS[0]) }
    var selectedPeriod by remember { mutableIntStateOf(OneTimePassword.DEFAULT_PERIOD) }

    var selectedAlias by remember { mutableStateOf<String?>(null) }
    val cryptographyManager = remember { CryptographyManager() }

    var totpCode by remember { mutableStateOf("-".repeat(selectedDigits.toInt())) }
    var timerText by remember { mutableStateOf("") }
    
    var outputMessage by remember { mutableStateOf<String?>(null) }
    
    val strAppName = stringResource(R.string.app_name)

    // Trigger regeneration when parameters change
    var lastState by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(secretText, selectedAlgorithm, selectedDigits, selectedPeriod, selectedAlias) {
        lastState = -1L // Force update output
    }

    LaunchedEffect(secretText, selectedAlgorithm, selectedDigits, selectedPeriod, selectedAlias) {
        while (true) {
            val builder = Uri.Builder()
            builder.scheme(OneTimePassword.OTP_SCHEME)
                .authority(OneTimePassword.TOTP)
                .appendPath(strAppName)

            builder.appendQueryParameter(OneTimePassword.SECRET_PARAM, secretText)

            if (selectedPeriod != OneTimePassword.DEFAULT_PERIOD) {
                builder.appendQueryParameter(OneTimePassword.PERIOD_PARAM, selectedPeriod.toString())
            }
            if (selectedDigits != OneTimePassword.DIGITS[0]) {
                builder.appendQueryParameter(OneTimePassword.DIGITS_PARAM, selectedDigits)
            }
            if (selectedAlgorithm != OneTimePassword.ALGORITHM[0]) {
                builder.appendQueryParameter(OneTimePassword.ALGORITHM_PARAM, selectedAlgorithm)
            }

            val uriString = builder.build().toString()

            try {
                val otp = OneTimePassword(uriString)
                val otpState = otp.state
                val code = otp.generateResponseCode(otpState.current)

                totpCode = code
                timerText = String.format("...%02ds", otp.period - otpState.secondsUntilNext)

                if (lastState != otpState.current) {
                    if (selectedAlias == null) {
                        outputMessage = code
                        outputViewModel.setMessage(outputMessage, "One-Time-Password")
                    } else {
                        val keyStoreEntry = cryptographyManager.getByAlias(selectedAlias!!, preferences)
                        if (keyStoreEntry != null) {
                            val result = MessageComposer.encodeAsOmsText(
                                TotpUriTransfer(
                                    uriString.toByteArray(),
                                    keyStoreEntry.public,
                                    RSAUtil.getRsaTransformation(preferences),
                                    AESUtil.getKeyLength(preferences),
                                    AESUtil.getAesTransformation(preferences)
                                ).message
                            )
                            outputMessage = result
                            outputViewModel.setMessage(outputMessage, "TOTP Configuration (encrypted)")
                        }
                    }
                    lastState = otpState.current
                }
            } catch (e: Exception) {
                // Invalid secret
                totpCode = "-".repeat(selectedDigits.toIntOrNull() ?: 6)
                timerText = ""
                outputMessage = null
            }

            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.totp_manual_entry), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {

                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
            value = secretText,
            onValueChange = { secretText = it.uppercase() },
            label = { Text(stringResource(R.string.totp_secret)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedAlias == null
        )

        Text(
            text = stringResource(R.string.totp_parameters_optional),
            style = MaterialTheme.typography.bodyMedium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            var algorithmMenuExpanded by remember { mutableStateOf(false) }
            Box {
                ElevatedFilterChip(
                    selected = true,
                    onClick = { algorithmMenuExpanded = true },
                    label = { Text(selectedAlgorithm) },
                    leadingIcon = {
                        Icon(androidx.compose.material.icons.Icons.Default.Category, contentDescription = null)
                    },
                    enabled = selectedAlias == null
                )
                DropdownMenu(
                    expanded = algorithmMenuExpanded,
                    onDismissRequest = { algorithmMenuExpanded = false }
                ) {
                    OneTimePassword.ALGORITHM.forEach { alg ->
                        DropdownMenuItem(
                            text = { Text(alg) },
                            onClick = {
                                selectedAlgorithm = alg
                                algorithmMenuExpanded = false
                            }
                        )
                    }
                }
            }

            var digitsMenuExpanded by remember { mutableStateOf(false) }
            Box {
                ElevatedFilterChip(
                    selected = true,
                    onClick = { digitsMenuExpanded = true },
                    label = { Text(selectedDigits) },
                    leadingIcon = {
                        Icon(androidx.compose.material.icons.Icons.Default.Password, contentDescription = null)
                    },
                    enabled = selectedAlias == null
                )
                DropdownMenu(
                    expanded = digitsMenuExpanded,
                    onDismissRequest = { digitsMenuExpanded = false }
                ) {
                    OneTimePassword.DIGITS.forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d) },
                            onClick = {
                                selectedDigits = d
                                digitsMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Period Selection. The original app used an AlertDialog with a NumberPicker.
            // For simplicity, we can provide a dropdown with common values or a simple dialog.
            var periodMenuExpanded by remember { mutableStateOf(false) }
            Box {
                ElevatedFilterChip(
                    selected = true,
                    onClick = { periodMenuExpanded = true },
                    label = { Text("${selectedPeriod}s") },
                    leadingIcon = {
                        Icon(androidx.compose.material.icons.Icons.Default.Timelapse, contentDescription = null)
                    },
                    enabled = selectedAlias == null
                )
                DropdownMenu(
                    expanded = periodMenuExpanded,
                    onDismissRequest = { periodMenuExpanded = false }
                ) {
                    listOf(15, 30, 60, 120).forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p}s") },
                            onClick = {
                                selectedPeriod = p
                                periodMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = totpCode,
                style = MaterialTheme.typography.displaySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                modifier = Modifier.alignByBaseline(),
                text = timerText,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = stringResource(R.string.encrypt_with))

        Box(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)) {
            KeyStoreListScreen(
                onSelectionChanged = { alias ->
                    selectedAlias = alias
                }
            )
        }

        Box(modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()) {
            OutputScreen(outputViewModel = outputViewModel)
        }
        }
    }
}
