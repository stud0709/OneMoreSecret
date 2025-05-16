package com.onemoresecret

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.onemoresecret.databinding.FragmentCrashReportBinding
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.function.Function
import androidx.core.net.toUri

class CrashReportFragment : Fragment() {
    private var binding: FragmentCrashReportBinding? = null
    private var crashReportData: CrashReportData? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCrashReportBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        crashReportData =
            requireActivity().intent.getSerializableExtra(OmsUncaughtExceptionHandler.EXTRA_CRASH_REPORT) as CrashReportData?

        binding!!.chkLogcat.setOnCheckedChangeListener { _: CompoundButton?, _: Boolean -> displayCrashReport() }
        binding!!.btnDismiss.setOnClickListener { requireActivity().finish() }
        binding!!.btnSend.setOnClickListener { sendEmail() }
        displayCrashReport()
    }

    private fun sendEmail() {
        val intentFx =
            Function { action: String? ->
                val intent = Intent(action)
                intent.setData("mailto:".toUri())
                intent.putExtra(Intent.EXTRA_SUBJECT, "OneMoreSecret crash report")
                intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(getString(R.string.contact_email)))
                intent
            }

        try {
            val crashReport = crashReportData!!.toString(binding!!.chkLogcat.isChecked)
            val fileRecord = OmsFileProvider.create(requireContext(), "crash_report.txt", false)
            Files.write(fileRecord!!.path, crashReport!!.toByteArray(StandardCharsets.UTF_8))
            val intentSend = intentFx.apply(Intent.ACTION_SEND)
            intentSend.putExtra(Intent.EXTRA_STREAM, fileRecord.uri)
            intentSend.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intentSend.putExtra(
                Intent.EXTRA_TEXT,
                "The report file has been attached. Please use this email to provide additional feedback (this is optional)."
            )

            try {
                //try send as attached file
                startActivity(intentSend)
                requireActivity().finish()
            } catch (ex: ActivityNotFoundException) {
                try {
                    //email without attachment
                    val intentSendTo = intentFx.apply(Intent.ACTION_SENDTO)
                    intentSendTo.putExtra(Intent.EXTRA_TEXT, crashReport)
                    startActivity(intentSendTo)
                    requireActivity().finish()
                } catch (exx: ActivityNotFoundException) {
                    requireContext().mainExecutor.execute {
                        Toast.makeText(context, "Could not send email", Toast.LENGTH_LONG).show()
                        requireActivity().finish()
                    }
                }
            }
        } catch (ex: IOException) {
            ex.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
        requireActivity().finish()
    }

    private fun displayCrashReport() {
        binding!!.txtCrashReport.text = crashReportData!!.toString(binding!!.chkLogcat.isChecked)
    }
}