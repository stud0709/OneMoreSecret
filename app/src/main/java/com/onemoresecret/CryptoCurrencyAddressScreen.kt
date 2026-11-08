package com.onemoresecret

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import com.onemoresecret.crypto.AESUtil
import com.onemoresecret.crypto.BTCAddress
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedCryptoCurrencyAddress
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.crypto.RSAUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoCurrencyAddressScreen() {
    val context = LocalContext.current
    val preferences = OmsPreferences.get(context)

    val outputViewModel: OutputViewModel = viewModel(
        factory = OutputViewModel.Factory(preferences)
    )

    var btcKeyPair by remember { mutableStateOf<BTCAddress.BTCKeyPair?>(null) }
    var address by remember { mutableStateOf("") }
    var selectedAlias by remember { mutableStateOf<String?>(null) }
    var backupHtml by remember { mutableStateOf<String?>(null) }

    val cryptographyManager = remember { CryptographyManager() }

    val strWifEncrypted = stringResource(R.string.wif_encrypted)
    val strPublicAddress = stringResource(R.string.public_address)
    val strBackupFile = stringResource(R.string.backup_file)

    val newBitcoinAddress = {
        try {
            btcKeyPair = BTCAddress.newKeyPair().toBTCKeyPair()
            address = btcKeyPair!!.btcAddressBase58
            backupHtml = null // Reset backup since key changed
        } catch (e: Exception) {
            Util.printStackTrace(e)
            Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
        }
    }

    // Initialize with a new address if empty
    LaunchedEffect(Unit) {
        if (address.isEmpty()) {
            newBitcoinAddress()
        }
    }

    LaunchedEffect(address, selectedAlias) {
        if (selectedAlias != null && btcKeyPair != null) {
            try {
                val kp = btcKeyPair!!
                val keyStoreEntry = cryptographyManager.getByAlias(selectedAlias!!, preferences)
                if (keyStoreEntry != null) {
                    val encryptedMessage = EncryptedCryptoCurrencyAddress(
                        MessageComposer.APPLICATION_BITCOIN_ADDRESS,
                        kp.wif,
                        keyStoreEntry.public,
                        RSAUtil.getRsaTransformation(preferences),
                        AESUtil.getKeyLength(preferences),
                        AESUtil.getAesTransformation(preferences)
                    ).message

                    val encoded = MessageComposer.encodeAsOmsText(encryptedMessage)
                    outputViewModel.setMessage(encoded, strWifEncrypted)

                    backupHtml = BitcoinBackupDocument.createHtml(preferences, kp.btcAddressBase58, encoded)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            backupHtml = null
            outputViewModel.setMessage(address, strPublicAddress)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crypto_address_generator), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                actions = {

                    IconButton(onClick = { newBitcoinAddress() }) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = "New Address")
                    }
                    IconButton(onClick = {
                        Util.openUrl(R.string.crypto_address_generator_url, context)
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Help, contentDescription = "Help")
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
            Text(text = stringResource(R.string.public_address) + ":")

            Text(
                text = if (address.isEmpty()) "" else address.substring(0,2) + " " + address.substring(2).chunked(4).joinToString(" "),
                modifier = Modifier.padding(vertical = 8.dp).padding(bottom = 16.dp),
                fontFamily = com.onemoresecret.composable.JetBrainsMono
            )

            Text(text = stringResource(R.string.encrypt_with))

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()) {
                KeyStoreListScreen(
                    onSelectionChanged = { alias ->
                        selectedAlias = alias
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (backupHtml != null) {
                        try {
                            val fileRecord = OmsFileProvider.create(context, "${address}_backup.html", true)
                            Files.write(fileRecord.path, backupHtml!!.toByteArray(StandardCharsets.UTF_8))
                            fileRecord.path!!.toFile().deleteOnExit()

                            val intent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                type = "text/html"
                            }
                            context.startActivity(Intent.createChooser(intent, String.format(strBackupFile, address)))
                        } catch (ex: Exception) {
                            Toast.makeText(context, ex.message, Toast.LENGTH_LONG).show()
                            ex.printStackTrace()
                        }
                    }
                },
                enabled = backupHtml != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.create_backup))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()) {
                OutputScreen(outputViewModel = outputViewModel)
            }
        }
    }
}
