package com.onemoresecret

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.fasterxml.jackson.core.JsonProcessingException
import com.onemoresecret.composable.KeyStoreList
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.bindPrivateKeyListItem
import com.onemoresecret.composable.createPrivateKeyListItemComposeView
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.KeyStoreEntry
import com.onemoresecret.crypto.RSAUtil
import java.util.function.Consumer

class KeyStoreListFragment : Fragment() {
    private val aliasList = mutableListOf<String>()
    private val itemAdapter = ItemAdapter()
    private var keyStoreEntries = emptySet<KeyStoreEntry>()
    private var recyclerView: RecyclerView? = null
    private var tracker: SelectionTracker<String>? = null
    private var runOnStart: Consumer<SelectionTracker<String>>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        loadKeyStoreEntries()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                OneMoreSecretTheme {
                    KeyStoreList { view ->
                        if (recyclerView !== view) {
                            recyclerView = view
                            setupRecyclerView(view)
                        }
                    }
                }
            }
        }
    }

    fun getSelectionTracker(): SelectionTracker<String> {
        return requireNotNull(tracker)
    }

    fun setRunOnStart(runOnStart: Consumer<SelectionTracker<String>>) {
        this.runOnStart = runOnStart
        notifyRunOnStartIfReady()
    }

    fun onItemRemoved(alias: String) {
        val index = aliasList.indexOf(alias)
        if (index == -1) return
        aliasList.remove(alias)
        itemAdapter.notifyItemRemoved(index)
        //Update indices for items below the removed one
        itemAdapter.notifyItemRangeChanged(index, aliasList.size - index)
    }

    override fun onStart() {
        super.onStart()
        notifyRunOnStartIfReady()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView = null
        tracker = null
    }

    private fun loadKeyStoreEntries() {
        aliasList.clear()
        val preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)
        val keyStoreStringSet =
            preferences.getStringSet(CryptographyManager.PROP_KEYSTORE, HashSet()) ?: emptySet()

        keyStoreEntries = keyStoreStringSet.map { entry ->
            try {
                Util.JACKSON_MAPPER.readValue(entry, KeyStoreEntry::class.java)
            } catch (e: JsonProcessingException) {
                throw RuntimeException(e)
            }
        }.toSet()

        aliasList.addAll(keyStoreEntries.map { it.alias }.sorted())
    }

    private fun setupRecyclerView(view: RecyclerView) {
        view.adapter = itemAdapter
        tracker = SelectionTracker.Builder(
            "selectionTracker",
            view,
            PrivateKeyItemKeyProvider(ItemKeyProvider.SCOPE_MAPPED),
            PrivateKeyLookup(),
            StorageStrategy.createStringStorage()
        ).withSelectionPredicate(SelectionPredicates.createSelectSingleAnything()).build()
        notifyRunOnStartIfReady()
    }

    private fun notifyRunOnStartIfReady() {
        val selectionTracker = tracker ?: return
        runOnStart?.accept(selectionTracker)
        runOnStart = null
    }

    inner class ItemAdapter : RecyclerView.Adapter<PrivateKeyViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrivateKeyViewHolder {
            val composeView = createPrivateKeyListItemComposeView(parent)
            return PrivateKeyViewHolder(composeView)
        }

        override fun onBindViewHolder(holder: PrivateKeyViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int {
            return aliasList.size
        }
    }

    inner class PrivateKeyViewHolder(
        private val composeView: ComposeView
    ) : RecyclerView.ViewHolder(composeView) {
        var alias: String = ""
            private set
        var itemPosition: Int = RecyclerView.NO_POSITION
            private set

        fun bind(position: Int) {
            this.itemPosition = position
            alias = aliasList[position]
            try {
                val keyStoreEntry = keyStoreEntries.first { it.alias == alias }
                val publicKey = RSAUtil.restorePublicKey(keyStoreEntry.public)
                val fingerprint = Util.byteArrayToHex(RSAUtil.getFingerprint(publicKey))
                bindPrivateKeyListItem(
                    composeView,
                    alias,
                    fingerprint,
                    tracker?.isSelected(alias) == true
                )
            } catch (e: Exception) {
                throw RuntimeException(e)
            }

            composeView.isActivated = tracker?.isSelected(alias) == true
        }
    }

    inner class PrivateKeyLookup : ItemDetailsLookup<String>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<String>? {
            val list = recyclerView ?: return null
            val view = list.findChildViewUnder(e.x, e.y) ?: return null
            val holder = list.getChildViewHolder(view) as PrivateKeyViewHolder

            return object : ItemDetails<String>() {
                override fun getPosition(): Int {
                    return holder.itemPosition
                }

                override fun getSelectionKey(): String {
                    return holder.alias
                }
            }
        }
    }

    inner class PrivateKeyItemKeyProvider(scope: Int) : ItemKeyProvider<String>(scope) {
        override fun getKey(position: Int): String {
            return aliasList[position]
        }

        override fun getPosition(key: String): Int {
            return aliasList.indexOf(key)
        }
    }
}
