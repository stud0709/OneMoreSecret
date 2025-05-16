package com.onemoresecret

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.onemoresecret.databinding.FragmentFileInfoBinding
import java.util.Locale

class FileInfoFragment : Fragment() {
    private var binding: FragmentFileInfoBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentFileInfoBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    fun setValues(filename: String?, filesize: Int) {
        if (binding == null) return
        binding!!.textViewFilenameValue.text = filename
        binding!!.textViewFileSizeValue.text =
            String.format(Locale.getDefault(), "%.3f KB", filesize / 1024.0)
    }
}