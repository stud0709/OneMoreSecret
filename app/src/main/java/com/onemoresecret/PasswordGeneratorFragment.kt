package com.onemoresecret

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.Util.openUrl
import com.onemoresecret.crypto.AESUtil.getAesTransformationIdx
import com.onemoresecret.crypto.AESUtil.getKeyLength
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.EncryptedMessage
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.crypto.RSAUtils.getRsaTransformationIdx
import com.onemoresecret.databinding.FragmentKeyStoreListBinding
import com.onemoresecret.databinding.FragmentPasswordGeneratorBinding
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.util.Arrays
import java.util.Locale
import java.util.function.Consumer
import java.util.stream.Collectors
import androidx.core.content.edit

class PasswordGeneratorFragment : Fragment() {
    private var binding: FragmentPasswordGeneratorBinding? = null
    private val menuProvider = PwdMenuProvider()
    private var preferences: SharedPreferences? = null
    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var outputFragment: OutputFragment? = null
    private val cryptographyManager = CryptographyManager()
    private var onSharedPreferenceChangeListener: OnSharedPreferenceChangeListener? = null

    private var encryptPwd: Consumer<String>? = null
    private var setPwd: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPasswordGeneratorBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider)

        keyStoreListFragment = binding!!.fragmentContainerView.getFragment<KeyStoreListFragment>()
        outputFragment = binding!!.fragmentContainerView4.getFragment<OutputFragment>()

        val preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        binding!!.chipUpperCase.isChecked =
            preferences.getBoolean(PROP_UCASE, true)
        binding!!.chipUpperCase.setOnCheckedChangeListener { e: CompoundButton?, b: Boolean ->
            onPropertyChange(
                PROP_UCASE,
                b
            )
        }

        binding!!.chipLowerCase.isChecked =
            preferences.getBoolean(PROP_LCASE, true)
        binding!!.chipLowerCase.setOnCheckedChangeListener { e: CompoundButton?, b: Boolean ->
            onPropertyChange(
                PROP_LCASE,
                b
            )
        }

        binding!!.chipDigits.isChecked =
            preferences.getBoolean(PROP_DIGITS, true)
        binding!!.chipDigits.setOnCheckedChangeListener { e: CompoundButton?, b: Boolean ->
            onPropertyChange(
                PROP_DIGITS,
                b
            )
        }

        binding!!.chipSpecials.isChecked =
            preferences.getBoolean(PROP_SPECIALS, true)
        binding!!.chipSpecials.setOnCheckedChangeListener { e: CompoundButton?, b: Boolean ->
            onPropertyChange(
                PROP_SPECIALS,
                b
            )
        }

        binding!!.chipSimilar.isChecked =
            preferences.getBoolean(PROP_SIMILAR, true)
        binding!!.chipSimilar.setOnCheckedChangeListener { e: CompoundButton?, b: Boolean ->
            onPropertyChange(
                PROP_SIMILAR,
                b
            )
        }

        binding!!.chipLayout.isChecked =
            preferences.getBoolean(PROP_LAYOUT, true)
        binding!!.chipLayout.setOnCheckedChangeListener { e: CompoundButton?, b: Boolean ->
            onPropertyChange(
                PROP_LAYOUT,
                b
            )
        }

        binding!!.chipPwdLength.text =
            preferences.getInt(PROP_PWD_LENGTH, PWD_LEN_DEFAULT).toString()
        binding!!.chipPwdLength.setOnClickListener { e: View? -> changePwdLen() }

        binding!!.chipOccurs.text = preferences.getInt(PROP_OCCURS, OCCURS_DEFAULT).toString()
        binding!!.chipOccurs.setOnClickListener { e: View? -> changeOccurs() }

        onSharedPreferenceChangeListener =
            OnSharedPreferenceChangeListener { sharedPreferences: SharedPreferences?, key: String? ->
                if (PROP_PWD_LENGTH == key) {
                    requireActivity()
                        .mainExecutor
                        .execute {
                            binding!!.chipPwdLength.text =
                                preferences.getInt(key, PWD_LEN_DEFAULT)
                                    .toString()
                        }
                } else if (PROP_OCCURS == key) {
                    requireActivity()
                        .mainExecutor
                        .execute {
                            binding!!.chipOccurs.text =
                                preferences.getInt(key, OCCURS_DEFAULT)
                                    .toString()
                        }
                }
            }

        preferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)

        keyStoreListFragment!!.setRunOnStart { fragmentKeyStoreListBinding: FragmentKeyStoreListBinding? ->
            keyStoreListFragment!!
                .selectionTracker
                .addObserver(object : SelectionTracker.SelectionObserver<String?>() {
                    override fun onSelectionChanged() {
                        super.onSelectionChanged()
                        if (keyStoreListFragment!!.selectionTracker.hasSelection()) {
                            val selectedAlias = keyStoreListFragment!!
                                .selectionTracker
                                .selection
                                .iterator()
                                .next()!!
                            encryptPwd!!.accept(selectedAlias)
                        } else {
                            setPwd!!.run()
                        }
                    }
                })
        }

        binding!!.editTextPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                setPwd = getSetPwd(s.toString())
                setPwd!!.run()
                encryptPwd = getEncryptPwd(s.toString())
            }
        })

        requireActivity().mainExecutor.execute { this.newPassword() }
    }

    private fun changePwdLen() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Password length")
        val numberPicker = NumberPicker(requireContext())
        numberPicker.minValue = PWD_LEN_MIN
        numberPicker.maxValue = PWD_LEN_MAX
        numberPicker.value = preferences!!.getInt(PROP_PWD_LENGTH, PWD_LEN_DEFAULT)
        builder.setView(numberPicker)
        builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int ->
            preferences!!.edit() {
                putInt(PROP_PWD_LENGTH, numberPicker.value)
            }
            newPassword()
        }
        builder.setNegativeButton(
            android.R.string.cancel,
            (DialogInterface.OnClickListener { dialog: DialogInterface, which: Int -> dialog.cancel() })
        )
        builder.show()
    }

    private fun changeOccurs() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Occurrence")
        builder.setMessage("…of every character class (at least)")
        val numberPicker = NumberPicker(requireContext())
        numberPicker.minValue = OCCURS_MIN
        numberPicker.maxValue = OCCURS_MAX
        numberPicker.value = preferences!!.getInt(PROP_OCCURS, OCCURS_DEFAULT)
        builder.setView(numberPicker)
        builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int ->
            preferences!!.edit()
                .putInt(PROP_OCCURS, numberPicker.value).apply()
            newPassword()
        }
        builder.setNegativeButton(
            android.R.string.cancel,
            (DialogInterface.OnClickListener { dialog: DialogInterface, which: Int -> dialog.cancel() })
        )
        builder.show()
    }

    private fun changeCharList(propertyName: String) {
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
                defaultList = DEFAULT_LCASE.uppercase(Locale.getDefault())
                title = getString(R.string.upper_case)
            }

            else -> throw IllegalArgumentException()
        }
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(title)
        val editText = EditText(requireContext())
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        editText.setText(preferences!!.getString(propertyName, defaultList))
        builder.setView(editText)
        builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int ->
            preferences!!.edit().putString(propertyName, editText.text.toString()).apply()
            newPassword()
        }
        builder.setNegativeButton(
            android.R.string.cancel
        ) { dialog: DialogInterface, which: Int -> dialog.cancel() }
        builder.show()
    }

    private fun newPassword() {
        check(!keyStoreListFragment!!.selectionTracker.hasSelection()) { "Generating new encrypted password" }

        var charClasses: MutableList<String?> = ArrayList()

        val length = preferences!!.getInt(PROP_PWD_LENGTH, PWD_LEN_DEFAULT)

        if (binding!!.chipUpperCase.isChecked) {
            charClasses.add(
                preferences!!.getString(
                    PROP_UCASE_LIST,
                    DEFAULT_LCASE.uppercase(Locale.getDefault())
                )
            )
        }
        if (binding!!.chipLowerCase.isChecked) {
            charClasses.add(preferences!!.getString(PROP_LCASE_LIST, DEFAULT_LCASE))
        }
        if (binding!!.chipDigits.isChecked) {
            charClasses.add(preferences!!.getString(PROP_DIGITS_LIST, DEFAULT_DIGITS))
        }
        if (binding!!.chipSpecials.isChecked) {
            charClasses.add(preferences!!.getString(PROP_SPECIALS_LIST, DEFAULT_SPECIALS))
        }

        val size = charClasses.size

        if (!binding!!.chipSimilar.isChecked) {
            val blacklist = preferences!!.getString(
                PROP_SIMILAR_LIST,
                DEFAULT_SIMILAR
            )!!.toCharArray()
            Arrays.sort(blacklist)

            for (i in 0..<size) {
                //deactivating "similar" will remove all similar characters
                val sb = StringBuilder()
                val cArr = charClasses.removeAt(0)!!.toCharArray()
                for (c in cArr) {
                    if (Arrays.binarySearch(blacklist, c) < 0) {
                        sb.append(c)
                    }
                }
                charClasses.add(sb.toString())
            }
        }

        if (binding!!.chipLayout.isChecked) {
            //make sure password can be printed with the selected layout
            val keyboardLayout = outputFragment!!.selectedLayout
/*
            if (keyboardLayout == null) {
                Toast.makeText(
                    requireContext(),
                    R.string.cannot_apply_layout_filter,
                    Toast.LENGTH_LONG
                ).show()
                return
            }
*/
            for (i in 0..<size) {
                val sb = StringBuilder()
                val cArr = charClasses.removeAt(0)!!.toCharArray()
                for (c in cArr) {
                    if (keyboardLayout.forKey(c) != null) {
                        //can type this character with this keyboard layout
                        sb.append(c)
                    }
                }

                charClasses.add(sb.toString())
            }
        }

        //remove duplicates
        for (i in 0..<size) {
            val s = charClasses.removeAt(0)
            charClasses.add(s!!.replace("(.)\\1+".toRegex(), "$1"))
        }

        //remove empty classes
        charClasses = charClasses.stream().filter { s: String? -> !s!!.isEmpty() }
            .collect(Collectors.toList())

        if (charClasses.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_symbols_available, Toast.LENGTH_LONG)
                .show()
            return
        }

        //ensure minimal occurrence
        val list = ArrayList<String?>()
        for (i in 0..<preferences!!.getInt(PROP_OCCURS, OCCURS_DEFAULT)) {
            list.addAll(charClasses)
        }

        val rnd = SecureRandom()

        while (list.size < length) {
            list.add(charClasses[rnd.nextInt(charClasses.size)])
        }

        val sb = StringBuilder()

        for (i in 0..<length) {
            val s = list.removeAt(rnd.nextInt(list.size))
            val cArr = s!!.toCharArray()
            sb.append(cArr[rnd.nextInt(cArr.size)])
        }

        val pwd = sb.toString()

        binding!!.editTextPassword.setText(pwd)
    }

    private fun getSetPwd(pwd: String): Runnable {
        return Runnable {
            binding!!.editTextPassword.isEnabled = true
            outputFragment!!.setMessage(pwd, getString(R.string.unprotected_password))
            switchControls(true)
        }
    }

    private fun getEncryptPwd(pwd: String): Consumer<String> {
        return Consumer { alias: String? ->
            try {
                val encrypted = encodeAsOmsText(
                    EncryptedMessage(
                        pwd.toByteArray(StandardCharsets.UTF_8),
                        (cryptographyManager.getCertificate(alias).publicKey as RSAPublicKey),
                        getRsaTransformationIdx(preferences!!),
                        getKeyLength(preferences!!),
                        getAesTransformationIdx(preferences!!)
                    ).message
                )

                binding!!.editTextPassword.isEnabled = false
                outputFragment!!.setMessage(encrypted, getString(R.string.encrypted_password))

                //disable controls that can trigger password generator. Otherwise, the user can accidentally
                //trigger password change.
                switchControls(false)
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    private fun switchControls(enabled: Boolean) {
        binding!!.chipPwdLength.isEnabled = enabled
        binding!!.chipLayout.isEnabled = enabled
        binding!!.chipSimilar.isEnabled = enabled
        binding!!.chipDigits.isEnabled = enabled
        binding!!.chipSpecials.isEnabled = enabled
        binding!!.chipUpperCase.isEnabled = enabled
        binding!!.chipLowerCase.isEnabled = enabled
        binding!!.chipOccurs.isEnabled = enabled

        requireActivity().invalidateOptionsMenu()
    }

    private fun onPropertyChange(propertyName: String, value: Boolean) {
        preferences!!.edit() { putBoolean(propertyName, value) }
        newPassword()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
        preferences!!.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
        binding = null
    }

    private inner class PwdMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_pwd_generator, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
            menu.findItem(R.id.menuItemGenPwd)
                .setVisible(!keyStoreListFragment!!.selectionTracker.hasSelection())
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemGenPwd) {
                newPassword()
            } else if (menuItem.itemId == R.id.menuItemUcase) {
                changeCharList(PROP_UCASE_LIST)
            } else if (menuItem.itemId == R.id.menuItemLcase) {
                changeCharList(PROP_LCASE_LIST)
            } else if (menuItem.itemId == R.id.menuItemDigits) {
                changeCharList(PROP_DIGITS_LIST)
            } else if (menuItem.itemId == R.id.menuItemSpecials) {
                changeCharList(PROP_SPECIALS_LIST)
            } else if (menuItem.itemId == R.id.menuItemSimilar) {
                changeCharList(PROP_SIMILAR_LIST)
            } else if (menuItem.itemId == R.id.menuItemPwGenHelp) {
                openUrl(R.string.pwd_generator_md_url, requireContext())
            } else {
                return false
            }

            return true
        }
    }

    companion object {
        private const val PROP_UCASE = "pwgen_ucase"
        private const val PROP_UCASE_LIST = "pwgen_ucase_list"
        private const val PROP_LCASE = "pwgen_lcase"
        private const val PROP_LCASE_LIST = "pwgen_lcase_list"
        private const val PROP_DIGITS = "pwgen_digits"
        private const val PROP_DIGITS_LIST = "pwgen_digits_list"
        private const val PROP_SPECIALS = "pwgen_specials"
        private const val PROP_SPECIALS_LIST = "pwgen_specials_list"
        private const val PROP_SIMILAR = "pwgen_similar"
        private const val PROP_SIMILAR_LIST = "pwgen_similar_LIST"
        private const val PROP_LAYOUT = "pwgen_layout"
        private const val PROP_PWD_LENGTH = "pwgen_length"
        private const val PROP_OCCURS = "pwgen_occurrs"
        private const val DEFAULT_DIGITS = "0123456789"
        private const val DEFAULT_LCASE = "abcdefghijklmnopqrstuvwxyz"
        private const val DEFAULT_SPECIALS = "!#$%&'*+,-.:;<=>?@_~"
        private const val DEFAULT_SIMILAR = "01IOl|"
        private const val PWD_LEN_DEFAULT = 10
        private const val PWD_LEN_MIN = 5
        private const val PWD_LEN_MAX = 50
        private const val OCCURS_DEFAULT = 1
        private const val OCCURS_MIN = 1
        private const val OCCURS_MAX = 10
    }
}