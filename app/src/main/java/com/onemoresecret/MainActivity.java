package com.onemoresecret;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.onemoresecret.crypto.AESUtil;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.crypto.RSAUtil;
import com.onemoresecret.databinding.ActivityMainBinding;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

import javax.crypto.Cipher;

public class MainActivity extends AppCompatActivity {
    private static final String
            PROP_PRIVATE_KEY_COMM = "private_key_comm_base64",
            PROP_PUBLIC_KEY_COMM = "public_key_comm_base64",
            PROP_WIFI_COMM_EXP = "wifi_comm_exp",
            PROP_IPADDR = "wifi_comm_ip",
            PROP_PORT = "wifi_comm_port";
    private static final String TAG = MainActivity.class.getSimpleName();
    private AppBarConfiguration appBarConfiguration;

    public record WiFiComm(byte[] ipAdr, int port,
                           byte[] publicKey, long ts_expiry) {
        public boolean isValid() {
            return System.currentTimeMillis() < ts_expiry;
        }
        public byte[] getRsaPrivateKeyMaterial(SharedPreferences preferences) {
            return Base64.decode(preferences.getString(PROP_PRIVATE_KEY_COMM, ""), Base64.DEFAULT);
        }
    }

    private WiFiComm wiFiComm = null;
    private Runnable wiFiListenerShutdown = null;
    private Socket socketWaitingForReply = null;
    private SharedPreferences preferences;

    public void setWiFiComm(WiFiComm wiFiComm, byte[] rsaPrivateKeyMaterial) {
        synchronized (MainActivity.class) {
            destroyWiFiListener();

            this.wiFiComm = wiFiComm;

            if (preferences == null)
                preferences = getPreferences(MODE_PRIVATE);

            if (wiFiComm == null) {
                preferences.edit()
                        .remove(PROP_PRIVATE_KEY_COMM)
                        .remove(PROP_PUBLIC_KEY_COMM)
                        .remove(PROP_WIFI_COMM_EXP)
                        .remove(PROP_IPADDR)
                        .remove(PROP_PORT).commit();
            } else {
                preferences.edit()
                        .putString(PROP_PRIVATE_KEY_COMM, Base64.encodeToString(rsaPrivateKeyMaterial, Base64.DEFAULT))
                        .putString(PROP_PUBLIC_KEY_COMM, Base64.encodeToString(wiFiComm.publicKey, Base64.DEFAULT))
                        .putLong(PROP_WIFI_COMM_EXP, wiFiComm.ts_expiry)
                        .putString(PROP_IPADDR, Base64.encodeToString(wiFiComm.ipAdr, Base64.DEFAULT))
                        .putInt(PROP_PORT, wiFiComm.port).commit();
            }
        }
        invalidateOptionsMenu();
    }

    public boolean isWiFiCommSet() {
        synchronized (MainActivity.class) {
            return wiFiComm != null;
        }
    }

    private WiFiComm getWiFiComm() {
        synchronized (MainActivity.class) {
            if (wiFiComm == null && preferences.contains(PROP_WIFI_COMM_EXP)) {
                var publicKeyMaterial = Base64.decode(preferences.getString(PROP_PUBLIC_KEY_COMM, ""), Base64.DEFAULT);
                var ipaddrByte = Base64.decode(preferences.getString(PROP_IPADDR, ""), Base64.DEFAULT);
                var port = preferences.getInt(PROP_PORT, 0);
                var ts_expiry = preferences.getLong(PROP_WIFI_COMM_EXP, 0);

                wiFiComm = new WiFiComm(ipaddrByte, port, publicKeyMaterial, ts_expiry);
            }

            //it is possible that we have read outdated settings
            if (wiFiComm != null && !wiFiComm.isValid()) {
                setWiFiComm(null, null);
            }

            return wiFiComm;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new OmsUncaughtExceptionHandler(this));
        if (preferences == null)
            preferences = getPreferences(MODE_PRIVATE);

        var binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        var navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        assert navHostFragment != null;
        var navController = navHostFragment.getNavController();
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        //prevent screenshots
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        new Thread(() -> OmsFileProvider.purgeTmp(MainActivity.this)).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        OmsFileProvider.purgeTmp(MainActivity.this);
        destroyWiFiListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        destroyWiFiListener();
    }

    @Override
    public boolean onSupportNavigateUp() {
        var navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    /**
     * Entry point for the intent is {@link QRFragment}. When a new intent arrives, the app state is unclear.
     * Therefore we just restart the app.
     *
     * @param intent The new intent that was started for the activity.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
        startActivity(intent);
    }

    public void sendReplyViaSocket(byte[] data, boolean closeSocket) {
        synchronized (MainActivity.class) {
            if (socketWaitingForReply == null || socketWaitingForReply.isClosed()) {
                Log.w(TAG, "sendReplyViaSocket: Socket waiting for reply not set or closed");
                return;
            }
            Log.d(TAG, String.format("sendReplyViaSocket: Sending %s bytes via socket waiting for reply. Caller: %s",
                    data.length,
                    Thread.currentThread().getStackTrace()[3].toString()));

            try {
                var wiFiComm = getWiFiComm();
                var reply = MessageComposer.createRsaAesEnvelope(
                        wiFiComm.publicKey,
                        RSAUtil.getRsaTransformation(preferences),
                        AESUtil.getKeyLength(preferences),
                        AESUtil.getAesTransformation(preferences),
                        data);

                var outputStream = socketWaitingForReply.getOutputStream();

                outputStream.write(reply);
                outputStream.flush();

                Log.d(TAG, "sendReplyViaSocket: Data successfully sent");
            } catch (Exception ex) {
                Util.printStackTrace(ex);
                closeSocket = true;
            } finally {
                if (closeSocket) {
                    try {
                        socketWaitingForReply.close();
                    } catch (IOException ignored) {

                    } finally {
                        socketWaitingForReply = null;
                        Log.d(TAG, "sendReplyViaSocket: Socket closed");
                    }
                }
            }
        }
    }

    public void destroyWiFiListener() {
        synchronized (MainActivity.class) {
            if (wiFiListenerShutdown == null) return;

            Log.d(TAG, "destroyWiFiListener caller:" + Thread.currentThread().getStackTrace()[3].toString());
            wiFiListenerShutdown.run();
            wiFiListenerShutdown = null;
        }
    }

    public void startWiFiListener(Consumer<String> messageConsumer, Runnable onSuccess) {
        var wiFiListener = new Thread(() -> {
            try (var serverSocket = new ServerSocket()) {
                Log.d(TAG, String.format("startWiFiListener caller: %s, hash: %s",
                        Thread.currentThread().getStackTrace()[3].toString(),
                        Objects.hashCode(Thread.currentThread())));

                synchronized (MainActivity.class) {
                    if (socketWaitingForReply != null && !socketWaitingForReply.isClosed()) {
                        Log.d(TAG, "startWiFiListener: socketWaitingForReply found - sending empty reply");
                        //If we have gotten here, the message processing was cancelled.
                        //Notify the client sending an empty (though valid) reply

                        sendReplyViaSocket(new byte[]{}, true);
                    }

                    destroyWiFiListener();

                    var wiFiComm = getWiFiComm();
                    if (wiFiComm == null) {
                        return;
                    }

                    wiFiListenerShutdown = () -> {
                        try {
                            serverSocket.close();
                            Log.d(TAG, "Server socket closed");
                        } catch (IOException e) {
                            Util.printStackTrace(e);
                        }
                    };

                    Log.d(TAG, String.format("startWiFiListener: Starting WiFi Listener on %s:%s...",
                            Inet4Address.getByAddress(wiFiComm.ipAdr),
                            wiFiComm.port));

                    serverSocket.bind(
                            new InetSocketAddress(
                                    Inet4Address.getByAddress(wiFiComm.ipAdr),
                                    wiFiComm.port));

                    Log.d(TAG, String.format("startWiFiListener: bound to %s:%s...",
                            Inet4Address.getByAddress(wiFiComm.ipAdr),
                            wiFiComm.port));

                    onSuccess.run();
                }

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        onWiFiConnection(serverSocket.accept(), messageConsumer);
                    } catch (Exception ex) {
                        Util.printStackTrace(ex);
                        break;
                    }
                }
            } catch (Exception ex) {
                //server socket is broken
                Util.printStackTrace(ex);

                setWiFiComm(null, null);

                this.getMainExecutor().execute(() -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle(R.string.wifi_pairing_error)
                            .setMessage(R.string.wifi_pairing_error_message)
                            .setPositiveButton(android.R.string.ok, null);

                    AlertDialog dialog = builder.create();
                    dialog.show();
                });
            }

            Log.d(TAG, "startWiFiListener: WiFi Listener has exited");

        });

        wiFiListener.setDaemon(true);
        wiFiListener.start();
    }

    private void onWiFiConnection(Socket socket, Consumer<String> messageConsumer) {
        Thread t = new Thread(() -> {
            Log.d(TAG, "onWiFiConnection: Incoming socket connection");
            try {
                var dataInputStream = new OmsDataInputStream(socket.getInputStream());
                //a transaction consists of a request and a response. End of request is signalled by shutdownOutput on the socket
                var envelope = MessageComposer.readRsaAesEnvelope(dataInputStream);

                //stream has been shut down
                var encryptedMessage = dataInputStream.readByteArray();

                Log.d(TAG, String.format("onWiFiConnection: Message has been received, %s bytes", encryptedMessage.length));

                // decrypt AES key
                var rsaPrivateKeyMaterial =getWiFiComm().getRsaPrivateKeyMaterial(preferences);
                var aesSecretKeyData = RSAUtil.process(
                        Cipher.DECRYPT_MODE,
                        rsaPrivateKeyMaterial,
                        envelope.rsaTransformation,
                        envelope.encryptedAesSecretKey);

                Arrays.fill(rsaPrivateKeyMaterial, (byte)0);

                // (7) AES-encrypted message
                var decryptedMessage = AESUtil.process(
                        Cipher.DECRYPT_MODE,
                        encryptedMessage,
                        aesSecretKeyData,
                        envelope.iv,
                        envelope.aesTransformation);

                //wipe AES key data
                Arrays.fill(aesSecretKeyData, (byte)0);

                synchronized (MainActivity.class) {
                    //keep socket open, wait for the reply
                    socketWaitingForReply = socket;
                }

                messageConsumer.accept(MessageComposer.encodeAsOmsText(decryptedMessage));
            } catch (Exception ex) {
                Util.printStackTrace(ex);
                this.getMainExecutor().execute(() ->
                    Toast.makeText(this,
                            Objects.requireNonNullElse(
                                    ex.getMessage(),
                                    ex.getClass().getName()),
                            Toast.LENGTH_LONG).show());
                Log.d(TAG, "onWiFiConnection: Closing socket");
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        });

        t.start();
    }
}

