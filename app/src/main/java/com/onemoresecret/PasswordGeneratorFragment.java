package com.onemoresecret;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.SelectionTracker;

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

import com.google.android.material.internal.TextWatcherAdapter;
import com.onemoresecret.bt.layout.KeyboardLayout;
import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.EncryptedMessageTransfer;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.databinding.FragmentPasswordGeneratorBinding;

import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class PasswordGeneratorFragment extends Fragment {
    private FragmentPasswordGeneratorBinding binding;
    private PwdMenuProvider menuProvider = new PwdMenuProvider();
    private SharedPreferences preferences;
    private KeyStoreListFragment keyStoreListFragment;
    private final CryptographyManager cryptographyManager = new CryptographyManager();
    private final AtomicBoolean textChangeListenerActive = new AtomicBoolean(true);
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
            DEFAULT_SIMILAR = "01IOl";
    private static final int PWD_LEN_DEFAULT = 10, PWD_LEN_MIN = 5, PWD_LEN_MAX = 50, OCCURS_DEFAUL = 1, OCCURS_MIN = 1, OCCURS_MAX = 10;

    private SharedPreferences.OnSharedPreferenceChangeListener onSharedPreferenceChangeListener;

    private Consumer<String> encryptPwd;
    private Runnable setPwd;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentPasswordGeneratorBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);

        keyStoreListFragment = (KeyStoreListFragment) binding.fragmentContainerView.getFragment();
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

        binding.chipOccurs.setText(Integer.toString(preferences.getInt(PROP_OCCURS, OCCURS_DEFAUL)));
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
                                .setText(Integer.toString(preferences.getInt(key, OCCURS_DEFAUL))));
            }
        };

        preferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);

        keyStoreListFragment.setRunOnStart(
                fragmentKeyStoreListBinding -> keyStoreListFragment
                        .getSelectionTracker()
                        .addObserver(new SelectionTracker.SelectionObserver<String>() {
                            @Override
                            public void onSelectionChanged() {
                                super.onSelectionChanged();
                                if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                                    onKeySelected();
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

                setPwd = getSetPwd(s.toString());
                encryptPwd = getEncryptPwd(s.toString());
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        newPassword();
    }

    private void onKeySelected() {
        String selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
        encryptPwd.accept(selectedAlias);
    }

    private void changePwdLen() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Password length");
        NumberPicker numberPicker = new NumberPicker(requireContext());
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
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle("Occurrence");
        builder.setMessage("\u2026of every character class (at least)");
        NumberPicker numberPicker = new NumberPicker(requireContext());
        numberPicker.setMinValue(OCCURS_MIN);
        numberPicker.setMaxValue(OCCURS_MAX);
        numberPicker.setValue(preferences.getInt(PROP_OCCURS, OCCURS_DEFAUL));
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
            case PROP_DIGITS_LIST:
                defaultList = DEFAULT_DIGITS;
                title = "Digits";
                break;
            case PROP_LCASE_LIST:
                defaultList = DEFAULT_LCASE;
                title = "Lower-Case Characters";
                break;
            case PROP_SIMILAR_LIST:
                defaultList = DEFAULT_SIMILAR;
                title = "Similar Symbols";
                break;
            case PROP_SPECIALS_LIST:
                defaultList = DEFAULT_SPECIALS;
                title = "Special Characters";
                break;
            case PROP_UCASE_LIST:
                defaultList = DEFAULT_LCASE.toUpperCase();
                title = "Uppper-Case Characters";
                break;
            default:
                throw new IllegalArgumentException();
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(title);
        EditText editText = new EditText(requireContext());
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

        int length = preferences.getInt(PROP_PWD_LENGTH, PWD_LEN_DEFAULT);

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

        int size = charClasses.size();

        if (!binding.chipSimilar.isChecked()) {
            char[] blacklist = preferences.getString(PROP_SIMILAR_LIST, "").toCharArray();
            Arrays.sort(blacklist);

            for (int i = 0; i < size; i++) {
                //deactivating "similar" will remove all similar characters
                StringBuilder sb = new StringBuilder();
                char[] cArr = charClasses.remove(0).toCharArray();
                for (char c : cArr) {
                    if (Arrays.binarySearch(blacklist, c) == -1) {
                        sb.append(c);
                    }
                }
                charClasses.add(sb.toString());
            }
        }

        if (binding.chipLayout.isChecked()) {
            //make sure password can be printed with the selected layout
            KeyboardLayout keyboardLayout =
                    ((KeyStoreListFragment) binding.fragmentContainerView.getFragment())
                            .getOutputFragment()
                            .getSelectedLayout();

            if (keyboardLayout == null) {
                Toast.makeText(requireContext(), "Cannot apply layout filter - no layout selected", Toast.LENGTH_LONG).show();
                return;
            }

            for (int i = 0; i < size; i++) {

                StringBuilder sb = new StringBuilder();
                char cArr[] = charClasses.remove(0).toCharArray();
                for (char c : cArr) {
                    if (keyboardLayout.forKey(c) != null) {
                        //can type this character with this keyboard layout
                        sb.append(c);
                    }
                }

                charClasses.add(sb.toString());
            }
        }

        //remove duplicates
        for (int i = 0; i < size; i++) {
            String s = charClasses.remove(0);
            charClasses.add(s.replaceAll("(.)\\1+", "$1"));
        }

        //remove empty classes
        charClasses = charClasses.stream().filter(s -> !s.isEmpty()).collect(Collectors.toList());

        if (charClasses.isEmpty()) {
            Toast.makeText(requireContext(), "No symbols available, check settings", Toast.LENGTH_LONG).show();
            return;
        }

        //ensure minimal occurrence
        List<String> list = new ArrayList<>();
        for (int i = 0; i < preferences.getInt(PROP_OCCURS, OCCURS_DEFAUL); i++) {
            for (String s : charClasses) {
                list.add(s);
            }
        }

        SecureRandom rnd = new SecureRandom();

        while (list.size() < length) {
            list.add(charClasses.get(rnd.nextInt(charClasses.size())));
        }

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < length; i++) {
            String s = list.remove(rnd.nextInt(list.size()));
            char cArr[] = s.toCharArray();
            sb.append(cArr[rnd.nextInt(cArr.length)]);
        }

        String pwd = sb.toString();

        encryptPwd = getEncryptPwd(pwd);
        setPwd = getSetPwd(pwd);

        setPwd.run();
    }

    private Runnable getSetPwd(String pwd) {
        return () -> {
            textChangeListenerActive.set(false);
            binding.editTextPassword.setText(pwd);
            textChangeListenerActive.set(true);

            binding.editTextPassword.setEnabled(true);
            keyStoreListFragment.getOutputFragment().setMessage(pwd, "Readable Password");
            switchControls(true);
        };
    }

    private Consumer<String> getEncryptPwd(String pwd) {
        return alias -> {
            try {
                String encrypted = MessageComposer.encodeAsOmsText(
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
                keyStoreListFragment.getOutputFragment().setMessage(encrypted, "Encrypted Password");

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

        getActivity().invalidateOptionsMenu();
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
            } else {
                return false;
            }

            return true;
        }
    }
}