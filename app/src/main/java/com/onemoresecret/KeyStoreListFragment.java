package com.onemoresecret;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentKeyStoreListBinding;
import com.onemoresecret.databinding.FragmentOutputBinding;
import com.onemoresecret.databinding.PrivateKeyListItemBinding;

import java.security.KeyStoreException;
import java.security.interfaces.RSAPublicKey;
import java.util.Enumeration;

/**
 * A fragment representing a list of Items.
 */
public class KeyStoreListFragment extends Fragment {
    public static final String TAG = KeyStoreListFragment.class.getSimpleName();
    private FragmentKeyStoreListBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentKeyStoreListBinding.inflate(inflater, container, false);
        binding.list.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.list.setAdapter(new ItemAdapter());
        return binding.getRoot();
    }

    class ItemAdapter extends RecyclerView.Adapter<ItemAdapter.ViewHolder> {
        private final CryptographyManager cryptographyManager = new CryptographyManager();
        private final String[] aliases;

        public ItemAdapter() {
            try {
                aliases = new String[cryptographyManager.keyStore.size()];
                int i = 0;
                Enumeration<String> aliasesEnum = cryptographyManager.keyStore.aliases();
                while (aliasesEnum.hasMoreElements()) {
                    aliases[i++] = aliasesEnum.nextElement();
                }
            } catch (KeyStoreException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        @NonNull
        @Override
        public ItemAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PrivateKeyListItemBinding binding = PrivateKeyListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull ItemAdapter.ViewHolder holder, int position) {
            try {
                String alias = aliases[position];
                holder.getBinding().textItemKeyAlias.setText(alias);
                holder.getBinding().textItemFingerprint.setText(
                        BluetoothController.byteArrayToHex(
                                CryptographyManager.getFingerprint(
                                        (RSAPublicKey) cryptographyManager.getCertificate(alias).getPublicKey())));


            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public int getItemCount() {
            return aliases.length;
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            private final PrivateKeyListItemBinding binding;

            public ViewHolder(@NonNull PrivateKeyListItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }

            public PrivateKeyListItemBinding getBinding() {
                return binding;
            }
        }
    }
}