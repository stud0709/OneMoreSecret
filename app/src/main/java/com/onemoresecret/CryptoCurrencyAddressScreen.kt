package com.onemoresecret

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import com.onemoresecret.crypto.*
import com.onemoresecret.qr.QRUtil
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoCurrencyAddressScreen() {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    val outputViewModel: OutputViewModel = viewModel(
        factory = OutputViewModel.Factory(preferences)
    )

    var btcKeyPair by remember { mutableStateOf<BTCAddress.BTCKeyPair?>(null) }
    var address by remember { mutableStateOf("") }
    var selectedAlias by remember { mutableStateOf<String?>(null) }
    var backupHtml by remember { mutableStateOf<String?>(null) }

    val cryptographyManager = remember { CryptographyManager() }

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
                    outputViewModel.setMessage(encoded, context.getString(R.string.wif_encrypted))
                    
                    // Generate backup HTML
                    val stringBuilder = java.lang.StringBuilder()
                    val list = QRUtil.getQrSequence(
                        encoded,
                        QRUtil.getChunkSize(preferences),
                        QRUtil.getBarcodeSize(preferences)
                    )

                    stringBuilder
                        .append("<html><body><h1>")
                        .append("OneMoreSecret Cold Wallet")
                        .append("</h1>")
                        .append("<p>This is a hard copy of your Bitcoin Address <b>")
                        .append(kp.btcAddressBase58)
                        .append("</b>:</p><p>")

                    ByteArrayOutputStream().use { baos ->
                        val bitmap = QRUtil.getQr(kp.btcAddressBase58, QRUtil.getBarcodeSize(preferences))
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                        baos.flush()
                        stringBuilder.append("<img src=\"data:image/png;base64,")
                            .append(Base64.getEncoder().encodeToString(baos.toByteArray()))
                            .append("\" style=\"width:200px;height:200px;\">")
                    }

                    stringBuilder.append("</p>The above QR code contains the public bitcoin address, ")
                        .append("use a regular QR code scanner to read it.</p><p>The private key is encrypted, ")
                        .append("scan the following QR code sequence <b>with OneMoreSecret</b> to access it:</p><p>")

                    for (i in list.indices) {
                        val bitmap = list[i]
                        ByteArrayOutputStream().use { baos ->
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                            baos.flush()
                            stringBuilder.append("<table style=\"display: inline-block;\"><tr style=\"vertical-align: bottom;\"><td>")
                                .append(i + 1)
                                .append("</td><td><img src=\"data:image/png;base64,")
                                .append(Base64.getEncoder().encodeToString(baos.toByteArray()))
                                .append("\" style=\"width:200px;height:200px;\"></td></tr></table>")
                        }
                    }
                    stringBuilder
                        .append("</p><p>")
                        .append("The same as text:")
                        .append("&nbsp;")
                        .append("</p><p style=\"font-family:monospace;\">")

                    var offset = 0
                    while (offset < encoded.length) {
                        val s = encoded.substring(offset, kotlin.math.min(offset + Util.BASE64_LINE_LENGTH, encoded.length))
                        stringBuilder.append(s).append("<br>")
                        offset += Util.BASE64_LINE_LENGTH
                    }

                    stringBuilder.append("</p><p>")
                        .append("Message format: oms00_[base64 encoded data]")
                        .append("</p><p>")
                        .append("Data format: see https://github.com/stud0709/OneMoreSecret/blob/master/app/src/main/java/com/onemoresecret/crypto/EncryptedCryptoCurrencyAddress.java")
                        .append("</p></body></html>")

                    backupHtml = stringBuilder.toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            backupHtml = null
            outputViewModel.setMessage(address, context.getString(R.string.public_address))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crypto_address_generator)) },
                actions = {
                    IconButton(onClick = { newBitcoinAddress() }) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = "New Address")
                    }
                    IconButton(onClick = {
                        Util.openUrl(R.string.crypto_address_generator_url, context)
                    }) {
                        Icon(Icons.Default.Help, contentDescription = "Help")
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
            Text(text = stringResource(R.string.public_address))

            Text(
                text = address.chunked(4).joinToString(" "),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Text(
                text = "WARNING: EXPERIMENTAL FEATURE!",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(text = stringResource(R.string.encrypt_with))

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                            context.startActivity(Intent.createChooser(intent, String.format(context.getString(R.string.backup_file), address)))
                        } catch (ex: Exception) {
                            Toast.makeText(context, ex.message, Toast.LENGTH_LONG).show()
                            ex.printStackTrace()
                        }
                    }
                },
                enabled = backupHtml != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Create Backup")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(modifier = Modifier.wrapContentHeight().fillMaxWidth()) {
                OutputScreen(outputViewModel = outputViewModel)
            }
        }
    }
}
