package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.databinding.FragmentKeyRequestPairingBinding
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause

class KeyRequestPairingFragment : FragmentWithNotificationBeforePause() {
    private lateinit var binding: FragmentKeyRequestPairingBinding
    private lateinit var reply: ByteArray

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentKeyRequestPairingBinding.inflate(
            inflater,
            container,
            false
        )

        return binding.root
    }

    private val btnListener = View.OnClickListener { btn: View? ->
        if (beforePause != null) beforePause!!.run()
        val activity = requireActivity() as MainActivity
        Thread { activity.sendReplyViaSocket(reply, true) }.start()
        discardBackStack(this)
    }

    override fun setBeforePause(r: Runnable?) {
        //not necessary
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnSendKey.setOnClickListener(btnListener)
        binding.btnSendKey.isEnabled = false
    }

    fun setReply(reply: ByteArray) {
        this.reply = reply
        requireActivity().mainExecutor.execute {
            binding.btnSendKey.isEnabled =
                true
        }
    }

    companion object {
        private val TAG: String = KeyRequestPairingFragment::class.java.simpleName
    }
}