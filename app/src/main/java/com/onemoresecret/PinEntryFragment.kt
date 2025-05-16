package com.onemoresecret

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.onemoresecret.crypto.CryptographyManager
import com.onemoresecret.databinding.FragmentPinEntryBinding
import java.security.KeyStoreException
import java.util.Arrays
import java.util.Random
import androidx.core.content.edit

class PinEntryFragment(
    private val onSuccess: Runnable?,
    private var onCancel: Runnable?,
    private val onPanic: Runnable?
) : DialogFragment() {
    private var binding: FragmentPinEntryBinding? = null
    private var preferences: SharedPreferences? = null
    private var context: Context? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPinEntryBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        /* Remember the context. Will need it in onDismiss to purge tmp files. */
        this.context = context
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        binding!!.textViewPin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            }

            override fun afterTextChanged(s: Editable) {
                binding!!.imageButtonUnlock.isEnabled = binding!!.textViewPin.text.length > 0
            }
        })

        binding!!.textViewPin.text = ""

        //initialize keys with random numbers 0...9
        val bArr = arrayOf(
            binding!!.btn0,
            binding!!.btn1,
            binding!!.btn2,
            binding!!.btn3,
            binding!!.btn4,
            binding!!.btn5,
            binding!!.btn6,
            binding!!.btn7,
            binding!!.btn8,
            binding!!.btn9
        )

        val bList = ArrayList(Arrays.asList(*bArr))

        val rnd = Random()

        for (i in bArr.indices) {
            bList.removeAt(rnd.nextInt(bList.size)).text = i.toString()
        }

        //set listeners
        for (btn in bArr) {
            btn.setOnClickListener { v: View -> this.onDigit(v) }
        }

        binding!!.imageButtonDel.setOnClickListener { v: View? ->
            val cs = binding!!.textViewPin.text
            if (cs.length == 0) return@setOnClickListener
            binding!!.textViewPin.text = cs.subSequence(0, cs.length - 1)
        }

        binding!!.imageButtonUnlock.setOnClickListener { v: View? -> tryUnlock() }
    }

    private fun tryUnlock() {
        val panicPinEntered = (binding!!.textViewPin.text.toString()
                == preferences!!.getString(PinSetupFragment.PROP_PANIC_PIN, null))

        if (panicPinEntered) {
            //panic pin
            panic()
        }

        if (panicPinEntered ||
            (binding!!.textViewPin.text.toString()
                    == preferences!!.getString(PinSetupFragment.PROP_PIN_VALUE, null))
        ) {
            //pin entered correctly
            Toast.makeText(requireContext(), R.string.pin_accepted, Toast.LENGTH_SHORT).show()
            requireContext().mainExecutor.execute(onSuccess)
            onCancel = null
            dismiss()
        } else {
            //wrong pin
            var remainingAttempts =
                preferences!!.getInt(PinSetupFragment.PROP_REMAINING_ATTEMPTS, Int.MAX_VALUE)
            remainingAttempts--

            preferences!!.edit().putInt(PinSetupFragment.PROP_REMAINING_ATTEMPTS, remainingAttempts)
                .apply()

            if (remainingAttempts <= 0) panic()

            Toast.makeText(requireContext(), R.string.wrong_pin, Toast.LENGTH_LONG).show()
            binding!!.textViewPin.text = ""
        }
    }

    private fun panic() {
        Thread { OmsFileProvider.purgeTmp(requireContext()) }.start()

        val cryptographyManager = CryptographyManager()

        try {
            val aliasesEnum = cryptographyManager.keyStore!!.aliases()

            while (aliasesEnum.hasMoreElements()) {
                cryptographyManager.deleteKey(aliasesEnum.nextElement())
            }
        } catch (e: KeyStoreException) {
            throw RuntimeException(e)
        }

        preferences!!.edit() {

            if (preferences!!.contains(QRFragment.PROP_RECENT_ENTRIES)) remove(QRFragment.PROP_RECENT_ENTRIES)

            if (preferences!!.contains(QRFragment.PROP_PRESETS)) remove(QRFragment.PROP_PRESETS)

        }

        onPanic?.run()
    }

    private fun onDigit(v: View) {
        binding!!.textViewPin.append((v as Button).text)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Thread { OmsFileProvider.purgeTmp(requireContext()) }.start()
        if (this.onCancel != null) {
            requireContext().mainExecutor.execute(onCancel)
        }
    }
}