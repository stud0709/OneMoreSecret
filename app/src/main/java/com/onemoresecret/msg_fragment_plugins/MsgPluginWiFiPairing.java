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
import com.onemoresecret.crypto.RSAUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.ByteBuffer;
import java.security.KeyStoreException;
import java.util.Arrays;

public class MsgPluginWiFiPairing extends MessageFragmentPlugin<byte[]> {
    private static String TAG = MsgPluginWiFiPairing.class.getSimpleName();
    private static final String PROP_WIFI_PORT = "wifi_pairing_port";
    private static final long ttl_default = 12L * 3600_000L; //12 hours
    private static final int PORT_DEFAULT = 43189;


    public MsgPluginWiFiPairing(MessageFragment messageFragment, byte[] messageData) throws Exception {
        super(messageFragment);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
             var dataInputStream = new OmsDataInputStream(bais)) {

            //(1) Application ID - already read, not present here

            //(2) Request ID
            var requestId = dataInputStream.readString();

            //(3) RSA public key - PC
            var rsaPublicKeyComm = RSAUtils.restorePublicKey(dataInputStream.readByteArray());

            //(4) RSA privateKey to protect communication
            var rsaPrivateKeyComm = RSAUtils.restorePrivateKey(dataInputStream.readByteArray());

            //get IP address
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
            boolean notIP4 = true;
            for (var linkAddress : linkProperties.getLinkAddresses()) {
                var inetAddress = linkAddress.getAddress();
                if (!(inetAddress instanceof Inet4Address)) continue;
                notIP4 = false;
                var ipAddress = inetAddress.getAddress();
                Log.d(TAG, "IP: " + inetAddress.getHostAddress() + " = " + Arrays.toString(ipAddress));

                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    var port = preferences.getInt(PROP_WIFI_PORT, PORT_DEFAULT);

                    Log.d(TAG, "Port: " + port);

                    //(1) IP address as integer value
                    baos.write(ipAddress);

                    //(2) port
                    var iArr = ByteBuffer.allocate(4).putInt(port).array();

                    //copy only lower portion, ports range 0...65535
                    baos.write(Arrays.copyOfRange(iArr, 2, iArr.length));

                    var bArr = baos.toByteArray();

                    context.getMainExecutor().execute(() -> {
                        var responseCode = Base58.encode(bArr);
                        ((WiFiPairingFragment) getMessageView()).setData(
                                requestId,
                                responseCode,
                                () -> {
                                    ((OutputFragment) getOutputView()).setMessage(responseCode + "\n" /* Hit ENTER to close the pop-up */, "Response Code");
                                    ((MainActivity) context).setWiFiComm(
                                            new MainActivity.WiFiComm(ipAddress, port, rsaPrivateKeyComm, rsaPublicKeyComm, System.currentTimeMillis() + ttl_default));
                                });
                    });
                }
            }
            if (notIP4) throw new IOException("Unsupported network (not IP4)");
        }
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new WiFiPairingFragment();
        return messageView;
    }

    @Override
    public void showBiometricPromptForDecryption() throws KeyStoreException {
        //not used
    }
}
