package com.onemoresecret

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.OneTimePassword
import com.onemoresecret.crypto.RSAUtil
import com.onemoresecret.crypto.TotpUriTransfer
import kotlinx.coroutines.delay

class TotpImportViewModel : ViewModel() {
    private val cryptographyManager = CryptographyManager()

    var messageText by mutableStateOf("")
    var messageTitle by mutableStateOf("")

    var totpValue by mutableStateOf("")
    var totpRemaining by mutableStateOf("")

    private var otp: OneTimePassword? = null
    private var lastState = -1L

    private var selectedAlias: String? = null
    private var originalMessage: ByteArray? = null

    fun init(message: ByteArray) {
        if (originalMessage != null) return
        originalMessage = message
        otp = OneTimePassword(String(message))
        require(otp!!.valid) { "Invalid scheme or authority" }
    }

    fun onAliasSelected(alias: String?, preferences: SharedPreferences) {
        selectedAlias = alias
        refreshMessage(preferences)
    }

    fun refreshTotp(preferences: SharedPreferences) {
        val currentOtp = otp ?: return
        val state = currentOtp.state
        totpRemaining = "...${currentOtp.period - state.secondsUntilNext}s"

        if (lastState != state.current) {
            totpValue = currentOtp.generateResponseCode(state.current)
            lastState = state.current
            refreshMessage(preferences)
        }
    }

    private fun refreshMessage(preferences: SharedPreferences) {
        if (selectedAlias != null) {
            val encrypted = encrypt(selectedAlias!!, originalMessage!!, preferences)
            messageText = encrypted
            messageTitle = "Encrypted OTP Configuration"
        } else {
            messageText = totpValue
            messageTitle = "One-Time Password"
        }
    }

    private fun encrypt(alias: String, message: ByteArray, preferences: SharedPreferences): String {
        try {
            return MessageComposer.encodeAsOmsText(
                TotpUriTransfer(
                    message,
                    cryptographyManager.getByAlias(alias, preferences)!!.public,
                    RSAUtil.getRsaTransformation(preferences),
                    AESUtil.getKeyLength(preferences),
                    AESUtil.getAesTransformation(preferences)
                ).message
            )
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpImportScreen(
    message: ByteArray,
    onImportCompleted: () -> Unit,
    viewModel: TotpImportViewModel = viewModel()
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    LaunchedEffect(message) {
        try {
            viewModel.init(message)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.message ?: e.javaClass.name,
                Toast.LENGTH_LONG
            ).show()
            onImportCompleted()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshTotp(preferences)
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.totp_import)) },
                actions = {
                    IconButton(onClick = { openUrl(R.string.totp_import_md_url, context) }) {
                        Icon(imageVector = Icons.Default.Help, contentDescription = stringResource(R.string.help))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = viewModel.totpValue,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = viewModel.totpRemaining,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.messageText,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                label = { Text(viewModel.messageTitle) },
                readOnly = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.encrypt_with),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                KeyStoreListScreen(onSelectionChanged = { alias -> 
                    viewModel.onAliasSelected(alias, preferences)
                })
            }
        }
    }
}
