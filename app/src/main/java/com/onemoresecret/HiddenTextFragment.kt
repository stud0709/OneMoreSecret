package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.onemoresecret.databinding.FragmentHiddenTextBinding

class HiddenTextFragment : Fragment() {
    private var binding: FragmentHiddenTextBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHiddenTextBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    fun setText(text: String?) {
        binding!!.textViewMessage.text = text
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding!!.textViewMessage.text = getString(R.string.hidden_text)
        binding = null
    }
}