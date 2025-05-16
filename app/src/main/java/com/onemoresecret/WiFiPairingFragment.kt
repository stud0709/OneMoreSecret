package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.onemoresecret.databinding.FragmentWiFiPairingBinding

class WiFiPairingFragment : Fragment() {
    private var binding: FragmentWiFiPairingBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWiFiPairingBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding!!.txtResponseCodeUpdate.visibility = View.INVISIBLE
        binding!!.txtIntroResponse.visibility = View.INVISIBLE
    }

    fun setData(requestId: String?, responseCode: String?, onConfirm: Runnable) {
        binding!!.txtRequestId.text = requestId
        binding!!.txtResponseCodeUpdate.text = responseCode
        binding!!.btnConfirm.setOnClickListener { e: View? ->
            binding!!.btnConfirm.isEnabled = false
            binding!!.btnConfirm.setText(R.string.pairing_accepted)
            binding!!.txtResponseCodeUpdate.visibility = View.VISIBLE
            binding!!.txtIntroResponse.visibility = View.VISIBLE
            onConfirm.run()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }
}