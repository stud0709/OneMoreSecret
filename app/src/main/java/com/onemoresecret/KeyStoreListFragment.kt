package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.ItemKeyProvider
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.RecyclerView
import com.onemoresecret.Util.byteArrayToHex
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.RSAUtils.getFingerprint
import com.onemoresecret.databinding.FragmentKeyStoreListBinding
import com.onemoresecret.databinding.PrivateKeyListItemBinding
import java.security.KeyStoreException
import java.security.interfaces.RSAPublicKey
import java.util.Collections
import java.util.function.Consumer

/**
 * A fragment representing a list of Items.
 */
class KeyStoreListFragment : Fragment() {
    private var binding: FragmentKeyStoreListBinding? = null
    lateinit var selectionTracker: SelectionTracker<String?>

    private val cryptographyManager = CryptographyManager()

    private val aliasList: MutableList<String> = ArrayList()

    private val itemAdapter = ItemAdapter()

    private var runOnStart: Consumer<FragmentKeyStoreListBinding?>? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentKeyStoreListBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    fun setRunOnStart(runOnStart: Consumer<FragmentKeyStoreListBinding?>?) {
        this.runOnStart = runOnStart
    }

    fun onItemRemoved(alias: String) {
        //var idx = aliasList.indexOf(alias);
        aliasList.remove(alias)
        //itemAdapter.notifyItemRemoved(idx); //this is not working as per 1.21
        itemAdapter.notifyDataSetChanged()
    }

    override fun onStart() {
        super.onStart()
        if (runOnStart != null) runOnStart!!.accept(binding)
        runOnStart = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aliasList.clear()

        try {
            val aliasesEnum = cryptographyManager.keyStore!!.aliases()
            while (aliasesEnum.hasMoreElements()) {
                aliasList.add(aliasesEnum.nextElement())
            }
            aliasList.sort()
        } catch (e: KeyStoreException) {
            throw RuntimeException(e)
        }

        binding!!.list.adapter = itemAdapter

        selectionTracker = SelectionTracker.Builder(
            "selectionTracker",
            binding!!.list,
            PrivateKeyItemKeyProvider(ItemKeyProvider.SCOPE_MAPPED),
            PrivateKeyLookup(),
            StorageStrategy.createStringStorage()
        )
            .withSelectionPredicate(SelectionPredicates.createSelectSingleAnything())
            .build()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    internal inner class ItemAdapter : RecyclerView.Adapter<PrivateKeyViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrivateKeyViewHolder {
            val binding =
                PrivateKeyListItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            return PrivateKeyViewHolder(binding)
        }

        override fun onBindViewHolder(holder: PrivateKeyViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount(): Int {
            return aliasList.size
        }
    }


    inner class PrivateKeyViewHolder(private val binding: PrivateKeyListItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        var alias: String? = null
        private var position = 0

        fun bind(position: Int) {
            this.position = position
            this.alias = aliasList[position]
            try {
                binding.textItemKeyAlias.text = alias
                val publicKey = cryptographyManager.getCertificate(alias).publicKey as RSAPublicKey
                binding.textItemFingerprint.text = byteArrayToHex(
                    getFingerprint(publicKey)
                )
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
            binding.root.isActivated = selectionTracker!!.isSelected(alias)
        }
    }


    inner class PrivateKeyLookup : ItemDetailsLookup<String?>() {
        override fun getItemDetails(e: MotionEvent): ItemDetails<String?>? {
            val view = binding!!.list.findChildViewUnder(e.x, e.y) ?: return null

            val holder = binding!!.list.getChildViewHolder(view) as PrivateKeyViewHolder
            return object : ItemDetails<String?>() {
                override fun getPosition(): Int {
                    return holder.layoutPosition //instead of position
                }

                override fun getSelectionKey(): String? {
                    return holder.alias
                }
            }
        }
    }


    inner class PrivateKeyItemKeyProvider
    /**
     * Creates a new provider with the given scope.
     *
     * @param scope Scope can't be changed at runtime.
     */
        (scope: Int) : ItemKeyProvider<String?>(scope) {
        override fun getKey(position: Int): String? {
            return aliasList[position]
        }

        override fun getPosition(key: String): Int {
            return aliasList.indexOf(key)
        }
    }
}