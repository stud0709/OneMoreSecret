package com.onemoresecret;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.navigation.fragment.NavHostFragment;

import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.msg_fragment_plugins.MessageFragmentPlugin;
import com.onemoresecret.msg_fragment_plugins.MsgPluginEncryptedFile;
import com.onemoresecret.msg_fragment_plugins.MsgPluginEncryptedMessage;
import com.onemoresecret.msg_fragment_plugins.MsgPluginKeyRequest;
import com.onemoresecret.msg_fragment_plugins.MsgPluginTotp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStoreException;
import java.util.Objects;

import com.onemoresecret.databinding.FragmentMessageBinding;

public class MessageFragment extends Fragment {
    private static final String TAG = MessageFragment.class.getSimpleName();
    private com.onemoresecret.databinding.FragmentMessageBinding binding;
    private MutableLiveData<Boolean> hiddenState = new MutableLiveData<>(true);
    private final MessageMenuProvider menuProvider = new MessageMenuProvider();
    private volatile boolean navBackIfPaused = true;
    private OutputFragment outputFragment;
    private MessageFragmentPlugin messageFragmentPlugin;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMessageBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        var navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination().getId() != R.id.MessageFragment) {
            Log.d(TAG, String.format("Already navigating to %s", navController.getCurrentDestination()));
            return;
        }
        if (navBackIfPaused) NavHostFragment.findNavController(this).popBackStack();
    }

    @Override
    public void onResume() {
        super.onResume();

        //rearm
        navBackIfPaused = true;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);

        outputFragment = ((OutputFragment) binding.messageOutputFragment.getFragment());
        outputFragment.setBeforePause(() -> navBackIfPaused = false /* disarm backward navigation */);

        if (requireArguments().containsKey(QRFragment.ARG_MESSAGE)) {
            onMessage();
        } else if (requireArguments().containsKey(QRFragment.ARG_URI)) {
            onUri();
        }

        //insert message view into fragment
        this.getChildFragmentManager().beginTransaction().add(R.id.fragmentMessageView, messageFragmentPlugin.getMessageView()).commit();

        //request authentication
        try {
            messageFragmentPlugin.showBiometricPromptForDecryption();
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(getContext(), Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()), Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    private void onUri() {
        var uri = (Uri) getArguments().getParcelable("URI");
        try {
            messageFragmentPlugin = new MsgPluginEncryptedFile(this,
                    outputFragment,
                    binding,
                    uri,
                    getArguments().getString(QRFragment.ARG_FILENAME),
                    getArguments().getInt(QRFragment.ARG_FILESIZE));

        } catch (IOException ex) {
            ex.printStackTrace();
            Toast.makeText(getContext(), Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()), Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    private void onMessage() {
        byte[] messageData = requireArguments().getByteArray(QRFragment.ARG_MESSAGE);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(requireArguments().getByteArray(QRFragment.ARG_MESSAGE));
             var dataInputStream = new OmsDataInputStream(bais)) {

            //(1) Application ID
            var applicationId = dataInputStream.readUnsignedShort();

            switch (applicationId) {
                case MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_TRANSFER -> {
                    messageFragmentPlugin = new MsgPluginEncryptedMessage(this, outputFragment, binding, messageData);
                }
                case MessageComposer.APPLICATION_KEY_REQUEST -> {
                    messageFragmentPlugin = new MsgPluginKeyRequest(this, outputFragment, binding, messageData);
                }
                case MessageComposer.APPLICATION_TOTP_URI_TRANSFER -> {
                    messageFragmentPlugin = new MsgPluginTotp(this, outputFragment, binding, messageData);
                }
                default ->
                        throw new IllegalArgumentException(getString(R.string.wrong_application) + " " + applicationId);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            Toast.makeText(getContext(), Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()), Toast.LENGTH_LONG).show();
            NavHostFragment.findNavController(this).popBackStack();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        binding = null;
    }

    private class MessageMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_message, menu);
        }

        @Override
        public void onPrepareMenu(@NonNull Menu menu) {
            MenuProvider.super.onPrepareMenu(menu);
            menu.findItem(R.id.menuItemMsgVisibility).setVisible(hiddenState.hasActiveObservers());
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemMsgVisibility) {
                hiddenState.setValue(!hiddenState.getValue());
                menuItem.setIcon(hiddenState.getValue() ? R.drawable.baseline_visibility_24 : R.drawable.baseline_visibility_off_24);
            } else if (menuItem.getItemId() == R.id.menuItemMsgHelp) {
                Util.openUrl(R.string.decrypted_message_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }
    }

    public MutableLiveData<Boolean> getHiddenState() {
        return hiddenState;
    }
}