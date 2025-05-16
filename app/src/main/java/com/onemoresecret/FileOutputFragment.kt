package com.onemoresecret

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import com.onemoresecret.databinding.FragmentFileOutputBinding
import com.onemoresecret.msg_fragment_plugins.FragmentWithNotificationBeforePause
import java.util.Objects

class FileOutputFragment : FragmentWithNotificationBeforePause() {
    private var binding: FragmentFileOutputBinding? = null

    private var uri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentFileOutputBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    private val btnListener = View.OnClickListener { btn: View ->
        if (beforePause != null) beforePause!!.run()
        val intent =
            Intent(if (btn === binding!!.btnView) Intent.ACTION_VIEW else Intent.ACTION_SEND)
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        val mimeType = Objects.requireNonNullElse(
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension),
            "application/octet-stream"
        )
        if (btn === binding!!.btnView) {
            intent.setDataAndType(uri, mimeType)
        } else {
            intent.setType(mimeType)
            intent.putExtra(Intent.EXTRA_STREAM, uri)
        }

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        requireActivity().startActivity(intent)
    }

    override fun setBeforePause(r: Runnable?) {
        //not necessary
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding!!.txtWorking.text = ""
        binding!!.btnSend.setOnClickListener(btnListener)
        binding!!.btnView.setOnClickListener(btnListener)
        binding!!.btnSend.isEnabled = false
        binding!!.btnView.isEnabled = false
    }

    fun setUri(uri: Uri?) {
        this.uri = uri

        requireContext().mainExecutor.execute {
            binding!!.btnView.isEnabled = uri != null
            binding!!.btnSend.isEnabled = uri != null
        }
    }

    fun setProgress(s: String) {
        requireContext().mainExecutor.execute {
            if (binding == null) return@execute
            binding!!.txtWorking.text = s
        }
    }

    companion object {
        private val TAG: String = FileOutputFragment::class.simpleName!!
    }
}