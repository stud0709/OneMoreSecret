package com.onemoresecret;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.SelectionTracker;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.EncryptedMessageTransfer;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.OneTimePassword;
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.databinding.FragmentTotpImportBinding;

import java.security.interfaces.RSAPublicKey;
import java.util.Timer;
import java.util.TimerTask;

public class TOTPImportFragment extends Fragment {
    private static final String TAG = TOTPImportFragment.class.getSimpleName();
    private FragmentTotpImportBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private final CryptographyManager cryptographyManager = new CryptographyManager();
    private final OtpMenuProvider menuProvider = new OtpMenuProvider();
    private final Timer timer = new Timer();
    private long lastState = -1L;
    private SharedPreferences preferences;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentTotpImportBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);

        keyStoreListFragment = binding.fragmentContainerView.getFragment();
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);

        byte[] message = requireArguments().getByteArray("MESSAGE");

        OneTimePassword otp = new OneTimePassword(new String(message));

        try {
            if (!otp.looksValid()) {
                throw new IllegalArgumentException("Invalid scheme or authority");
            }

            String name = otp.getName();
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("missing name in URI");
            }

            String issuer = otp.getIssuer();
            if (issuer == null || issuer.isEmpty()) {
                binding.textViewNameIssuer.setText(name);
            } else {
                binding.textViewNameIssuer.setText(String.format("%s by %s", name, issuer));
            }

            String secret = otp.getSecret();

            if (secret == null || secret.isEmpty()) {
                throw new IllegalArgumentException("invalid secret key");
            }

            keyStoreListFragment.setRunOnStart(
                    fragmentKeyStoreListBinding -> keyStoreListFragment
                            .getSelectionTracker()
                            .addObserver(new SelectionTracker.SelectionObserver<>() {
                                @Override
                                public void onSelectionChanged() {
                                    super.onSelectionChanged();
                                    if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
                                        String selectedAlias = keyStoreListFragment.getSelectionTracker().getSelection().iterator().next();
                                        String encrypted = encrypt(selectedAlias, message);
                                        keyStoreListFragment.getOutputFragment().setMessage(encrypted, "Encrypted OTP Configuration");
                                    }
                                }
                            }));

            binding.textViewValue.setText("");

            timer.schedule(getTimerTask(otp), 1000);

        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(getContext(), getString(R.string.wrong_message_format), Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    private String encrypt(String alias, byte[] message) {
        try {
            return MessageComposer.encodeAsOmsText(
                    new EncryptedMessageTransfer(message,
                            (RSAPublicKey) cryptographyManager.getCertificate(alias).getPublicKey(),
                            RSAUtils.getRsaTransformationIdx(preferences),
                            AESUtil.getKeyLength(preferences),
                            AESUtil.getAesTransformationIdx(preferences)).getMessage());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private TimerTask getTimerTask(OneTimePassword otp) {
        return new TimerTask() {
            @Override
            public void run() {
                if (generateResponseCode(otp)) {
                    timer.schedule(getTimerTask(otp), 1000);
                }
            }
        };
    }

    private boolean generateResponseCode(OneTimePassword otp) {
        if (keyStoreListFragment.getSelectionTracker().hasSelection()) {
            //dummy output
            requireActivity().getMainExecutor().execute(() -> {
                binding.textViewRemaining.setText("");
                binding.textViewValue.setText(MessageComposer.OMS_PREFIX + "...");
                lastState = -1;
            });
            return true;
        }
        try {
            long[] state = otp.getState();
            String code = otp.generateResponseCode(state[0]);
            requireActivity().getMainExecutor().execute(() -> {
                binding.textViewRemaining.setText(String.format("...%ss", otp.getPeriod() - state[1]));
                binding.textViewValue.setText(code);
                if (lastState != state[0]) {
                    //new State = new code; update output fragment
                    keyStoreListFragment.getOutputFragment().setMessage(code, "One-Time Password");
                    lastState = state[0];
                }
            });
        } catch (Exception e) {
            Log.wtf(TAG, e);
            requireActivity().getMainExecutor().execute(() ->
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show());
            return false;
        }
        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timer.cancel();
    }

    private class OtpMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_otp_import, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            MenuProvider.super.onPrepareMenu(menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemPwGenHelp) {
                Util.openUrl(R.string.pwd_generator_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }
    }
}