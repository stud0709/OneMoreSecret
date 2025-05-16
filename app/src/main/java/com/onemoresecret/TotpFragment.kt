package com.onemoresecret

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.onemoresecret.crypto.OneTimePassword
import com.onemoresecret.databinding.FragmentTotpBinding
import java.util.Timer
import java.util.TimerTask

class TotpFragment : Fragment() {
    private var binding: FragmentTotpBinding? = null
    private val timer = Timer()
    private var lastState = -1L
    private var otp: OneTimePassword? = null
    private val code = MutableLiveData<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentTotpBinding.inflate(inflater, container, false)
        binding!!.textViewTotpValue.text = ""
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        timerTask.run()
    }

    fun init(
        otp: OneTimePassword,
        owner: LifecycleOwner,
        observer: Observer<String>
    ) {
        this.otp = otp
        code.observe(owner, observer)
        val name = otp.name
        val issuer = otp.issuer

        requireActivity().mainExecutor.execute {
            if (issuer == null || issuer.isEmpty()) {
                binding!!.textViewNameIssuer.text = name
            } else {
                binding!!.textViewNameIssuer.text =
                    String.format(getString(R.string.totp_name_issuer), name, issuer)
            }
        }
    }

    private val timerTask: TimerTask
        get() = object : TimerTask() {
            override fun run() {
                if (generateResponseCode(false)) {
                    timer.schedule(timerTask, 1000)
                }
            }
        }

    fun setTotpText(s: String?) {
        requireActivity().mainExecutor.execute {
            if (binding == null) return@execute  //fragment has been destroyed
            binding!!.textViewTotpValue.text = s
        }
    }

    fun refresh() {
        generateResponseCode(true)
    }

    private fun generateResponseCode(force: Boolean): Boolean {
        if (otp == null) return true //otp not set (yet)


        try {
            val state = otp!!.state

            requireActivity().mainExecutor.execute {
                if (binding == null) return@execute  //fragment has been destroyed
                binding!!.textViewRemaining.text =
                    String.format("...%ss", otp!!.period - state.secondsUntilNext)
            }

            if (lastState != state.current || force) {
                code.postValue(otp!!.generateResponseCode(state.current))
                lastState = state.current
            }
        } catch (e: Exception) {
            Log.wtf(TAG, e)
            requireActivity().mainExecutor.execute {
                Toast.makeText(
                    requireContext(),
                    e.message,
                    Toast.LENGTH_LONG
                ).show()
            }
            return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timer.cancel()
        binding!!.textViewTotpValue.text = ""
        binding = null
    }

    companion object {
        private val TAG: String = TotpFragment::class.java.simpleName
    }
}