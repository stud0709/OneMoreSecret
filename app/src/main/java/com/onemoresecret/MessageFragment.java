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
import com.onemoresecret.msg_fragment_plugins.MsgPluginCryptoCurrencyAddress;
import com.onemoresecret.msg_fragment_plugins.MsgPluginEncryptedFile;
import com.onemoresecret.msg_fragment_plugins.MsgPluginEncryptedMessage;
import com.onemoresecret.msg_fragment_plugins.MsgPluginKeyRequest;
import com.onemoresecret.msg_fragment_plugins.MsgPluginTotp;

import java.util.Objects;

import com.onemoresecret.databinding.FragmentMessageBinding;
import com.onemoresecret.msg_fragment_plugins.MsgPluginWiFiPairing;

public class MessageFragment extends Fragment {
    private static final String TAG = MessageFragment.class.getSimpleName();
    private com.onemoresecret.databinding.FragmentMessageBinding binding;
    private final MutableLiveData<Boolean> hiddenState = new MutableLiveData<>(true);
    private final MessageMenuProvider menuProvider = new MessageMenuProvider();
    private volatile boolean navBackIfPaused = true;
    private MessageFragmentPlugin<?> messageFragmentPlugin;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentMessageBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        var navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() != R.id.MessageFragment) {
            Log.d(TAG, String.format("Already navigating to %s", navController.getCurrentDestination()));
            return;
        }
        Log.d(TAG, "onPause: going backward");
        if (navBackIfPaused) Util.discardBackStack(this);
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
        try {
            if (requireArguments().containsKey(QRFragment.ARG_MESSAGE)) {
                onMessage();
            } else if (requireArguments().containsKey(QRFragment.ARG_URI)) {
                onUri();
            }

            messageFragmentPlugin.getOutputView().setBeforePause(() -> navBackIfPaused = false /* disarm backward navigation */);

            //insert message and output view into fragment
            this.getChildFragmentManager()
                    .beginTransaction()
                    .add(R.id.fragmentMessageView, messageFragmentPlugin.getMessageView())
                    .add(R.id.fragmentOutputView, messageFragmentPlugin.getOutputView())
                    .commit();

            //request authentication

            messageFragmentPlugin.showBiometricPromptForDecryption();
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(getContext(), Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()), Toast.LENGTH_LONG).show();
            Util.discardBackStack(this);
        }
    }

    private void onUri() throws Exception {
        var uri = (Uri) getArguments().getParcelable("URI");

        messageFragmentPlugin = new MsgPluginEncryptedFile(this,
                uri,
                getArguments().getString(QRFragment.ARG_FILENAME),
                getArguments().getInt(QRFragment.ARG_FILESIZE));
    }

    private void onMessage() throws Exception {
        byte[] messageData = requireArguments().getByteArray(QRFragment.ARG_MESSAGE);
        int applicationId = requireArguments().getInt(QRFragment.ARG_APPLICATION_ID);

        switch (applicationId) {
            case MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED,
                    MessageComposer.APPLICATION_ENCRYPTED_MESSAGE ->
                    messageFragmentPlugin = new MsgPluginEncryptedMessage(this, messageData);

            case MessageComposer.APPLICATION_KEY_REQUEST ->
                    messageFragmentPlugin = new MsgPluginKeyRequest(this, messageData);

            case MessageComposer.APPLICATION_TOTP_URI_DEPRECATED,
                    MessageComposer.APPLICATION_TOTP_URI ->
                    messageFragmentPlugin = new MsgPluginTotp(this, messageData);

            case MessageComposer.APPLICATION_BITCOIN_ADDRESS ->
                    messageFragmentPlugin = new MsgPluginCryptoCurrencyAddress(this, messageData);

            case MessageComposer.APPLICATION_WIFI_PAIRING ->
                    messageFragmentPlugin = new MsgPluginWiFiPairing(this, messageData);
            default ->
                    throw new IllegalArgumentException(getString(R.string.wrong_application) + " " + applicationId);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        new Thread(() -> ((MainActivity) requireActivity()).sendReplyViaSocket(new byte[]{}, true)).start();
        messageFragmentPlugin.onDestroyView();
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
            //visibility switch will only be shown if there are active observers
            menu.findItem(R.id.menuItemMsgVisibility).setVisible(hiddenState.hasActiveObservers());
            menu.findItem(R.id.menuItemMsgVisibility).setIcon(hiddenState.getValue() ? R.drawable.baseline_visibility_24 : R.drawable.baseline_visibility_off_24);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemMsgVisibility) {
                hiddenState.setValue(!hiddenState.getValue());
                requireActivity().invalidateOptionsMenu();
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