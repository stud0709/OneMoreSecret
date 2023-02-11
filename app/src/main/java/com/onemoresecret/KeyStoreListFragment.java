package com.onemoresecret;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StableIdKeyProvider;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.onemoresecret.bt.BluetoothController;
import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentKeyStoreListBinding;
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
    private SelectionTracker<String> selectionTracker;

    private final CryptographyManager cryptographyManager = new CryptographyManager();

    private String[] aliases;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
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

        binding = FragmentKeyStoreListBinding.inflate(inflater, container, false);
        binding.list.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.list.setAdapter(new ItemAdapter());

        selectionTracker = new SelectionTracker.Builder<>("selectionTracker",
                binding.list,
                new PrivateKeyItemKeyProvider(ItemKeyProvider.SCOPE_MAPPED),
                new PrivateKeyLookup(),
                StorageStrategy.createStringStorage())
                .withSelectionPredicate(SelectionPredicates.createSelectSingleAnything())
                .build();

        selectionTracker.addObserver(new SelectionTracker.SelectionObserver<String>() {
            @Override
            public void onSelectionChanged() {
                super.onSelectionChanged();

            }
        });

        return binding.getRoot();
    }

    class ItemAdapter extends RecyclerView.Adapter<PrivateKeyViewHolder> {


        @NonNull
        @Override
        public KeyStoreListFragment.PrivateKeyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PrivateKeyListItemBinding binding = PrivateKeyListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new KeyStoreListFragment.PrivateKeyViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull KeyStoreListFragment.PrivateKeyViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return aliases.length;
        }

    }


    public class PrivateKeyViewHolder extends RecyclerView.ViewHolder {
        private final PrivateKeyListItemBinding binding;
        private String alias;
        private int position;

        public PrivateKeyViewHolder(@NonNull PrivateKeyListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(int position) {
            this.position = position;
            this.alias = aliases[position];
            try {
                binding.textItemKeyAlias.setText(alias);
                binding.textItemFingerprint.setText(
                        BluetoothController.byteArrayToHex(
                                CryptographyManager.getFingerprint(
                                        (RSAPublicKey) cryptographyManager.getCertificate(alias).getPublicKey())));
            } catch (Exception e) {
                e.printStackTrace();
            }
            binding.getRoot().setActivated(selectionTracker.isSelected(alias));
        }
    }


    public class PrivateKeyLookup extends ItemDetailsLookup<String> {

        @Nullable
        @Override
        public ItemDetails<String> getItemDetails(@NonNull MotionEvent e) {
            View view = binding.list.findChildViewUnder(e.getX(), e.getY());
            if (view == null)
                return null;

            PrivateKeyViewHolder holder = (PrivateKeyViewHolder) binding.list.getChildViewHolder(view);
            return new ItemDetails<String>() {
                @Override
                public int getPosition() {
                    return holder.position;
                }

                @Nullable
                @Override
                public String getSelectionKey() {
                    return holder.alias;
                }
            };
        }
    }


    public class PrivateKeyItemKeyProvider extends ItemKeyProvider<String> {

        /**
         * Creates a new provider with the given scope.
         *
         * @param scope Scope can't be changed at runtime.
         */
        protected PrivateKeyItemKeyProvider(int scope) {
            super(scope);
        }

        @Nullable
        @Override
        public String getKey(int position) {
            return aliases[position];
        }

        @Override
        public int getPosition(@NonNull String key) {
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(key)) return i;
            }
            return -1;
        }
    }

}