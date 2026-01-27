package com.onemoresecret.msg_fragment_plugins;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.util.Log;

import androidx.fragment.app.Fragment;

import com.onemoresecret.MainActivity;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.WiFiPairingFragment;
import com.onemoresecret.crypto.Base58;
import com.onemoresecret.crypto.RSAUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MsgPluginWiFiPairing extends MessageFragmentPlugin {
    private static final String TAG = MsgPluginWiFiPairing.class.getSimpleName();
    private static final long ttl_default = 12L * 3600_000L; //12 hours

    public record IpAndPort(byte[] ipAddress, int port, byte[] responseCode) {
    }

    public MsgPluginWiFiPairing(MessageFragment messageFragment, byte[] messageData) throws Exception {
        super(messageFragment);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
             var dataInputStream = new OmsDataInputStream(bais)) {

            //(1) Application ID - already read, not present here

            //(2) Request ID
            var requestId = dataInputStream.readString();

            //(3) RSA public key - PC
            var rsaPublicKeyMaterial = dataInputStream.readByteArray();

            //(4) RSA privateKey to protect communication
            var rsaPrivateKeyMaterial = dataInputStream.readByteArray();

            //get IP address
            var ipAndPort = getIpAndPort(context);

            context.getMainExecutor().execute(() -> {
                var responseCodeBase58 = Base58.encode(ipAndPort.responseCode);
                ((WiFiPairingFragment) getMessageView()).setData(
                        requestId,
                        responseCodeBase58,
                        () -> {
                            ((OutputFragment) getOutputView()).setMessage(responseCodeBase58 + "\n", "Response Code");
                            ((MainActivity) context).setWiFiComm(
                                    new MainActivity.WiFiComm(
                                            ipAndPort.ipAddress,
                                            ipAndPort.port,
                                            rsaPrivateKeyMaterial,
                                            System.currentTimeMillis() + ttl_default),
                                    rsaPrivateKeyMaterial);
                        });
            });
        }
    }

    public static IpAndPort getIpAndPort(Context context) throws IOException {
        var connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        var network = connectivityManager.getActiveNetwork();
        if (network == null) {
            throw new IOException(context.getString(R.string.no_network));
        }
        var networkCapabilities = connectivityManager.getNetworkCapabilities(network);
        if (networkCapabilities == null)
            throw new IOException(context.getString(R.string.not_on_wifi_network));
        var transportInfo = networkCapabilities.getTransportInfo();
        if (!(transportInfo instanceof WifiInfo)) {
            throw new IOException(context.getString(R.string.not_on_wifi_network));
        }
        var linkProperties = connectivityManager.getLinkProperties(network);
        if (linkProperties != null) {
            for (var linkAddress : linkProperties.getLinkAddresses()) {
                var inetAddress = linkAddress.getAddress();
                if (!(inetAddress instanceof Inet4Address)) continue;

                var ipAddress = inetAddress.getAddress();
                Log.d(TAG, "IP: " + inetAddress.getHostAddress() + " = " + Arrays.toString(ipAddress));

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     ServerSocket serverSocket = new ServerSocket(0)) {

                    var port = serverSocket.getLocalPort();

                    //(1) IP address as integer value
                    baos.write(ipAddress);

                    //(2) port
                    var iArr = ByteBuffer.allocate(4).putInt(port).array();

                    //copy only lower portion, ports range 0...65535
                    baos.write(Arrays.copyOfRange(iArr, 2, iArr.length));

                    var bArr = baos.toByteArray();

                    return new IpAndPort(ipAddress, port, bArr);
                }
            }
        }

        throw new IOException("Unsupported network (not IP4)");
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new WiFiPairingFragment();
        return messageView;
    }

    @Override
    public void showBiometricPromptForDecryption() {
        //not used
    }
}
