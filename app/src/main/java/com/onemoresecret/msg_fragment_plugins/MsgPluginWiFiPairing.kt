package com.onemoresecret.msg_fragment_plugins

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.util.Log
import androidx.fragment.app.Fragment
import com.onemoresecret.MainActivity
import com.onemoresecret.MessageFragment
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import com.onemoresecret.WiFiPairingFragment
import com.onemoresecret.crypto.Base58
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.Arrays

class MsgPluginWiFiPairing(messageFragment: MessageFragment, messageData: ByteArray) :
    MessageFragmentPlugin(messageFragment) {

    init {
        OmsDataInputStream(ByteArrayInputStream(messageData)).use { dataInputStream ->
            //(1) Application ID - already read, not present here

            //(2) Request ID
            val requestId = dataInputStream.readString()

            //(3) RSA public key - PC
            dataInputStream.readByteArray()

            //(4) RSA privateKey to protect communication
            val rsaPrivateKeyMaterial = dataInputStream.readByteArray()

            //get IP address
            val ipAndPort = getIpAndPort(context)

            context.mainExecutor.execute {
                val responseCodeBase58 = Base58.encode(ipAndPort.responseCode)
                (getMessageView() as WiFiPairingFragment).setData(
                    requestId,
                    responseCodeBase58,
                    Runnable {
                        (getOutputView() as OutputFragment).setMessage(responseCodeBase58 + "\n", "Response Code")
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
                )
            }
        }
    }

    override fun getMessageView(): Fragment {
        if (messageView == null) {
            messageView = WiFiPairingFragment()
        }
        return messageView
    }

    override fun showBiometricPromptForDecryption() {
        //not used
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

                            //(1) IP address as integer value
                            baos.write(ipAddress)

                            //(2) port
                            val iArr = ByteBuffer.allocate(4).putInt(port).array()

                            //copy only lower portion, ports range 0...65535
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
