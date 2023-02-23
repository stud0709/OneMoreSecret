package com.onemoresecret;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.RecyclerView;

import com.onemoresecret.crypto.CryptographyManager;
import com.onemoresecret.databinding.FragmentKeyStoreListBinding;
import com.onemoresecret.databinding.PrivateKeyListItemBinding;

import java.security.KeyStoreException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;

/**
 * A fragment representing a list of Items.
 */
public class KeyStoreListFragment extends Fragment {
    private FragmentKeyStoreListBinding binding;
    private SelectionTracker<String> selectionTracker;

    private final CryptographyManager cryptographyManager = new CryptographyManager();

    private final List<String> aliasList = new ArrayList<>();

    private final ItemAdapter itemAdapter = new ItemAdapter();

    private Consumer<FragmentKeyStoreListBinding> runOnStart;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentKeyStoreListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    public SelectionTracker<String> getSelectionTracker() {
        return selectionTracker;
    }

    public void setRunOnStart(Consumer<FragmentKeyStoreListBinding> runOnStart) {
        this.runOnStart = runOnStart;
    }

    public void onItemRemoved(String alias) {
        int idx = aliasList.indexOf(alias);
        aliasList.remove(alias);
        //itemAdapter.notifyItemRemoved(idx); //this is not working
        itemAdapter.notifyDataSetChanged();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (runOnStart != null) runOnStart.accept(binding);
        runOnStart = null;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        aliasList.clear();

        try {
            Enumeration<String> aliasesEnum = cryptographyManager.keyStore.aliases();
            while (aliasesEnum.hasMoreElements()) {
                aliasList.add(aliasesEnum.nextElement());
            }
            Collections.sort(aliasList);
        } catch (KeyStoreException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        binding.list.setAdapter(itemAdapter);

        selectionTracker = new SelectionTracker.Builder<>("selectionTracker",
                binding.list,
                new PrivateKeyItemKeyProvider(ItemKeyProvider.SCOPE_MAPPED),
                new PrivateKeyLookup(),
                StorageStrategy.createStringStorage())
                .withSelectionPredicate(SelectionPredicates.createSelectSingleAnything())
                .build();
    }

    public OutputFragment getOutputFragment() {
        return (OutputFragment) binding.keyListOutputFragment.getFragment();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    class ItemAdapter extends RecyclerView.Adapter<PrivateKeyViewHolder> {

        @NonNull
        @Override
        public KeyStoreListFragment.PrivateKeyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            PrivateKeyListItemBinding binding =
                    PrivateKeyListItemBinding.inflate(LayoutInflater.from(parent.getContext()),
                            parent,
                            false);
            return new KeyStoreListFragment.PrivateKeyViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull KeyStoreListFragment.PrivateKeyViewHolder holder, int position) {
            holder.bind(position);
        }

        @Override
        public int getItemCount() {
            return aliasList.size();
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
            this.alias = aliasList.get(position);
            try {
                binding.textItemKeyAlias.setText(alias);
                binding.textItemFingerprint.setText(
                        Util.byteArrayToHex(
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
            return aliasList.get(position);
        }

        @Override
        public int getPosition(@NonNull String key) {
            return aliasList.indexOf(key);
        }
    }

}