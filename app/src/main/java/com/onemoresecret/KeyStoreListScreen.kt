package com.onemoresecret

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.onemoresecret.composable.PrivateKeyListItem
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.KeyStoreEntry
import com.onemoresecret.crypto.RSAUtil

@Composable
fun KeyStoreListScreen(
    onSelectionChanged: (String?) -> Unit
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    var aliasList by remember { mutableStateOf(emptyList<String>()) }
    var keyStoreEntries by remember { mutableStateOf(emptySet<KeyStoreEntry>()) }
    var selectedAlias by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val keyStoreStringSet = preferences.getStringSet(CryptographyManager.PROP_KEYSTORE, HashSet()) ?: emptySet()
        val entries = keyStoreStringSet.map { entry -> OmsJson.decode<KeyStoreEntry>(entry) }.toSet()
        keyStoreEntries = entries
        aliasList = entries.map { it.alias }.sorted()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        items(aliasList) { alias ->
            val entry = keyStoreEntries.firstOrNull { it.alias == alias }
            if (entry != null) {
                val publicKey = remember(entry) { RSAUtil.restorePublicKey(entry.public) }
                val fingerprint = remember(publicKey) { Util.byteArrayToHex(RSAUtil.getFingerprint(publicKey)) }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val newSelection = if (selectedAlias == alias) null else alias
                            selectedAlias = newSelection
                            onSelectionChanged(newSelection)
                        }
                ) {
                    PrivateKeyListItem(
                        alias = alias,
                        fingerprint = "…%s".format(fingerprint.takeLast(10)),
                        selected = selectedAlias == alias
                    )
                }
            }
        }
    }
}

