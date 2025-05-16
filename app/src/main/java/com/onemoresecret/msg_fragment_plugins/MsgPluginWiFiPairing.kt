package com.onemoresecret.msg_fragment_plugins

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiInfo
import android.util.Log
import androidx.fragment.app.Fragment
import com.onemoresecret.MainActivity
import com.onemoresecret.MainActivity.WiFiComm
import com.onemoresecret.MessageFragment
import com.onemoresecret.OmsDataInputStream
import com.onemoresecret.OutputFragment
import com.onemoresecret.R
import com.onemoresecret.WiFiPairingFragment
import com.onemoresecret.crypto.Base58.encode
import com.onemoresecret.crypto.RSAUtils.restorePrivateKey
import com.onemoresecret.crypto.RSAUtils.restorePublicKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.security.KeyStoreException
import java.util.Arrays

class MsgPluginWiFiPairing(messageFragment: MessageFragment, messageData: ByteArray?) :
    MessageFragmentPlugin(messageFragment) {
    @JvmRecord
    data class IpAndPort(val ipAddress: ByteArray, val port: Int, val responseCode: ByteArray)

    init {
        ByteArrayInputStream(messageData).use { bais ->
            OmsDataInputStream(bais).use { dataInputStream ->

                //(1) Application ID - already read, not present here
                //(2) Request ID
                val requestId = dataInputStream.readString()

                //(3) RSA public key - PC
                val rsaPublicKeyComm = restorePublicKey(dataInputStream.readByteArray())

                //(4) RSA privateKey to protect communication
                val rsaPrivateKeyComm = restorePrivateKey(dataInputStream.readByteArray())

                //get IP address
                val ipAndPort = getIpAndPort(context)
                context.mainExecutor.execute {
                    val responseCodeBase58 = encode(ipAndPort.responseCode)
                    (getMessageView() as WiFiPairingFragment).setData(
                        requestId,
                        responseCodeBase58
                    ) {
                        (getOutputView() as OutputFragment).setMessage(
                            responseCodeBase58 + "\n",
                            "Response Code"
                        )
                        (context as MainActivity).setWiFiComm(
                            WiFiComm(
                                ipAndPort.ipAddress,
                                ipAndPort.port,
                                rsaPrivateKeyComm,
                                rsaPublicKeyComm,
                                System.currentTimeMillis() + ttl_default
                            )
                        )
                    }
                }
            }
        }
    }

    override fun getMessageView(): Fragment? {
        if (messageView == null) messageView = WiFiPairingFragment()
        return messageView
    }

    @Throws(KeyStoreException::class)
    override fun showBiometricPromptForDecryption() {
        //not used
    }

    companion object {
        private val TAG: String = MsgPluginWiFiPairing::class.java.simpleName
        private const val ttl_default = 12L * 3600000L //12 hours

        @Throws(IOException::class)
        fun getIpAndPort(context: Context): IpAndPort {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val network = connectivityManager.activeNetwork
                ?: throw IOException(context.getString(R.string.no_network))
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                ?: throw IOException(context.getString(R.string.not_on_wifi_network))
            val transportInfo = networkCapabilities.transportInfo as? WifiInfo
                ?: throw IOException(context.getString(R.string.not_on_wifi_network))
            val linkProperties = connectivityManager.getLinkProperties(network)
            if (linkProperties != null) {
                for (linkAddress in linkProperties.linkAddresses) {
                    val inetAddress = linkAddress.address as? Inet4Address ?: continue

                    val ipAddress = inetAddress.address
                    Log.d(
                        TAG,
                        "IP: " + inetAddress.hostAddress + " = " + ipAddress.contentToString()
                    )

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
