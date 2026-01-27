package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.selection.SelectionTracker;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.crypto.EncryptedFile;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtil;
import com.onemoresecret.databinding.FragmentFileEncryptionBinding;

import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;

public class FileEncryptionFragment extends Fragment {
    private FragmentFileEncryptionBinding binding;
    private KeyStoreListFragment keyStoreListFragment;
    private final CryptographyManager cryptographyManager = new CryptographyManager();
    private SharedPreferences preferences;
    private static final String TAG = FileEncryptionFragment.class.getSimpleName();
    private Uri uri;
    private Util.UriFileInfo fileInfo;
    private boolean encryptionRunning = false;
    private int lastProgressPrc = -1;
    boolean navBackIfPaused = false;

    private final FileEncryptionMenuProvider menuProvider = new FileEncryptionMenuProvider();


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentFileEncryptionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        requireActivity().removeMenuProvider(menuProvider);
        binding = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(menuProvider);

        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE);
        binding.btnEncrypt.setEnabled(false);
        keyStoreListFragment = binding.fragmentContainerView.getFragment();

        uri = getArguments().getParcelable(QRFragment.ARG_URI);
        fileInfo = Util.getFileInfo(requireContext(), uri);

        requireContext().getMainExecutor().execute(() ->
                ((FileInfoFragment) binding.fragmentContainerView6.getFragment()).setValues(
                        fileInfo.filename,
                        fileInfo.fileSize));


        keyStoreListFragment.setRunOnStart(
                fragmentKeyStoreListBinding -> keyStoreListFragment
                        .getSelectionTracker()
                        .addObserver(new SelectionTracker.SelectionObserver<>() {
                            @Override
                            public void onSelectionChanged() {
                                super.onSelectionChanged();
                                binding.btnEncrypt.setEnabled(keyStoreListFragment.getSelectionTracker().hasSelection());
                            }
                        }));

        binding.btnEncrypt.setOnClickListener(v -> encrypt());
        binding.txtProgress.setText("");
    }

    private void encrypt() {
        var selectedAlias = keyStoreListFragment.getSelectionTracker()
                .getSelection()
                .iterator()
                .next();

        if (encryptionRunning) {
            //cancel encryption
            binding.btnEncrypt.setText(R.string.encrypt);
            encryptionRunning = false;
        } else {
            //start encryption
            binding.btnEncrypt.setText(R.string.cancel);
            encryptionRunning = true;
            lastProgressPrc = -1;

            new Thread(() -> {
                try {
                    var fileRecord = OmsFileProvider.create(requireContext(),
                            fileInfo.filename + "." + MessageComposer.OMS_FILE_TYPE,
                            true);

                    EncryptedFile.create(requireContext().getContentResolver().openInputStream(uri),
                            fileRecord.path.toFile(),
                            Objects.requireNonNull(cryptographyManager.getByAlias(selectedAlias, preferences)).getPublic(),
                            RSAUtil.getRsaTransformation(preferences),
                            AESUtil.getKeyLength(preferences),
                            AESUtil.getAesTransformation(preferences),
                            () -> binding == null || !encryptionRunning,
                            this::updateProgress
                    );

                    if (encryptionRunning) {
                        updateProgress(fileInfo.fileSize); //100%
                        requireContext().getMainExecutor().execute(() -> {
                            if (binding == null) return;
                            binding.btnEncrypt.setText(R.string.encrypt);
                        });
                        navBackIfPaused = true;

                        var intent = new Intent(Intent.ACTION_SEND);
                        intent.setType("application/octet-stream");
                        intent.putExtra(Intent.EXTRA_STREAM, fileRecord.uri);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        startActivity(intent);
                    } else {
                        //operation has been cancelled
                        Files.delete(fileRecord.path);
                        requireContext().getMainExecutor().execute(() -> {
                            if (binding == null) return;
                            binding.btnEncrypt.setText(R.string.encrypt);
                            Toast.makeText(requireContext(),
                                    R.string.operation_has_been_cancelled,
                                    Toast.LENGTH_SHORT).show();
                        });
                        updateProgress(null);
                    }
                    encryptionRunning = false;
                } catch (Exception ex) {
                    Util.printStackTrace(ex);
                    requireActivity().getMainExecutor().execute(() -> Toast.makeText(requireContext(),
                            String.format("%s: %s", ex.getClass().getSimpleName(), ex.getMessage()),
                            Toast.LENGTH_LONG).show());
                }
            }).start();
        }
    }

    private void updateProgress(@Nullable Integer value) {
        String s = "";
        if (value != null) {
            var progressPrc = (int) ((double) value / (double) fileInfo.fileSize * 100D);
            if (lastProgressPrc == progressPrc) return;

            lastProgressPrc = progressPrc;
            s = lastProgressPrc == 100 ?
                    getString(R.string.done) :
                    String.format(Locale.getDefault(), getString(R.string.working_prc), lastProgressPrc);
        }
        var fs = s;

        requireContext().getMainExecutor().execute(() -> {
            if (binding == null) return;
            binding.txtProgress.setText(fs);
        });
    }

    private class FileEncryptionMenuProvider implements MenuProvider {

        @Override
        public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu);
        }

        @Override
        public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
            if (menuItem.getItemId() == R.id.menuItemHelp) {
                Util.openUrl(R.string.encrypt_file_md_url, requireContext());
            } else {
                return false;
            }

            return true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!navBackIfPaused) return;
        var navController = NavHostFragment.findNavController(this);
        if (navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() != R.id.fileEncryptionFragment) {
            Log.d(TAG, String.format("Already navigating to %s", navController.getCurrentDestination()));
            return;
        }
        Log.d(TAG, "onPause: going backward");
        Util.discardBackStack(this);
    }
}