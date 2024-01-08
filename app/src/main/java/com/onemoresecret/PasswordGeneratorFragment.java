package com.onemoresecret;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.SelectionTracker;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.EncryptedMessageTransfer;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.databinding.FragmentPasswordGeneratorBinding;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PasswordGeneratorFragment extends Fragment {
    private FragmentPasswordGeneratorBinding binding;
    private final PwdMenuProvider menuProvider = new PwdMenuProvider();
    private SharedPreferences preferences;
    private KeyStoreListFragment keyStoreListFragment;
    private OutputFragment outputFragment;
    private final CryptographyManager cryptographyManager = new CryptographyManager();
    private final AtomicBoolean textChangeListenerActive = new AtomicBoolean(true);
    private Runnable runOnce = () -> {
        newPassword();
        runOnce = null;
    };
    private static final String
            PROP_UCASE = "pwgen_ucase",
            PROP_UCASE_LIST = "pwgen_ucase_list",
            PROP_LCASE = "pwgen_lcase",
            PROP_LCASE_LIST = "pwgen_lcase_list",
            PROP_DIGITS = "pwgen_digits",
            PROP_DIGITS_LIST = "pwgen_digits_list",
            PROP_SPECIALS = "pwgen_specials",
            PROP_SPECIALS_LIST = "pwgen_specials_list",
            PROP_SIMILAR = "pwgen_similar",
            PROP_SIMILAR_LIST = "pwgen_similar_LIST",
            PROP_LAYOUT = "pwgen_layout",
            PROP_PWD_LENGTH = "pwgen_length",
            PROP_OCCURS = "pwgen_occurrs",
            DEFAULT_DIGITS = "0123456789",
            DEFAULT_LCASE = "abcdefghijklmnopqrstuvwxyz",
            DEFAULT_SPECIALS = "!#$%&'*+,-.:;<=>?@_~",
            DEFAULT_SIMILAR = "01IOl|";
    private static final int PWD_LEN_DEFAULT = 10,
            PWD_LEN_MIN = 5,
            PWD_LEN_MAX = 50,
            OCCURS_DEFAULT = 1,
            OCCURS_MIN = 1,
            OCCURS_MAX = 10;

    private SharedPreferences.OnSharedPreferenceChangeListener onSharedPreferenceChangeListener;

    private Consumer<String> encryptPwd;
    private Runnable setPwd;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPasswordGeneratorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);

        keyStoreListFragment = binding.fragmentContainerView.getFragment();
        outputFragment = binding.fragmentContainerView4.getFragment();

        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        binding.chipUpperCase.setChecked(preferences.getBoolean(PROP_UCASE, true));
        binding.chipUpperCase.setOnCheckedChangeListener((e, b) -> onPropertyChange(PROP_UCASE, b));

        binding.chipLowerCase.setChecked(preferences.getBoolean(PROP_LCASE, true));
        binding.chipLowerCase.setOnCheckedChangeListener((e, b) -> onPropertyChange(PROP_LCASE, b));

        binding.chipDigits.setChecked(preferences.getBoolean(PROP_DIGITS, true));
        binding.chipDigits.setOnCheckedChangeListener((e, b) -> onPropertyChange(PROP_DIGITS, b));

        binding.chipSpecials.setChecked(preferences.getBoolean(PROP_SPECIALS, true));
        binding.chipSpecials.setOnCheckedChangeListener((e, b) -> onPropertyChange(PROP_SPECIALS, b));

        binding.chipSimilar.setChecked(preferences.getBoolean(PROP_SIMILAR, true));
        binding.chipSimilar.setOnCheckedChangeListener((e, b) -> onPropertyChange(PROP_SIMILAR, b));

        binding.chipLayout.setChecked(preferences.getBoolean(PROP_LAYOUT, true));
        binding.chipLayout.setOnCheckedChangeListener((e, b) -> onPropertyChange(PROP_LAYOUT, b));

        binding.chipPwdLength.setText(Integer.toString(preferences.getInt(PROP_PWD_LENGTH, PWD_LEN_DEFAULT)));
        binding.chipPwdLength.setOnClickListener(e -> changePwdLen());

        binding.chipOccurs.setText(Integer.toString(preferences.getInt(PROP_OCCURS, OCCURS_DEFAULT)));
        binding.chipOccurs.setOnClickListener(e -> changeOccurs());

        onSharedPreferenceChangeListener = (sharedPreferences, key) -> {
            if (key.equals(PROP_PWD_LENGTH)) {
                requireActivity()
                        .getMainExecutor()
                        .execute(() -> binding.chipPwdLength
                                .setText(Integer.toString(preferences.getInt(key, PWD_LEN_DEFAULT))));
            } else if (key.equals(PROP_OCCURS)) {
                requireActivity()
                        .getMainExecutor()
                        .execute(() -> binding.chipOccurs
                                .setText(Integer.toString(preferences.getInt(key, OCCURS_DEFAULT))));
            }
        };

        preferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);

        setPwd = getSetPwd("", true);
        encryptPwd = getEncryptPwd("");

        keyStoreListFragment.setRunOnStart(
                fragmentKeyStoreListBinding -> keyStoreListFragment
                        .getSelectionTracker()
                        .addObserver(new SelectionTracker.SelectionObserver<String>() {
                            @Override
                            public void onSelectionChanged() {
                                super.onSelectionChanged();
                                if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                                    var selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
                                    encryptPwd.accept(selectedAlias);
                                } else {
                                    setPwd.run();
                                }
                            }
                        }));

        binding.editTextPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!textChangeListenerActive.get()) return;

                setPwd = getSetPwd(s.toString(), false);
                setPwd.run();
                encryptPwd = getEncryptPwd(s.toString());
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (runOnce != null) runOnce.run();
    }

    private void changePwdLen() {
        var builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Password length");
        var numberPicker = new NumberPicker(requireContext());
        numberPicker.setMinValue(PWD_LEN_MIN);
        numberPicker.setMaxValue(PWD_LEN_MAX);
        numberPicker.setValue(preferences.getInt(PROP_PWD_LENGTH, PWD_LEN_DEFAULT));
        builder.setView(numberPicker);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            preferences.edit().putInt(PROP_PWD_LENGTH, numberPicker.getValue()).apply();
            newPassword();
        });
        builder.setNegativeButton(android.R.string.cancel, ((dialog, which) -> dialog.cancel()));
        builder.show();
    }

    private void changeOccurs() {
        var builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Occurrence");
        builder.setMessage("\u2026of every character class (at least)");
        var numberPicker = new NumberPicker(requireContext());
        numberPicker.setMinValue(OCCURS_MIN);
        numberPicker.setMaxValue(OCCURS_MAX);
        numberPicker.setValue(preferences.getInt(PROP_OCCURS, OCCURS_DEFAULT));
        builder.setView(numberPicker);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            preferences.edit().putInt(PROP_OCCURS, numberPicker.getValue()).apply();
            newPassword();
        });
        builder.setNegativeButton(android.R.string.cancel, ((dialog, which) -> dialog.cancel()));
        builder.show();
    }

    private void changeCharList(String propertyName) {
        String defaultList, title;
        switch (propertyName) {
            case PROP_DIGITS_LIST -> {
                defaultList = DEFAULT_DIGITS;
                title = getString(R.string.digits);
            }
            case PROP_LCASE_LIST -> {
                defaultList = DEFAULT_LCASE;
                title = getString(R.string.lower_case);
            }
            case PROP_SIMILAR_LIST -> {
                defaultList = DEFAULT_SIMILAR;
                title = getString(R.string.similar);
            }
            case PROP_SPECIALS_LIST -> {
                defaultList = DEFAULT_SPECIALS;
                title = getString(R.string.specials);
            }
            case PROP_UCASE_LIST -> {
                defaultList = DEFAULT_LCASE.toUpperCase();
                title = getString(R.string.upper_case);
            }
            default -> throw new IllegalArgumentException();
        }
        var builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(title);
        var editText = new EditText(requireContext());
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setText(preferences.getString(propertyName, defaultList));
        builder.setView(editText);
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            preferences.edit().putString(propertyName, editText.getText().toString()).apply();
            newPassword();
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void newPassword() {
        if (keyStoreListFragment.getSelectionTracker().hasSelection())
            throw new IllegalStateException("Generating new encrypted password");

        List<String> charClasses = new ArrayList<>();

        var length = preferences.getInt(PROP_PWD_LENGTH, PWD_LEN_DEFAULT);

        if (binding.chipUpperCase.isChecked()) {
            charClasses.add(preferences.getString(PROP_UCASE_LIST, DEFAULT_LCASE.toUpperCase()));
        }
        if (binding.chipLowerCase.isChecked()) {
            charClasses.add(preferences.getString(PROP_LCASE_LIST, DEFAULT_LCASE));
        }
        if (binding.chipDigits.isChecked()) {
            charClasses.add(preferences.getString(PROP_DIGITS_LIST, DEFAULT_DIGITS));
        }
        if (binding.chipSpecials.isChecked()) {
            charClasses.add(preferences.getString(PROP_SPECIALS_LIST, DEFAULT_SPECIALS));
        }

        var size = charClasses.size();

        if (!binding.chipSimilar.isChecked()) {
            var blacklist = preferences.getString(PROP_SIMILAR_LIST, DEFAULT_SIMILAR).toCharArray();
            Arrays.sort(blacklist);

            for (int i = 0; i < size; i++) {
                //deactivating "similar" will remove all similar characters
                var sb = new StringBuilder();
                var cArr = charClasses.remove(0).toCharArray();
                for (var c : cArr) {
                    if (Arrays.binarySearch(blacklist, c) < 0) {
                        sb.append(c);
                    }
                }
                charClasses.add(sb.toString());
            }
        }

        if (binding.chipLayout.isChecked()) {
            //make sure password can be printed with the selected layout
            var keyboardLayout = outputFragment.getSelectedLayout();

            if (keyboardLayout == null) {
                Toast.makeText(requireContext(), R.string.cannot_apply_layout_filter, Toast.LENGTH_LONG).show();
                return;
            }

            for (var i = 0; i < size; i++) {

                var sb = new StringBuilder();
                var cArr = charClasses.remove(0).toCharArray();
                for (var c : cArr) {
                    if (keyboardLayout.forKey(c) != null) {
                        //can type this character with this keyboard layout
                        sb.append(c);
                    }
                }

                charClasses.add(sb.toString());
            }
        }

        //remove duplicates
        for (var i = 0; i < size; i++) {
            var s = charClasses.remove(0);
            charClasses.add(s.replaceAll("(.)\\1+", "$1"));
        }

        //remove empty classes
        charClasses = charClasses.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());

        if (charClasses.isEmpty()) {
            Toast.makeText(requireContext(), R.string.no_symbols_available, Toast.LENGTH_LONG).show();
            return;
        }

        //ensure minimal occurrence
        var list = new ArrayList<String>();
        for (var i = 0; i < preferences.getInt(PROP_OCCURS, OCCURS_DEFAULT); i++) {
            list.addAll(charClasses);
        }

        var rnd = new SecureRandom();

        while (list.size() < length) {
            list.add(charClasses.get(rnd.nextInt(charClasses.size())));
        }

        var sb = new StringBuilder();

        for (var i = 0; i < length; i++) {
            var s = list.remove(rnd.nextInt(list.size()));
            var cArr = s.toCharArray();
            sb.append(cArr[rnd.nextInt(cArr.length)]);
        }

        var pwd = sb.toString();

        encryptPwd = getEncryptPwd(pwd);
        setPwd = getSetPwd(pwd, true);

        setPwd.run();
    }

    private Runnable getSetPwd(String pwd, boolean updateEditTextPassword) {
        return () -> {
            if(updateEditTextPassword) {
                textChangeListenerActive.set(false);
                binding.editTextPassword.setText(pwd);
                textChangeListenerActive.set(true);
                binding.editTextPassword.setEnabled(true);
            }
            outputFragment.setMessage(pwd, getString(R.string.unprotected_password));
            switchControls(true);
        };
    }

    private Consumer<String> getEncryptPwd(String pwd) {
        return alias -> {
            try {
                var encrypted = MessageComposer.encodeAsOmsText(
                        new EncryptedMessageTransfer(pwd.getBytes(StandardCharsets.UTF_8),
                                (RSAPublicKey) cryptographyManager.getCertificate(alias).getPublicKey(),
                                RSAUtils.getRsaTransformationIdx(preferences),
                                AESUtil.getKeyLength(preferences),
                                AESUtil.getAesTransformationIdx(preferences)).getMessage());

                textChangeListenerActive.set(false);
                binding.editTextPassword.setText(
                        encrypted);
                textChangeListenerActive.set(true);

                binding.editTextPassword.setEnabled(false);
                outputFragment.setMessage(encrypted, getString(R.string.encrypted_password));

                //disable controls that can trigger password generator. Otherwise, the user can accidentally
                //trigger password change.
                switchControls(false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    private void switchControls(boolean enabled) {
        binding.chipPwdLength.setEnabled(enabled);
        binding.chipLayout.setEnabled(enabled);
        binding.chipSimilar.setEnabled(enabled);
        binding.chipDigits.setEnabled(enabled);
        binding.chipSpecials.setEnabled(enabled);
        binding.chipUpperCase.setEnabled(enabled);
        binding.chipLowerCase.setEnabled(enabled);
        binding.chipOccurs.setEnabled(enabled);

        requireActivity().invalidateOptionsMenu();
    }

    private void onPropertyChange(String propertyName, boolean value) {
        preferences.edit().putBoolean(propertyName, value).apply();
        newPassword();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        preferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
        binding = null;
    }

    private class PwdMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_pwd_generator, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            MenuProvider.super.onPrepareMenu(menu);
            menu.findItem(R.id.menuItemGenPwd).setVisible(!keyStoreListFragment.getSelectionTracker().hasSelection());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemGenPwd) {
                newPassword();
            } else if (menuItem.getItemId() == R.id.menuItemUcase) {
                changeCharList(PROP_UCASE_LIST);
            } else if (menuItem.getItemId() == R.id.menuItemLcase) {
                changeCharList(PROP_LCASE_LIST);
            } else if (menuItem.getItemId() == R.id.menuItemDigits) {
                changeCharList(PROP_DIGITS_LIST);
            } else if (menuItem.getItemId() == R.id.menuItemSpecials) {
                changeCharList(PROP_SPECIALS_LIST);
            } else if (menuItem.getItemId() == R.id.menuItemSimilar) {
                changeCharList(PROP_SIMILAR_LIST);
            } else if (menuItem.getItemId() == R.id.menuItemPwGenHelp) {
                Util.openUrl(R.string.pwd_generator_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }
    }
}