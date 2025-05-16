package com.onemoresecret

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.onemoresecret.QRFragment.PresetEntry
import com.onemoresecret.databinding.FragmentPresetBinding
import java.util.function.Consumer

class PresetFragment(
    private val presetEntry: PresetEntry,
    private val onClick: Consumer<PresetEntry>,
    private val onLongClick: Consumer<PresetEntry>
) :
    Fragment() {
    private var binding: FragmentPresetBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPresetBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.d(TAG, "onViewCreated")
        super.onViewCreated(view, savedInstanceState)

        //apply preset
        binding!!.button.setOnClickListener { v: View? ->
            onClick.accept(presetEntry)
        }

        //enter preset configuration
        binding!!.button.setOnLongClickListener { v: View? ->
            onLongClick.accept(presetEntry)
            true
        }

        binding!!.button.text = presetEntry.symbol
        binding!!.txtName.text = presetEntry.name
    }

    companion object {
        private val TAG: String = PresetFragment::class.java.simpleName
    }
}