package com.onemoresecret

import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.OmsFileProvider.Companion.create
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.composable.PasswordField
import com.onemoresecret.crypto.AESUtil.generateSalt
import com.onemoresecret.crypto.AESUtil.getAesKeyAlgorithm
import com.onemoresecret.crypto.AESUtil.getAesKeyMaterialFromPassword
import com.onemoresecret.crypto.AESUtil.getAesTransformation
import com.onemoresecret.crypto.AESUtil.getKeySpecIterations
import com.onemoresecret.crypto.AesEncryptedPrivateKeyTransfer
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.CryptographyManager.Companion.generateKeyPair
import com.onemoresecret.crypto.RSAUtil.getFingerprint
import com.onemoresecret.crypto.RSAUtil.restorePublicKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.SecureRandom
import java.util.Arrays


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPrivateKeyScreen(
    onPopBackStack: () -> Unit
) {
    val context = LocalContext.current
    val preferences = OmsPreferences.get(context)

    val viewModel: NewPrivateKeyViewModel = viewModel(
        factory = NewPrivateKeyViewModel.Factory(
            preferences,
            CryptographyManager()
        )
    )

    val strBackupFile = stringResource(R.string.backup_file)

    LaunchedEffect(viewModel) {
        viewModel.event.collect { event ->
            when (event) {
                is NewPrivateKeyViewModel.Event.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
                }
                is NewPrivateKeyViewModel.Event.PopBackStack -> {
                    onPopBackStack()
                }
                is NewPrivateKeyViewModel.Event.ShareFile -> {
                    val intent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, event.fileRecordUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        type = "text/html"
                    }
                    context.startActivity(
                        Intent.createChooser(
                            intent,
                            String.format(strBackupFile, event.alias)
                        )
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.new_private_key), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = {
                        Util.openUrl(R.string.new_private_key_md_url, context)
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = "Help"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NewPrivateKey(viewModel)
        }
    }
}



@Composable
fun NewPrivateKey(viewModel: NewPrivateKeyViewModel = viewModel()) {
    val context = LocalContext.current
    NewPrivateKeyScreen(
        state = viewModel.state,
        onAction = viewModel::onAction,
        onCreate = { viewModel.createPrivateKey(context) },
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



class NewPrivateKeyViewModel(
    private val preferences: SharedPreferences, private val cryptographyManager: CryptographyManager
) : ViewModel() {
    var state by mutableStateOf(State())
    private val _event = MutableSharedFlow<Event>()
    val event = _event.asSharedFlow()
    var onActivate: () -> Unit = {}

    sealed class Event {
        data class ShowToast(val message: String) : Event()
        data object PopBackStack : Event()
        data class ShareFile(val fileRecordUri: android.net.Uri, val alias: String) : Event()
    }

    fun onAction(action: Action) {
        state = when (action) {
            is Action.OnAliasChanged -> state.copy(alias = action.value)
            is Action.OnPasswordChanged -> state.copy(password = action.value)
            is Action.OnRepeatPasswordChanged -> state.copy(repeatPassword = action.value)
            is Action.On4096BitChanged -> state.copy(is4096Bit = action.enabled)
            is Action.OnBackupConsentChanged -> state.copy(isConsentChecked = action.enabled)
        }
    }

    fun createPrivateKey(context: android.content.Context) {
        viewModelScope.launch {
            try {
                if (state.alias.isEmpty()) {
                    _event.emit(Event.ShowToast(context.getString(R.string.key_alias_may_not_be_empty)))
                    return@launch
                }
                cryptographyManager.getByAlias(state.alias, preferences)?.let {
                    _event.emit(Event.ShowToast(context.getString(R.string.key_alias_already_exists)))
                    return@launch
                }
                if (state.password.length < 10) {
                    _event.emit(Event.ShowToast(context.getString(R.string.password_too_short)))
                    return@launch
                }
                if (state.password != state.repeatPassword) {
                    _event.emit(Event.ShowToast(context.getString(R.string.password_mismatch)))
                    return@launch
                }

                val keyMaterial = generateKeyPair(if (state.is4096Bit) 4096 else 2048)
                val publicKeyMaterial = keyMaterial.first
                val privateKeyMaterial = keyMaterial.second
                val publicKey = restorePublicKey(publicKeyMaterial)
                val fingerprint = getFingerprint(publicKey)
                val ivSize = getAesTransformation(preferences).ivLength
                val iv = ByteArray(ivSize)
                SecureRandom().nextBytes(iv)
                val salt = generateSalt(16)
                val aesKeyLength = 256
                val aesKeySpecIterations = getKeySpecIterations(preferences)
                require(aesKeySpecIterations in 1..org.onemoresecret.protocol.OmsProtocol.MAX_ITERATIONS) { "Unsupported PBKDF2 iteration count" }
                val aesKeyAlgorithm = "PBKDF2WithHmacSHA256"

                val aesKeyMaterial = getAesKeyMaterialFromPassword(
                    state.password.toCharArray(),
                    salt,
                    aesKeyAlgorithm,
                    aesKeyLength,
                    aesKeySpecIterations
                )

                val message = AesEncryptedPrivateKeyTransfer(
                    state.alias,
                    privateKeyMaterial,
                    publicKeyMaterial,
                    aesKeyMaterial,
                    iv,
                    salt,
                    getAesTransformation(preferences),
                    getAesKeyAlgorithm(preferences),
                    aesKeyLength,
                    aesKeySpecIterations
                ).message

                Arrays.fill(aesKeyMaterial, 0.toByte())

                onActivate = {
                    viewModelScope.launch {
                        try {
                            cryptographyManager.importRsaKey(
                                preferences, privateKeyMaterial, publicKeyMaterial, state.alias
                            )

                            Arrays.fill(privateKeyMaterial, 0.toByte()) //wipe private key data
                            _event.emit(Event.ShowToast(context.getString(R.string.key_successfully_activated)))
                            _event.emit(Event.PopBackStack)
                        } catch (ex: Exception) {
                            printStackTrace(ex)
                            _event.emit(Event.ShowToast(ex.message ?: ex.toString()))
                        }
                    }
                }

                state = state.copy(isKeyCreated = true)

                //share HTML file
                val html = PrivateKeyBackupDocument.createHtml(preferences, state.alias, fingerprint, message)
                val fingerprintString = byteArrayToHex(fingerprint).replace("\\s".toRegex(), "_")

                val fileRecord = create(context, "pk_$fingerprintString.html", true)
                Files.write(fileRecord.path, html.toByteArray(StandardCharsets.UTF_8))
                fileRecord.path!!.toFile().deleteOnExit()

                _event.emit(Event.ShareFile(fileRecord.uri!!, state.alias))
            } catch (ex: Exception) {
                printStackTrace(ex)
                _event.emit(Event.ShowToast(ex.message ?: ex.toString()))
            }
        }
    }

    sealed class Action {
        data class OnAliasChanged(val value: String) : Action()
        data class OnPasswordChanged(val value: String) : Action()
        data class OnRepeatPasswordChanged(val value: String) : Action()
        data class On4096BitChanged(val enabled: Boolean) : Action()
        data class OnBackupConsentChanged(val enabled: Boolean) : Action()
    }

    data class State(
        val alias: String = "",
        val password: String = "",
        val repeatPassword: String = "",
        val is4096Bit: Boolean = false,
        val isConsentChecked: Boolean = false,
        val isKeyCreated: Boolean = false
    )

    class Factory(
        private val preferences: SharedPreferences,
        private val cryptographyManager: CryptographyManager
    ) :
        ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(/* cls = */ NewPrivateKeyViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return NewPrivateKeyViewModel(preferences, cryptographyManager) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}