package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.onemoresecret.databinding.FragmentCryptoCurrencyAddressBinding

class CryptoCurrencyAddressFragment : Fragment() {
    private var binding: FragmentCryptoCurrencyAddressBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCryptoCurrencyAddressBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    fun setValue(publicAddress: String?) {
        if (binding == null) return
        binding!!.textViewPublicAddress.text = publicAddress
    }
}