package com.onemoresecret

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.SelectionTracker
import com.onemoresecret.crypto.AESUtil.getAesTransformationIdx
import com.onemoresecret.crypto.AESUtil.getKeyLength
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.crypto.MessageComposer.Companion.encodeAsOmsText
import com.onemoresecret.crypto.OneTimePassword
import com.onemoresecret.crypto.RSAUtils.getRsaTransformationIdx
import com.onemoresecret.crypto.TotpUriTransfer
import com.onemoresecret.databinding.FragmentKeyStoreListBinding
import com.onemoresecret.databinding.FragmentTotpManualEntryBinding
import java.security.interfaces.RSAPublicKey
import java.util.Timer
import java.util.TimerTask
import java.util.regex.Pattern

class TotpManualEntryFragment : Fragment() {
    private var binding: FragmentTotpManualEntryBinding? = null
    private var keyStoreListFragment: KeyStoreListFragment? = null
    private var outputFragment: OutputFragment? = null
    private var preferences: SharedPreferences? = null
    private val timer = Timer()

    private val cryptographyManager = CryptographyManager()

    private var selectedAlias: String? = null
    private var otp: OneTimePassword? = null
    private var lastState = -1L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTotpManualEntryBinding.inflate(inflater, container, false)
        binding!!.chipPeriod.text = String.format(
            getString(R.string.format_seconds),
            OneTimePassword.DEFAULT_PERIOD
        )
        binding!!.chipDigits.text = OneTimePassword.DIGITS[0]
        binding!!.chipAlgorithm.text = OneTimePassword.ALGORITHM[0]
        binding!!.textViewTotp.text = ""
        binding!!.textViewTimer.text = ""
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        keyStoreListFragment = binding!!.fragmentContainerView.getFragment<KeyStoreListFragment>()
        outputFragment = binding!!.fragmentContainerView2.getFragment<OutputFragment>()

        keyStoreListFragment!!.setRunOnStart { fragmentKeyStoreListBinding: FragmentKeyStoreListBinding? ->
            keyStoreListFragment!!
                .selectionTracker
                .addObserver(object : SelectionTracker.SelectionObserver<String?>() {
                    override fun onSelectionChanged() {
                        super.onSelectionChanged()
                        selectedAlias =
                            if (keyStoreListFragment!!.selectionTracker.hasSelection()) {
                                keyStoreListFragment!!.selectionTracker.selection.iterator().next()
                            } else {
                                null
                            }
                        generateResult()
                    }
                })
        }

        val textWatcher: TextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                generateResult()
            }
        }

        binding!!.editTextLabel.addTextChangedListener(textWatcher)
        binding!!.editTextSecret.addTextChangedListener(textWatcher)
        binding!!.chipAlgorithm.setOnClickListener { e: View? -> selectAlgorithm() }
        binding!!.chipDigits.setOnClickListener { e: View? -> selectDigits() }
        binding!!.chipPeriod.setOnClickListener { e: View? -> setPeriod() }

        requireActivity().mainExecutor.execute {
            generateResult()
            timerTask.run()
        }
    }

    private fun setPeriod() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Period, s")
        val numberPicker = NumberPicker(requireContext())
        numberPicker.minValue = PERIOD_MIN
        numberPicker.maxValue = PERIOD_MAX
        numberPicker.value = OneTimePassword.DEFAULT_PERIOD
        builder.setView(numberPicker)
        builder.setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int ->
            binding!!.chipPeriod.text =
                String.format(getString(R.string.format_seconds), numberPicker.value)
            generateResult()
        }
        builder.setNegativeButton(
            android.R.string.cancel,
            (DialogInterface.OnClickListener { dialog: DialogInterface, which: Int -> dialog.cancel() })
        )
        builder.show()
    }

    private fun selectDigits() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Digits")
        builder.setItems(
            OneTimePassword.DIGITS
        ) { dialog: DialogInterface?, which: Int ->
            requireContext().mainExecutor.execute {
                binding!!.chipDigits.text = OneTimePassword.DIGITS[which]
                generateResult()
            }
        }

        builder.show()
    }

    private fun selectAlgorithm() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Algorithm")
        builder.setItems(
            OneTimePassword.ALGORITHM
        ) { dialog: DialogInterface?, which: Int ->
            requireContext().mainExecutor.execute {
                binding!!.chipAlgorithm.text = OneTimePassword.ALGORITHM[which]
                generateResult()
            }
        }

        builder.show()
    }

    private fun generateResult() {
        val builder = Uri.Builder()
        builder.scheme(OneTimePassword.OTP_SCHEME)
            .authority(OneTimePassword.TOTP)
            .appendPath(binding!!.editTextLabel.text.toString())

        var param = binding!!.editTextSecret.text.toString()
        builder.appendQueryParameter(OneTimePassword.SECRET_PARAM, param)

        param = binding!!.chipPeriod.text.toString()
        if (param != String.format(
                getString(R.string.format_seconds),
                OneTimePassword.DEFAULT_PERIOD
            )
        ) {
            val pat = Pattern.compile("\\d+")
            val m = pat.matcher(param)
            m.find()
            builder.appendQueryParameter(OneTimePassword.PERIOD_PARAM, m.group())
        }

        param = binding!!.chipDigits.text.toString()
        if (param != OneTimePassword.DIGITS[0]) {
            builder.appendQueryParameter(OneTimePassword.DIGITS_PARAM, param)
        }

        param = binding!!.chipAlgorithm.text.toString()
        if (param != OneTimePassword.ALGORITHM[0]) {
            builder.appendQueryParameter(OneTimePassword.ALGORITHM_PARAM, param)
        }

        val uri = builder.build().toString()

        otp = OneTimePassword(uri)

        if (selectedAlias == null) {
            generateResponseCode(true)
        } else {
            try {
                val result = encodeAsOmsText(
                    TotpUriTransfer(
                        uri.toByteArray(),
                        (cryptographyManager.getCertificate(selectedAlias).publicKey as RSAPublicKey),
                        getRsaTransformationIdx(preferences!!),
                        getKeyLength(preferences!!),
                        getAesTransformationIdx(preferences!!)
                    ).message
                )

                outputFragment!!.setMessage(result, "TOTP Configuration (encrypted)")
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }

        binding!!.editTextSecret.isEnabled = selectedAlias == null
        binding!!.editTextLabel.isEnabled = selectedAlias == null
        binding!!.chipAlgorithm.isEnabled = selectedAlias == null
        binding!!.chipDigits.isEnabled = selectedAlias == null
        binding!!.chipPeriod.isEnabled = selectedAlias == null

        generateResponseCode(true)
    }

    private val timerTask: TimerTask
        get() = object : TimerTask() {
            override fun run() {
                generateResponseCode(false)
                timer.schedule(timerTask, 1000)
            }
        }

    private fun generateResponseCode(force: Boolean) {
        if (otp == null) return  //otp not set yet


        try {
            val otpState = otp!!.state
            val code = otp!!.generateResponseCode(otpState.current)

            requireActivity().mainExecutor.execute {
                if (binding == null) return@execute  //fragment has been destroyed

                binding!!.textViewTimer.text =
                    String.format("...%ss", otp!!.period - otpState.secondsUntilNext)
                binding!!.textViewTotp.text = code
                if (lastState != otpState.current || force) {
                    //new State = new code; update output fragment
                    if (selectedAlias == null) {
                        outputFragment!!.setMessage(code, "One-Time-Password")
                    }
                    lastState = otpState.current
                }
            }
        } catch (e: Exception) {
            //invalid secret
            Log.e(TAG, e.message!!)
            outputFragment!!.setMessage(null, null)
            requireActivity().mainExecutor.execute {
                if (binding == null) return@execute  //fragment has been destroyed

                binding!!.textViewTotp.text =
                    "-".repeat(binding!!.chipDigits.text.toString().toInt())
                binding!!.textViewTimer.text = ""
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        timer.cancel()
        binding = null
    }

    companion object {
        private val TAG: String = TotpManualEntryFragment::class.java.simpleName
        private const val PERIOD_MIN = 1
        private const val PERIOD_MAX = 120
    }
}