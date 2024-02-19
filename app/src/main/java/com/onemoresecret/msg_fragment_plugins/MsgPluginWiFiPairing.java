package com.onemoresecret.msg_fragment_plugins;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import androidx.fragment.app.Fragment;

import com.onemoresecret.MainActivity;
import com.onemoresecret.MessageFragment;
import com.onemoresecret.OmsDataInputStream;
import com.onemoresecret.OmsDataOutputStream;
import com.onemoresecret.OutputFragment;
import com.onemoresecret.R;
import com.onemoresecret.Util;
import com.onemoresecret.WiFiPairingFragment;
import com.onemoresecret.crypto.Base58;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Random;

public class MsgPluginWiFiPairing extends MessageFragmentPlugin<byte[]> {
    private static String TAG = MsgPluginWiFiPairing.class.getSimpleName();

    public MsgPluginWiFiPairing(MessageFragment messageFragment, byte[] messageData, int applicationId) throws Exception {
        super(messageFragment, messageData, applicationId);
    }

    @Override
    public Fragment getMessageView() {
        if (messageView == null) messageView = new WiFiPairingFragment();
        return messageView;
    }

    @Override
    protected void init(byte[] messageData, int _applicationId) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(messageData);
             var dataInputStream = new OmsDataInputStream(bais)) {

            //(1) Application ID - already read, not present here

            //(2) Request ID
            var requestId = dataInputStream.readString();

            //(3) RSA public key - PC
            var rsaPublicKeyPC = RSAUtils.restorePublicKey(dataInputStream.readByteArray());

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
                int port;
                //find a port
                try (ServerSocket ss = new ServerSocket(0);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     OmsDataOutputStream dataOutputStream = new OmsDataOutputStream(baos)) {

                    port = ss.getLocalPort();
                    Log.d(TAG, "Port: " + port);

                    //(1) IP address as integer value
                    var ipAdrInt = ByteBuffer.wrap(ipAddress).getInt();
                    dataOutputStream.writeInt(ipAdrInt);

                    //(2) port
                    dataOutputStream.writeUnsignedShort(port);

                    var bArr = baos.toByteArray();

                    context.getMainExecutor().execute(() -> {
                        var responseCode = Base58.encode(bArr);
                        ((WiFiPairingFragment) getMessageView()).setData(
                                requestId,
                                responseCode,
                                () -> {
                                    ((OutputFragment) getOutputView()).setMessage(responseCode, "Response Code");
                                    ((MainActivity) context).setWiFiComm(
                                            new MainActivity.WiFiComm(ipAddress, port, rsaPrivateKeyComm));
                                });
                    });
                }
            }
            if (notIP4) throw new IOException("Unsupported network (not IP4)");
        }
    }

    @Override
    public void showBiometricPromptForDecryption() throws KeyStoreException {
        //not used
    }
}
