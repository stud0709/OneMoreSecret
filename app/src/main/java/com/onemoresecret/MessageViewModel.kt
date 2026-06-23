package com.onemoresecret

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.onemoresecret.crypto.MessageComposer
import com.onemoresecret.msg_fragment_plugins.MessageFragmentPlugin
import com.onemoresecret.msg_fragment_plugins.MsgPluginCryptoCurrencyAddress
import com.onemoresecret.msg_fragment_plugins.MsgPluginEncryptedFile
import com.onemoresecret.msg_fragment_plugins.MsgPluginEncryptedMessage
import com.onemoresecret.msg_fragment_plugins.MsgPluginKeyRequest
import com.onemoresecret.msg_fragment_plugins.MsgPluginTotp
import com.onemoresecret.msg_fragment_plugins.MsgPluginWiFiPairing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MessageViewModel(
    applicationId: Int?,
    messageData: ByteArray?,
    uri: Uri?,
    activity: FragmentActivity,
    onNavigateBack: () -> Unit
) : ViewModel() {

    private val _hiddenState = MutableStateFlow(true)
    val hiddenState = _hiddenState.asStateFlow()

    val plugin: MessageFragmentPlugin?

    init {
        plugin = if (uri != null) {
            MsgPluginEncryptedFile(activity, uri, onNavigateBack)
        } else if (messageData != null && applicationId != null) {
            when (applicationId) {
                MessageComposer.APPLICATION_ENCRYPTED_MESSAGE_DEPRECATED,
                MessageComposer.APPLICATION_ENCRYPTED_MESSAGE,
                MessageComposer.APPLICATION_ENCRYPTED_OTP ->
                    MsgPluginEncryptedMessage(activity, messageData, onNavigateBack)

                MessageComposer.APPLICATION_KEY_REQUEST,
                MessageComposer.APPLICATION_OMS4WEB_CALLBACK_REQUEST,
                MessageComposer.APPLICATION_KEY_REQUEST_PAIRING ->
                    MsgPluginKeyRequest(activity, messageData, onNavigateBack)

                MessageComposer.APPLICATION_TOTP_URI_DEPRECATED,
                MessageComposer.APPLICATION_TOTP_URI ->
                    MsgPluginTotp(activity, messageData, onNavigateBack)

                MessageComposer.APPLICATION_BITCOIN_ADDRESS ->
                    MsgPluginCryptoCurrencyAddress(activity, messageData, onNavigateBack)

                MessageComposer.APPLICATION_WIFI_PAIRING ->
                    MsgPluginWiFiPairing(activity, messageData, onNavigateBack)

                else -> throw IllegalArgumentException("Wrong application id: $applicationId")
            }
        } else {
            null
        }
    }

    fun toggleVisibility() {
        _hiddenState.value = !_hiddenState.value
    }

    override fun onCleared() {
        super.onCleared()
        plugin?.onDestroyView()
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(
        private val applicationId: Int?,
        private val messageData: ByteArray?,
        private val uri: Uri?,
        private val activity: FragmentActivity,
        private val onNavigateBack: () -> Unit
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MessageViewModel::class.java)) {
                return MessageViewModel(applicationId, messageData, uri, activity, onNavigateBack) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
