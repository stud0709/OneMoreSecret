package com.onemoresecret

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.openUrl
import com.onemoresecret.databinding.FragmentPinSetupBinding

class PinSetupFragment : Fragment() {
    private var binding: FragmentPinSetupBinding? = null
    private var preferences: SharedPreferences? = null
    private val menuProvider = PinMenuProvider()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPinSetupBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val preferences = requireActivity().getPreferences(Context.MODE_PRIVATE)

        val pinEnabled = preferences.getBoolean(PROP_PIN_ENABLED, false)
        binding!!.chkEnablePin.isChecked = pinEnabled
        setControls(pinEnabled)
        binding!!.chkEnablePin.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            setControls(
                isChecked
            )
        }
        binding!!.btnSavePinSettings.setOnClickListener { v: View? -> onSave() }
        binding!!.editTextPin.addTextChangedListener(textWatcherPin)
        binding!!.editTextRepeatPin.addTextChangedListener(textWatcherPin)
        binding!!.editTextPanicPin.addTextChangedListener(textWatcherPanicPin)
        binding!!.editTextRepeatPanicPin.addTextChangedListener(textWatcherPanicPin)

        //restoring values
        binding!!.editTextPin.setText(preferences.getString(PROP_PIN_VALUE, ""))
        binding!!.editTextRepeatPin.setText(preferences.getString(PROP_PIN_VALUE, ""))

        binding!!.editTextPanicPin.setText(preferences.getString(PROP_PANIC_PIN, ""))
        binding!!.editTextRepeatPanicPin.setText(preferences.getString(PROP_PANIC_PIN, ""))

        val maxAttempts = preferences.getInt(PROP_MAX_ATTEMPTS, 0)
        if (maxAttempts > 0) binding!!.editTextFailedAttempts.setText(maxAttempts.toString())

        val requestInterval = preferences.getLong(PROP_REQUEST_INTERVAL_MINUTES, 0)
        if (requestInterval > 0) binding!!.editTextRequestInterval.setText(requestInterval.toString())

        requireActivity().addMenuProvider(menuProvider)
    }

    private fun onSave() {
        val editor = preferences!!.edit()

        editor.putBoolean(PROP_PIN_ENABLED, binding!!.chkEnablePin.isChecked)

        if (binding!!.chkEnablePin.isChecked) {
            editor.putString(PROP_PIN_VALUE, binding!!.editTextPin.text.toString())

            if (binding!!.editTextRepeatPanicPin.text.toString().isEmpty()) {
                editor.remove(PROP_PANIC_PIN)
            } else {
                editor.putString(PROP_PANIC_PIN, binding!!.editTextPanicPin.text.toString())
            }

            val maxAttempts = if (binding!!.editTextFailedAttempts.text.toString()
                    .isEmpty()
            ) 0 else binding!!.editTextFailedAttempts.text.toString().toInt()
            if (maxAttempts > 0) {
                editor.putInt(PROP_MAX_ATTEMPTS, maxAttempts)
                editor.putInt(PROP_REMAINING_ATTEMPTS, maxAttempts)
            } else {
                editor.remove(PROP_MAX_ATTEMPTS)
                editor.remove(PROP_REMAINING_ATTEMPTS)
            }

            val request_interval = if (binding!!.editTextRequestInterval.text.toString()
                    .isEmpty()
            ) 0 else binding!!.editTextRequestInterval.text.toString().toInt()
            if (request_interval > 0) {
                editor.putLong(PROP_REQUEST_INTERVAL_MINUTES, request_interval.toLong())
            } else {
                editor.remove(PROP_REQUEST_INTERVAL_MINUTES)
            }
        } else {
            editor.remove(PROP_PIN_VALUE)
                .remove(PROP_PANIC_PIN)
                .remove(PROP_MAX_ATTEMPTS)
                .remove(PROP_REQUEST_INTERVAL_MINUTES)
        }

        editor.apply()

        requireContext().mainExecutor.execute {
            Toast.makeText(context, R.string.pin_preferences_saved, Toast.LENGTH_SHORT).show()
            discardBackStack(this@PinSetupFragment)
        }
    }

    private fun setControls(isChecked: Boolean) {
        validateForm()
        binding!!.editTextFailedAttempts.isEnabled = isChecked
        binding!!.editTextPin.isEnabled = isChecked
        binding!!.editTextPanicPin.isEnabled = isChecked
        binding!!.editTextRepeatPin.isEnabled = isChecked
        binding!!.editTextRequestInterval.isEnabled = isChecked
        binding!!.editTextRepeatPanicPin.isEnabled = isChecked
        binding!!.imgViewPinMatch.visibility =
            if (isChecked) View.VISIBLE else View.INVISIBLE
        binding!!.imgViewPanicMatch.visibility =
            if (isChecked) View.VISIBLE else View.INVISIBLE
    }

    /**
     * Check if the form data is valid and it is OK to save it
     */
    private fun validateForm() {
        binding!!.btnSavePinSettings.isEnabled =
            !binding!!.chkEnablePin.isChecked || (isPinValid && isPanicPinValid)
    }

    private val textWatcherPin: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable) {
            val drawable = ResourcesCompat.getDrawable(
                resources,
                if (this@PinSetupFragment.isPinValid) R.drawable.baseline_check_circle_24 else R.drawable.baseline_cancel_24,
                context!!.theme
            )

            binding!!.imgViewPinMatch.setImageDrawable(drawable)
            validateForm()
        }
    }

    private val textWatcherPanicPin: TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        }

        override fun afterTextChanged(s: Editable) {
            val drawable = ResourcesCompat.getDrawable(
                resources,
                if (this@PinSetupFragment.isPanicPinValid) R.drawable.baseline_check_circle_24 else R.drawable.baseline_cancel_24,
                context!!.theme
            )

            binding!!.imgViewPanicMatch.setImageDrawable(drawable)
            validateForm()
        }
    }

    private val isPinValid: Boolean
        get() {
            var b = !binding!!.editTextPin.text.toString().isEmpty() &&
                    (binding!!.editTextPin.text.toString()
                            == binding!!.editTextRepeatPin.text.toString())

            if (b && !binding!!.editTextPanicPin.text.toString().isEmpty() &&
                binding!!.editTextPanicPin.text.toString() == binding!!.editTextPin.text
                    .toString()
            ) {
                Toast.makeText(
                    requireContext(),
                    R.string.panic_pin_should_not_match_pin,
                    Toast.LENGTH_LONG
                ).show()
                b = false
            }

            return b
        }

    private val isPanicPinValid: Boolean
        get() {
            var b = (binding!!.editTextPanicPin.text.toString()
                    == binding!!.editTextRepeatPanicPin.text.toString())

            if (b && !binding!!.editTextPin.text.toString().isEmpty() &&
                binding!!.editTextPanicPin.text.toString() == binding!!.editTextPin.text
                    .toString()
            ) {
                Toast.makeText(
                    requireContext(),
                    R.string.panic_pin_should_not_match_pin,
                    Toast.LENGTH_LONG
                ).show()
                b = false
            }

            return b
        }

    private inner class PinMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_help, menu)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            if (menuItem.itemId == R.id.menuItemHelp) {
                openUrl(R.string.pin_setup_md_url, requireContext())
            } else {
                return false
            }

            return true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().removeMenuProvider(menuProvider)
        binding = null
    }

    companion object {
        const val PROP_PIN_ENABLED: String = "pin_enabled"
        const val PROP_PIN_VALUE: String = "pin_value"
        const val PROP_PANIC_PIN: String = "pin_panic"
        const val PROP_MAX_ATTEMPTS: String = "pin_max_attempts"
        const val PROP_REQUEST_INTERVAL_MINUTES: String = "pin_request_interval_minutes"
        const val PROP_REMAINING_ATTEMPTS: String = "pin_remaining_attempts"
    }
}