package com.onemoresecret

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.recyclerview.selection.*
import androidx.recyclerview.widget.RecyclerView
import com.onemoresecret.composable.KeyStoreList
import com.onemoresecret.composable.OneMoreSecretTheme
import com.onemoresecret.composable.bindPrivateKeyListItem
import com.onemoresecret.composable.createPrivateKeyListItemComposeView
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.KeyStoreEntry
import com.onemoresecret.crypto.RSAUtil

@Composable
fun KeyStoreListScreen(
    onSelectionChanged: (String?) -> Unit
) {
    val context = LocalContext.current
    val preferences = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)

    var aliasList by remember { mutableStateOf(emptyList<String>()) }
    var keyStoreEntries by remember { mutableStateOf(emptySet<KeyStoreEntry>()) }
    var tracker by remember { mutableStateOf<SelectionTracker<String>?>(null) }
    var itemAdapter by remember { mutableStateOf<ItemAdapter?>(null) }

    LaunchedEffect(Unit) {
        val keyStoreStringSet = preferences.getStringSet(CryptographyManager.PROP_KEYSTORE, HashSet()) ?: emptySet()
        val entries = keyStoreStringSet.map { entry -> OmsJson.decode<KeyStoreEntry>(entry) }.toSet()
        keyStoreEntries = entries
        aliasList = entries.map { it.alias }.sorted()
    }

    KeyStoreList { view ->
        if (view.adapter == null && aliasList.isNotEmpty()) {
            val adapter = ItemAdapter(aliasList, keyStoreEntries, tracker)
            itemAdapter = adapter
            view.adapter = adapter

            val newTracker = SelectionTracker.Builder(
                "selectionTracker",
                view,
                PrivateKeyItemKeyProvider(ItemKeyProvider.SCOPE_MAPPED, aliasList),
                PrivateKeyLookup(view),
                StorageStrategy.createStringStorage()
            ).withSelectionPredicate(SelectionPredicates.createSelectSingleAnything()).build()

            newTracker.addObserver(object : SelectionTracker.SelectionObserver<String>() {
                override fun onSelectionChanged() {
                    super.onSelectionChanged()
                    val selectedAlias = newTracker.selection.firstOrNull()
                    onSelectionChanged(selectedAlias)
                    // Notify adapter of selection change to update activated state
                    adapter.setTracker(newTracker)
                }
            })
            tracker = newTracker
        }
    }
}

class ItemAdapter(
    private var aliasList: List<String>,
    private var keyStoreEntries: Set<KeyStoreEntry>,
    private var tracker: SelectionTracker<String>?
) : RecyclerView.Adapter<PrivateKeyViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrivateKeyViewHolder {
        val composeView = createPrivateKeyListItemComposeView(parent)
        return PrivateKeyViewHolder(composeView, keyStoreEntries)
    }

    override fun onBindViewHolder(holder: PrivateKeyViewHolder, position: Int) {
        val alias = aliasList[position]
        holder.bind(alias, position, tracker?.isSelected(alias) == true)
    }

    override fun getItemCount(): Int = aliasList.size

    fun setTracker(newTracker: SelectionTracker<String>?) {
        tracker = newTracker
        notifyDataSetChanged()
    }
}

class PrivateKeyViewHolder(
    private val composeView: ComposeView,
    private val keyStoreEntries: Set<KeyStoreEntry>
) : RecyclerView.ViewHolder(composeView) {
    var alias: String = ""
        private set
    var itemPosition: Int = RecyclerView.NO_POSITION
        private set

    fun bind(alias: String, position: Int, isSelected: Boolean) {
        this.itemPosition = position
        this.alias = alias
        try {
            val keyStoreEntry = keyStoreEntries.first { it.alias == alias }
            val publicKey = RSAUtil.restorePublicKey(keyStoreEntry.public)
            val fingerprint = Util.byteArrayToHex(RSAUtil.getFingerprint(publicKey))
            bindPrivateKeyListItem(
                composeView,
                alias,
                fingerprint,
                isSelected
            )
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
        composeView.isActivated = isSelected
    }
}

class PrivateKeyLookup(private val recyclerView: RecyclerView) : ItemDetailsLookup<String>() {
    override fun getItemDetails(e: MotionEvent): ItemDetails<String>? {
        val view = recyclerView.findChildViewUnder(e.x, e.y) ?: return null
        val holder = recyclerView.getChildViewHolder(view) as PrivateKeyViewHolder

        return object : ItemDetails<String>() {
            override fun getPosition(): Int = holder.itemPosition
            override fun getSelectionKey(): String = holder.alias
        }
    }
}

class PrivateKeyItemKeyProvider(scope: Int, private val aliasList: List<String>) : ItemKeyProvider<String>(scope) {
    override fun getKey(position: Int): String = aliasList[position]
    override fun getPosition(key: String): Int = aliasList.indexOf(key)
}
