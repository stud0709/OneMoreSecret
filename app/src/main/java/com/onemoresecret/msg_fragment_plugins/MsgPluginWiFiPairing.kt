package com.onemoresecret.msg_fragment_plugins

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.onemoresecret.MainActivity
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.R
import com.onemoresecret.composable.OutputScreen
import com.onemoresecret.composable.OutputViewModel
import com.onemoresecret.composable.WiFiPairingScreen
import com.onemoresecret.crypto.Base58
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.Arrays

class MsgPluginWiFiPairing(
    activity: FragmentActivity,
    messageData: ByteArray,
    hiddenState: MutableStateFlow<Boolean>,
    onNavigateBack: () -> Unit
) : MessageFragmentPlugin(activity, hiddenState, onNavigateBack) {

    private var requestId by mutableStateOf("")
    private var responseCode by mutableStateOf("")
    private var onStartClick: (() -> Unit)? by mutableStateOf(null)

    private val outputViewModel = OutputViewModel(preferences)

    init {
        OmsDataInputStream(ByteArrayInputStream(messageData)).use { dataInputStream ->
            requestId = dataInputStream.readString()
            dataInputStream.readByteArray()
            val rsaPrivateKeyMaterial = dataInputStream.readByteArray()

            val ipAndPort = getIpAndPort(context)

            context.mainExecutor.execute {
                responseCode = Base58.encode(ipAndPort.responseCode)
                onStartClick = {
                    outputViewModel.setMessage(responseCode, "Response Code")
                    (context as MainActivity).setWiFiComm(
                        MainActivity.WiFiComm(
                            ipAndPort.ipAddress,
                            ipAndPort.port,
                            rsaPrivateKeyMaterial,
                            System.currentTimeMillis() + TTL_DEFAULT
                        ),
                        rsaPrivateKeyMaterial
                    )
                }
            }
        }
    }

    @Composable
    override fun MessageView(hiddenState: Boolean) {
        WiFiPairingScreen(
            requestId = requestId,
            responseCode = if (hiddenState) "●".repeat(responseCode.length) else responseCode,
            onStartClick = onStartClick
        )
    }

    @Composable
    override fun TopBarActions() {

    }

    @Composable
    override fun OutputView() {
        OutputScreen(outputViewModel = outputViewModel)
    }

    override fun showBiometricPromptForDecryption() {
    }

    data class IpAndPort(val ipAddress: ByteArray, val port: Int, val responseCode: ByteArray)

    companion object {
        private const val TTL_DEFAULT = 12L * 3600_000L //12 hours
        private val TAG = MsgPluginWiFiPairing::class.java.simpleName

        @Throws(IOException::class)
        fun getIpAndPort(context: Context): IpAndPort {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val network = connectivityManager.activeNetwork
                ?: throw IOException(context.getString(R.string.no_network))
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                ?: throw IOException(context.getString(R.string.not_on_wifi_network))
            val transportInfo = networkCapabilities.transportInfo
            if (transportInfo !is WifiInfo) {
                throw IOException(context.getString(R.string.not_on_wifi_network))
            }

            val linkProperties = connectivityManager.getLinkProperties(network)
            if (linkProperties != null) {
                for (linkAddress in linkProperties.linkAddresses) {
                    val inetAddress = linkAddress.address
                    if (inetAddress !is Inet4Address) continue

                    val ipAddress = inetAddress.address
                    Log.d(TAG, "IP: " + inetAddress.hostAddress + " = " + Arrays.toString(ipAddress))

                    ByteArrayOutputStream().use { baos ->
                        ServerSocket(0).use { serverSocket ->
                            val port = serverSocket.localPort
                            baos.write(ipAddress)
                            val iArr = ByteBuffer.allocate(4).putInt(port).array()
                            baos.write(Arrays.copyOfRange(iArr, 2, iArr.size))
                            val bArr = baos.toByteArray()
                            return IpAndPort(ipAddress, port, bArr)
                        }
                    }
                }
            }

            throw IOException("Unsupported network (not IP4)")
        }
    }
}
