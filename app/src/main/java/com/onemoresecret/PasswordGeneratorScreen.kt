package com.onemoresecret

import android.content.Context
import android.widget.NumberPicker
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import com.onemoresecret.crypto.*
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays

const val PROP_UCASE = "pwgen_ucase"
const val PROP_UCASE_LIST = "pwgen_ucase_list"
const val PROP_LCASE = "pwgen_lcase"
const val PROP_LCASE_LIST = "pwgen_lcase_list"
const val PROP_DIGITS = "pwgen_digits"
const val PROP_DIGITS_LIST = "pwgen_digits_list"
const val PROP_SPECIALS = "pwgen_specials"
const val PROP_SPECIALS_LIST = "pwgen_specials_list"
const val PROP_SIMILAR = "pwgen_similar"
const val PROP_SIMILAR_LIST = "pwgen_similar_LIST"
const val PROP_LAYOUT = "pwgen_layout"
const val PROP_PWD_LENGTH = "pwgen_length"
const val PROP_OCCURS = "pwgen_occurrs"
const val DEFAULT_DIGITS = "0123456789"
const val DEFAULT_LCASE = "abcdefghijklmnopqrstuvwxyz"
const val DEFAULT_SPECIALS = "!#\$%&'*+,-.:;<=>?@_~"
const val DEFAULT_SIMILAR = "01IOl|"
const val PWD_LEN_DEFAULT = 10
const val PWD_LEN_MIN = 5
const val PWD_LEN_MAX = 50
const val OCCURS_DEFAULT = 1
const val OCCURS_MIN = 1
const val OCCURS_MAX = 10

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PasswordGeneratorScreen() {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    var pwdLength by remember { mutableIntStateOf(preferences.getInt(PROP_PWD_LENGTH, PWD_LEN_DEFAULT)) }
    var occurs by remember { mutableIntStateOf(preferences.getInt(PROP_OCCURS, OCCURS_DEFAULT)) }
    var uCase by remember { mutableStateOf(preferences.getBoolean(PROP_UCASE, true)) }
    var lCase by remember { mutableStateOf(preferences.getBoolean(PROP_LCASE, true)) }
    var digits by remember { mutableStateOf(preferences.getBoolean(PROP_DIGITS, true)) }
    var specials by remember { mutableStateOf(preferences.getBoolean(PROP_SPECIALS, true)) }
    var similar by remember { mutableStateOf(preferences.getBoolean(PROP_SIMILAR, true)) }
    var layout by remember { mutableStateOf(preferences.getBoolean(PROP_LAYOUT, true)) }

    var passwordText by remember { mutableStateOf("") }
    var isControlsEnabled by remember { mutableStateOf(true) }

    var showPwdLenDialog by remember { mutableStateOf(false) }
    var showOccursDialog by remember { mutableStateOf(false) }
    var showCharListDialogFor by remember { mutableStateOf<String?>(null) }
    
    var selectedAlias by remember { mutableStateOf<String?>(null) }
    val cryptographyManager = remember { CryptographyManager() }

    val newPassword = {
        if (selectedAlias != null) {
            // Cannot generate new password when a key is selected
        } else {
            var charClasses = mutableListOf<String>()

            if (uCase) charClasses.add(preferences.getString(PROP_UCASE_LIST, DEFAULT_LCASE.uppercase())!!)
            if (lCase) charClasses.add(preferences.getString(PROP_LCASE_LIST, DEFAULT_LCASE)!!)
            if (digits) charClasses.add(preferences.getString(PROP_DIGITS_LIST, DEFAULT_DIGITS)!!)
            if (specials) charClasses.add(preferences.getString(PROP_SPECIALS_LIST, DEFAULT_SPECIALS)!!)

            val size = charClasses.size

            if (!similar) {
                val blacklist = preferences.getString(PROP_SIMILAR_LIST, DEFAULT_SIMILAR)!!.toCharArray()
                Arrays.sort(blacklist)

                for (i in 0 until size) {
                    val sb = java.lang.StringBuilder()
                    val cArr = charClasses.removeAt(0).toCharArray()
                    for (c in cArr) {
                        if (Arrays.binarySearch(blacklist, c) < 0) sb.append(c)
                    }
                    charClasses.add(sb.toString())
                }
            }

            if (layout) {
                // TODO: we need to check if selectedLayout is available from OutputScreen
                // For now, let's just use the characters
            }

            for (i in 0 until size) {
                val s = charClasses.removeAt(0)
                charClasses.add(s.replace(Regex("(.)\\1+"), "$1"))
            }

            charClasses = charClasses.filter { it.isNotEmpty() }.toMutableList()

            if (charClasses.isEmpty()) {
                Toast.makeText(context, R.string.no_symbols_available, Toast.LENGTH_LONG).show()
            } else {
                val list = mutableListOf<String>()
                for (i in 0 until occurs) {
                    list.addAll(charClasses)
                }

                val rnd = SecureRandom()

                while (list.size < pwdLength) {
                    list.add(charClasses[rnd.nextInt(charClasses.size)])
                }

                val sb = java.lang.StringBuilder()
                for (i in 0 until pwdLength) {
                    val s = list.removeAt(rnd.nextInt(list.size))
                    val cArr = s.toCharArray()
                    sb.append(cArr[rnd.nextInt(cArr.size)])
                }

                passwordText = sb.toString()
                isControlsEnabled = true
            }
        }
    }
    
    LaunchedEffect(Unit) {
        newPassword()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(context.getString(R.string.password_generator)) },
                actions = {
                    if (selectedAlias == null) {
                        IconButton(onClick = { newPassword() }) {
                            Icon(Icons.Filled.Password, contentDescription = "Generate")
                        }
                    }
                    // Add other menu items (Ucase, Lcase etc) as DropdownMenuItem if desired
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomFilterChip(
                    label = context.getString(R.string.chip_upper_case),
                    selected = uCase,
                    enabled = isControlsEnabled,
                    onCheckedChange = { 
                        preferences.edit { putBoolean(PROP_UCASE, it) }
                        uCase = it; newPassword() 
                    }
                )
                CustomFilterChip(
                    label = context.getString(R.string.chip_lower_case),
                    selected = lCase,
                    enabled = isControlsEnabled,
                    onCheckedChange = { 
                        preferences.edit { putBoolean(PROP_LCASE, it) }
                        lCase = it; newPassword() 
                    }
                )
                CustomFilterChip(
                    label = context.getString(R.string.chip_numbers),
                    selected = digits,
                    enabled = isControlsEnabled,
                    onCheckedChange = { 
                        preferences.edit { putBoolean(PROP_DIGITS, it) }
                        digits = it; newPassword() 
                    }
                )
                CustomFilterChip(
                    label = context.getString(R.string.chip_specials),
                    selected = specials,
                    enabled = isControlsEnabled,
                    onCheckedChange = { 
                        preferences.edit { putBoolean(PROP_SPECIALS, it) }
                        specials = it; newPassword() 
                    }
                )
                CustomFilterChip(
                    label = context.getString(R.string.chip_similar),
                    selected = similar,
                    enabled = isControlsEnabled,
                    onCheckedChange = { 
                        preferences.edit { putBoolean(PROP_SIMILAR, it) }
                        similar = it; newPassword() 
                    }
                )
                CustomFilterChip(
                    label = context.getString(R.string.chip_layout),
                    selected = layout,
                    enabled = isControlsEnabled,
                    onCheckedChange = { 
                        preferences.edit { putBoolean(PROP_LAYOUT, it) }
                        layout = it; newPassword() 
                    }
                )
                
                AssistChip(
                    onClick = { showPwdLenDialog = true },
                    label = { Text(pwdLength.toString()) },
                    leadingIcon = { Icon(Icons.Filled.Password, contentDescription = null) },
                    enabled = isControlsEnabled,
                    shape = RoundedCornerShape(50)
                )

                AssistChip(
                    onClick = { showOccursDialog = true },
                    label = { Text(occurs.toString()) },
                    leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
                    enabled = isControlsEnabled,
                    shape = RoundedCornerShape(50)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = passwordText,
                onValueChange = { 
                    passwordText = it
                    if (selectedAlias != null) {
                        try {
                            val keyStoreEntry = cryptographyManager.getByAlias(selectedAlias!!, preferences)
                            val encryptedMessage = EncryptedMessage(
                                it.toByteArray(StandardCharsets.UTF_8),
                                java.util.Objects.requireNonNull(keyStoreEntry)?.public!!,
                                RSAUtil.getRsaTransformation(preferences),
                                AESUtil.getKeyLength(preferences),
                                AESUtil.getAesTransformation(preferences)
                            ).message
                            val encrypted = MessageComposer.encodeAsOmsText(encryptedMessage)
                            isControlsEnabled = false
                        } catch (e: Exception) {
                            throw RuntimeException(e)
                        }
                    } else {
                        isControlsEnabled = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(context.getString(R.string.password)) },
                enabled = isControlsEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = context.getString(R.string.encrypt_with),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                KeyStoreListScreen(onSelectionChanged = { alias -> 
                    selectedAlias = alias
                    if (alias != null) {
                        // trigger encryptPwd
                        passwordText = passwordText // to trigger recompose/encrypt
                    } else {
                        isControlsEnabled = true
                    }
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomFilterChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { onCheckedChange(!selected) },
        label = { Text(label) },
        enabled = enabled,
        shape = RoundedCornerShape(50)
    )
}
