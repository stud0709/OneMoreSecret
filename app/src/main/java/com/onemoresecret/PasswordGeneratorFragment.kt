package com.onemoresecret

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.selection.SelectionTracker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.crypto.*
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Arrays
import java.util.Objects
import java.util.stream.Collectors
import androidx.core.content.edit

class PasswordGeneratorFragment : Fragment() {
    private val menuProvider = PwdMenuProvider()
    private lateinit var preferences: SharedPreferences
    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var outputFragment: OutputFragment? = null
    private val cryptographyManager = CryptographyManager()

    companion object {
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
        const val DEFAULT_SPECIALS = "!#$%&'*+,-.:;<=>?@_~"
        const val DEFAULT_SIMILAR = "01IOl|"
        const val PWD_LEN_DEFAULT = 10
        const val PWD_LEN_MIN = 5
        const val PWD_LEN_MAX = 50
        const val OCCURS_DEFAULT = 1
        const val OCCURS_MIN = 1
        const val OCCURS_MAX = 10
    }

    private var pwdLength by mutableIntStateOf(PWD_LEN_DEFAULT)
    private var occurs by mutableIntStateOf(OCCURS_DEFAULT)
    private var uCase by mutableStateOf(true)
    private var lCase by mutableStateOf(true)
    private var digits by mutableStateOf(true)
    private var specials by mutableStateOf(true)
    private var similar by mutableStateOf(true)
    private var layout by mutableStateOf(true)

    private var passwordText by mutableStateOf("")
    private var isControlsEnabled by mutableStateOf(true)

    private var showPwdLenDialog by mutableStateOf(false)
    private var showOccursDialog by mutableStateOf(false)
    private var showCharListDialogFor by mutableStateOf<String?>(null)

    private val onSharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (PROP_PWD_LENGTH == key) {
                pwdLength = preferences.getInt(key, PWD_LEN_DEFAULT)
            } else if (PROP_OCCURS == key) {
                occurs = preferences.getInt(key, OCCURS_DEFAULT)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        pwdLength = preferences.getInt(PROP_PWD_LENGTH, PWD_LEN_DEFAULT)
        occurs = preferences.getInt(PROP_OCCURS, OCCURS_DEFAULT)
        uCase = preferences.getBoolean(PROP_UCASE, true)
        lCase = preferences.getBoolean(PROP_LCASE, true)
        digits = preferences.getBoolean(PROP_DIGITS, true)
        specials = preferences.getBoolean(PROP_SPECIALS, true)
        similar = preferences.getBoolean(PROP_SIMILAR, true)
        layout = preferences.getBoolean(PROP_LAYOUT, true)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        PasswordGeneratorScreen()
                    }
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider, viewLifecycleOwner, Lifecycle.State.RESUMED)
        preferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)

        setupChildFragments()

        requireActivity().mainExecutor.execute { newPassword() }
    }

    private var isObserverAdded = false

    private fun setupChildFragments() {
        keyStoreListFragment = childFragmentManager.findFragmentByTag("keyStoreListFragment") as? KeyStoreListFragment
            ?: KeyStoreListFragment()

        outputFragment = childFragmentManager.findFragmentByTag("outputFragment") as? OutputFragment
            ?: OutputFragment()
    }

    private fun setupKeyStoreObserver() {
        if (isObserverAdded) return
        keyStoreListFragment?.setRunOnStart { tracker ->
            tracker.addObserver(object : SelectionTracker.SelectionObserver<String>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()
                    if (tracker.hasSelection()) {
                        val selectedAlias = tracker.selection.firstOrNull()
                        if (selectedAlias != null) encryptPwd(selectedAlias, passwordText)
                    } else {
                        setPwd(passwordText)
                    }
                }
            })
        }
        isObserverAdded = true
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun PasswordGeneratorScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CustomFilterChip(
                    label = getString(R.string.chip_upper_case),
                    selected = uCase,
                    enabled = isControlsEnabled,
                    onCheckedChange = { onPropertyChange(PROP_UCASE, it) }
                )
                CustomFilterChip(
                    label = getString(R.string.chip_lower_case),
                    selected = lCase,
                    enabled = isControlsEnabled,
                    onCheckedChange = { onPropertyChange(PROP_LCASE, it) }
                )
                CustomFilterChip(
                    label = getString(R.string.chip_numbers),
                    selected = digits,
                    enabled = isControlsEnabled,
                    onCheckedChange = { onPropertyChange(PROP_DIGITS, it) }
                )
                CustomFilterChip(
                    label = getString(R.string.chip_specials),
                    selected = specials,
                    enabled = isControlsEnabled,
                    onCheckedChange = { onPropertyChange(PROP_SPECIALS, it) }
                )
                CustomFilterChip(
                    label = getString(R.string.chip_similar),
                    selected = similar,
                    enabled = isControlsEnabled,
                    onCheckedChange = { onPropertyChange(PROP_SIMILAR, it) }
                )
                CustomFilterChip(
                    label = getString(R.string.chip_layout),
                    selected = layout,
                    enabled = isControlsEnabled,
                    onCheckedChange = { onPropertyChange(PROP_LAYOUT, it) }
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
                    setPwd(it)
                    if (keyStoreListFragment?.getSelectionTracker()?.hasSelection() == true) {
                        val selectedAlias = keyStoreListFragment?.getSelectionTracker()?.selection?.firstOrNull()
                        if (selectedAlias != null) encryptPwd(selectedAlias, it)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(getString(R.string.password)) },
                enabled = isControlsEnabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = getString(R.string.encrypt_with),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { context ->
                        FragmentContainerView(context).apply {
                            id = R.id.keyStoreListContainer
                        }
                    },
                    update = { _ ->
                        if (childFragmentManager.findFragmentById(R.id.keyStoreListContainer) == null) {
                            childFragmentManager.commit {
                                replace(R.id.keyStoreListContainer, keyStoreListFragment!!, "keyStoreListFragment")
                            }
                        }
                        setupKeyStoreObserver()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Box(modifier = Modifier.wrapContentHeight()) {
                AndroidView(
                    factory = { context ->
                        FragmentContainerView(context).apply {
                            id = R.id.outputContainer
                        }
                    },
                    update = { _ ->
                        if (childFragmentManager.findFragmentById(R.id.outputContainer) == null) {
                            childFragmentManager.commit {
                                replace(R.id.outputContainer, outputFragment!!, "outputFragment")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            Dialogs()
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

    @Composable
    private fun Dialogs() {
        if (showPwdLenDialog) {
            var tempLen by remember { mutableStateOf(pwdLength) }
            AlertDialog(
                onDismissRequest = { showPwdLenDialog = false },
                title = { Text("Password length") },
                text = {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        AndroidView(
                            factory = { context ->
                                NumberPicker(context).apply {
                                    minValue = PWD_LEN_MIN
                                    maxValue = PWD_LEN_MAX
                                    value = pwdLength
                                    setOnValueChangedListener { _, _, newVal ->
                                        tempLen = newVal
                                    }
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        preferences.edit { putInt(PROP_PWD_LENGTH, tempLen) }
                        pwdLength = tempLen
                        newPassword()
                        showPwdLenDialog = false
                    }) {
                        Text(getString(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPwdLenDialog = false }) {
                        Text(getString(android.R.string.cancel))
                    }
                }
            )
        }

        if (showOccursDialog) {
            var tempOccurs by remember { mutableStateOf(occurs) }
            AlertDialog(
                onDismissRequest = { showOccursDialog = false },
                title = { Text("Occurrence") },
                text = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("…of every character class (at least)")
                        Spacer(modifier = Modifier.height(16.dp))
                        AndroidView(
                            factory = { context ->
                                NumberPicker(context).apply {
                                    minValue = OCCURS_MIN
                                    maxValue = OCCURS_MAX
                                    value = occurs
                                    setOnValueChangedListener { _, _, newVal ->
                                        tempOccurs = newVal
                                    }
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        preferences.edit { putInt(PROP_OCCURS, tempOccurs) }
                        occurs = tempOccurs
                        newPassword()
                        showOccursDialog = false
                    }) {
                        Text(getString(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOccursDialog = false }) {
                        Text(getString(android.R.string.cancel))
                    }
                }
            )
        }

        showCharListDialogFor?.let { propertyName ->
            val defaultList: String
            val title: String
            when (propertyName) {
                PROP_DIGITS_LIST -> {
                    defaultList = DEFAULT_DIGITS
                    title = getString(R.string.digits)
                }
                PROP_LCASE_LIST -> {
                    defaultList = DEFAULT_LCASE
                    title = getString(R.string.lower_case)
                }
                PROP_SIMILAR_LIST -> {
                    defaultList = DEFAULT_SIMILAR
                    title = getString(R.string.similar)
                }
                PROP_SPECIALS_LIST -> {
                    defaultList = DEFAULT_SPECIALS
                    title = getString(R.string.specials)
                }
                PROP_UCASE_LIST -> {
                    defaultList = DEFAULT_LCASE.uppercase()
                    title = getString(R.string.upper_case)
                }
                else -> throw IllegalArgumentException()
            }
            
            var textValue by remember { mutableStateOf(preferences.getString(propertyName, defaultList) ?: defaultList) }

            AlertDialog(
                onDismissRequest = { showCharListDialogFor = null },
                title = { Text(title) },
                text = {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { textValue = it }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        preferences.edit { putString(propertyName, textValue) }
                        newPassword()
                        showCharListDialogFor = null
                    }) {
                        Text(getString(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCharListDialogFor = null }) {
                        Text(getString(android.R.string.cancel))
                    }
                }
            )
        }
    }

    private fun newPassword() {
        if (keyStoreListFragment?.getSelectionTracker()?.hasSelection() == true) {
            throw IllegalStateException("Generating new encrypted password")
        }

        var charClasses = mutableListOf<String>()

        if (uCase) {
            charClasses.add(preferences.getString(PROP_UCASE_LIST, DEFAULT_LCASE.uppercase())!!)
        }
        if (lCase) {
            charClasses.add(preferences.getString(PROP_LCASE_LIST, DEFAULT_LCASE)!!)
        }
        if (digits) {
            charClasses.add(preferences.getString(PROP_DIGITS_LIST, DEFAULT_DIGITS)!!)
        }
        if (specials) {
            charClasses.add(preferences.getString(PROP_SPECIALS_LIST, DEFAULT_SPECIALS)!!)
        }

        val size = charClasses.size

        if (!similar) {
            val blacklist = preferences.getString(PROP_SIMILAR_LIST, DEFAULT_SIMILAR)!!.toCharArray()
            Arrays.sort(blacklist)

            for (i in 0 until size) {
                val sb = java.lang.StringBuilder()
                val cArr = charClasses.removeAt(0).toCharArray()
                for (c in cArr) {
                    if (Arrays.binarySearch(blacklist, c) < 0) {
                        sb.append(c)
                    }
                }
                charClasses.add(sb.toString())
            }
        }

        if (layout) {
            val keyboardLayout = outputFragment?.selectedLayout
            if (keyboardLayout == null) {
                Toast.makeText(requireContext(), R.string.cannot_apply_layout_filter, Toast.LENGTH_LONG).show()
                return
            }

            for (i in 0 until size) {
                val sb = java.lang.StringBuilder()
                val cArr = charClasses.removeAt(0).toCharArray()
                for (c in cArr) {
                    if (keyboardLayout.forKey(c) != null) {
                        sb.append(c)
                    }
                }
                charClasses.add(sb.toString())
            }
        }

        // Remove duplicates
        for (i in 0 until size) {
            val s = charClasses.removeAt(0)
            charClasses.add(s.replace(Regex("(.)\\1+"), "$1"))
        }

        // Remove empty classes
        charClasses = charClasses.filter { it.isNotEmpty() }.toMutableList()

        if (charClasses.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_symbols_available, Toast.LENGTH_LONG).show()
            return
        }

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
        setPwd(passwordText)
    }

    private fun setPwd(pwd: String) {
        isControlsEnabled = true
        outputFragment?.setMessage(pwd, getString(R.string.unprotected_password))
        requireActivity().invalidateOptionsMenu()
    }

    private fun encryptPwd(alias: String, pwd: String) {
        try {
            val keyStoreEntry = cryptographyManager.getByAlias(alias, preferences)
            val encryptedMessage = EncryptedMessage(
                pwd.toByteArray(StandardCharsets.UTF_8),
                Objects.requireNonNull(keyStoreEntry)?.public!!,
                RSAUtil.getRsaTransformation(preferences),
                AESUtil.getKeyLength(preferences),
                AESUtil.getAesTransformation(preferences)
            ).message
            
            val encrypted = MessageComposer.encodeAsOmsText(encryptedMessage)

            isControlsEnabled = false
            outputFragment?.setMessage(encrypted, getString(R.string.encrypted_password))
            requireActivity().invalidateOptionsMenu()
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    private fun onPropertyChange(propertyName: String, value: Boolean) {
        preferences.edit { putBoolean(propertyName, value) }
        when (propertyName) {
            PROP_UCASE -> uCase = value
            PROP_LCASE -> lCase = value
            PROP_DIGITS -> digits = value
            PROP_SPECIALS -> specials = value
            PROP_SIMILAR -> similar = value
            PROP_LAYOUT -> layout = value
        }
        newPassword()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        preferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
        
        val ksf = childFragmentManager.findFragmentById(R.id.keyStoreListContainer)
        val of = childFragmentManager.findFragmentById(R.id.outputContainer)
        
        if (ksf != null || of != null) {
            val tx = childFragmentManager.beginTransaction()
            if (ksf != null) tx.remove(ksf)
            if (of != null) tx.remove(of)
            tx.commitNowAllowingStateLoss()
        }
    }

    private inner class PwdMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_pwd_generator, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
            val hasSelection = keyStoreListFragment?.getSelectionTracker()?.hasSelection() == true
            menu.findItem(R.id.menuItemGenPwd)?.isVisible = !hasSelection
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            return when (menuItem.itemId) {
                R.id.menuItemGenPwd -> {
                    newPassword()
                    true
                }
                R.id.menuItemUcase -> {
                    showCharListDialogFor = PROP_UCASE_LIST
                    true
                }
                R.id.menuItemLcase -> {
                    showCharListDialogFor = PROP_LCASE_LIST
                    true
                }
                R.id.menuItemDigits -> {
                    showCharListDialogFor = PROP_DIGITS_LIST
                    true
                }
                R.id.menuItemSpecials -> {
                    showCharListDialogFor = PROP_SPECIALS_LIST
                    true
                }
                R.id.menuItemSimilar -> {
                    showCharListDialogFor = PROP_SIMILAR_LIST
                    true
                }
                R.id.menuItemPwGenHelp -> {
                    Util.openUrl(R.string.pwd_generator_md_url, requireContext())
                    true
                }
                else -> false
            }
        }
    }
}
