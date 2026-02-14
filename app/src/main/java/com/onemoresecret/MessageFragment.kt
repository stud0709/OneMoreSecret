package com.onemoresecret

import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.navigation.fragment.NavHostFragment
import com.onemoresecret.Util.discardBackStack
import com.onemoresecret.Util.openUrl
import com.onemoresecret.Util.printStackTrace
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.databinding.FragmentMessageBinding
import com.onemoresecret.msg_fragment_plugins.MessageFragmentPlugin
import com.onemoresecret.msg_fragment_plugins.MsgPluginCryptoCurrencyAddress
import com.onemoresecret.msg_fragment_plugins.MsgPluginEncryptedFile
import com.onemoresecret.msg_fragment_plugins.MsgPluginEncryptedMessage
import com.onemoresecret.msg_fragment_plugins.MsgPluginKeyRequest
import com.onemoresecret.msg_fragment_plugins.MsgPluginTotp
import com.onemoresecret.msg_fragment_plugins.MsgPluginWiFiPairing
import java.util.Objects
import kotlin.Exception
import kotlin.IllegalArgumentException
import kotlin.String
import kotlin.Throws
import kotlin.byteArrayOf
import kotlin.concurrent.Volatile

class MessageFragment : Fragment() {
    private var binding: FragmentMessageBinding? = null
    @JvmField
    val hiddenState = MutableLiveData(true)
    private val menuProvider = MessageMenuProvider()

    @Volatile
    private var navBackIfPaused = true
    private var messageFragmentPlugin: MessageFragmentPlugin? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMessageBinding.inflate(inflater, container, false)

        return binding!!.getRoot()
    }

    override fun onPause() {
        super.onPause()
        if (!navBackIfPaused) return
        val navController = NavHostFragment.findNavController(this)
        if (navController.currentDestination != null
            && navController.currentDestination!!.id != R.id.MessageFragment
        ) {
            Log.d(TAG, String.format("Already navigating to %s", navController.currentDestination))
            return
        }
        Log.d(TAG, "onPause: going backward")
        discardBackStack(this)
    }

    override fun onResume() {
        super.onResume()

        //rearm
        navBackIfPaused = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(menuProvider)
        try {
            if (requireArguments().containsKey(QRFragment.ARG_MESSAGE)) {
                onMessage()
            } else if (requireArguments().containsKey(QRFragment.ARG_URI)) {
                onUri()
            }

            messageFragmentPlugin!!.getOutputView().setBeforePause {
                navBackIfPaused = false
            } /* disarm backward navigation */

            //insert message and output view into fragment
            this.getChildFragmentManager()
                .beginTransaction()
                .add(R.id.fragmentMessageView, messageFragmentPlugin!!.getMessageView())
                .add(R.id.fragmentOutputView, messageFragmentPlugin!!.getOutputView())
                .commit()

            //request authentication
            messageFragmentPlugin!!.showBiometricPromptForDecryption()
        } catch (ex: Exception) {
            printStackTrace(ex)
            Toast.makeText(
                context,
                Objects.requireNonNullElse<String?>(ex.message, ex.javaClass.getName()),
                Toast.LENGTH_LONG
            ).show()
            discardBackStack(this)
        }
    }

    @Throws(Exception::class)
    private fun onUri() {
        val uri = requireArguments().getParcelable<Parcelable?>("URI") as Uri?

        messageFragmentPlugin = MsgPluginEncryptedFile(this, uri)
    }

    @Throws(Exception::class)
    private fun onMessage() {
        val messageData = requireArguments().getByteArray(QRFragment.ARG_MESSAGE)
        val applicationId = requireArguments().getInt(QRFragment.ARG_APPLICATION_ID)

        when (applicationId) {
            MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED, MessageComposer.APPLICATION_ENCRYPTED_MESSAGE, MessageComposer.APPLICATION_ENCRYPTED_OTP -> messageFragmentPlugin =
                MsgPluginEncryptedMessage(this, messageData)

            MessageComposer.APPLICATION_KEY_REQUEST, MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST, MessageComposer.APPLICATION_KEY_REQUEST_PAIRING -> messageFragmentPlugin =
                MsgPluginKeyRequest(this, messageData)

            MessageComposer.APPLICATION_TOTP_URI_DEPRECATED, MessageComposer.APPLICATION_TOTP_URI -> messageFragmentPlugin =
                MsgPluginTotp(this, messageData)

            MessageComposer.APPLICATION_BITCOIN_ADDRESS -> messageFragmentPlugin =
                MsgPluginCryptoCurrencyAddress(this, messageData)

            MessageComposer.APPLICATION_WIFI_PAIRING -> messageFragmentPlugin =
                MsgPluginWiFiPairing(this, messageData)

            else -> throw IllegalArgumentException(getString(R.string.wrong_application) + " " + applicationId)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val activity = requireActivity() as MainActivity
        activity.removeMenuProvider(menuProvider)
        Thread { activity.sendReplyViaSocket(byteArrayOf(), true) }.start()
        messageFragmentPlugin!!.onDestroyView()
        binding = null
    }

    private inner class MessageMenuProvider : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
            menuInflater.inflate(R.menu.menu_message, menu)
        }

        override fun onPrepareMenu(menu: Menu) {
            super.onPrepareMenu(menu)
            //visibility switch will only be shown if there are active observers
            menu.findItem(R.id.menuItemMsgVisibility).isVisible = hiddenState.hasActiveObservers()
            menu.findItem(R.id.menuItemMsgVisibility)
                .setIcon(if (hiddenState.getValue() == true) R.drawable.baseline_visibility_24 else R.drawable.baseline_visibility_off_24)
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
            when (menuItem.itemId) {
                R.id.menuItemMsgVisibility -> {
                    hiddenState.value = false == hiddenState.getValue()
                    requireActivity().invalidateOptionsMenu()
                }
                R.id.menuItemMsgHelp -> {
                    openUrl(R.string.decrypted_message_md_url, requireContext())
                }
                else -> {
                    return false
                }
            }

            return true
        }
    }

    companion object {
        private val TAG: String = MessageFragment::class.java.getSimpleName()
    }
}