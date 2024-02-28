package com.onemoresecret;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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
import com.onemoresecret.crypto.RSAUtils;
import com.onemoresecret.databinding.ActivityMainBinding;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private AppBarConfiguration appBarConfiguration;

    public record WiFiComm(byte[] ipAdr, int port, RSAPrivateKey privateKey,
                           RSAPublicKey publicKey) {
    }

    private WiFiComm wiFiComm = null;
    private ServerSocket serverSocket = null;
    private Socket socketWaitingForReply = null;
    private SharedPreferences preferences;

    public void setWiFiComm(WiFiComm wiFiComm) {
        destroyWiFiListener();
        this.wiFiComm = wiFiComm;
    }

    public WiFiComm getWiFiComm() {
        return wiFiComm;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new OmsUncaughtExceptionHandler(this));
        preferences = getPreferences(Context.MODE_PRIVATE);

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
                Log.w(TAG, "Socket waiting for reply not set or closed");
                return;
            }

            Log.d(TAG, String.format("Sending %s bytes via socket waiting for reply", data.length));

            try {
                var reply = MessageComposer.createRsaAesEnvelope(
                        wiFiComm.publicKey,
                        RSAUtils.getRsaTransformationIdx(preferences),
                        AESUtil.getKeyLength(preferences),
                        AESUtil.getAesTransformationIdx(preferences),
                        data);

                var outputStream = socketWaitingForReply.getOutputStream();

                outputStream.write(reply);
                outputStream.flush();

                Log.d(TAG, "Data successfully sent");
            } catch (Exception ex) {
                ex.printStackTrace();
                closeSocket = true;
            } finally {
                if (closeSocket) {
                    try {
                        socketWaitingForReply.close();
                    } catch (IOException ignored) {

                    } finally {
                        socketWaitingForReply = null;
                        Log.d(TAG, "Socket closed");
                    }
                }
            }
        }
    }

    public void destroyWiFiListener() {
        synchronized (MainActivity.class) {
            if (serverSocket == null) return;

            Log.d(TAG, "Destroying WiFi Listener...");
            if (!serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {
                }
            }
            serverSocket = null;
        }
    }

    public void startWiFiListener(Consumer<String> messageConsumer, Runnable onSuccess) {

        var thread = new Thread(() -> {
            var invalidateWiFiCommOnError = true;

            try {
                synchronized (MainActivity.class) {
                    if (socketWaitingForReply != null && !socketWaitingForReply.isClosed()) {
                        Log.d(TAG, "Closing socket waiting for reply...");
                        //If we have gotten here, the message processing was cancelled.
                        //Notify the client sending an empty (though valid) reply

                        sendReplyViaSocket(new byte[]{}, true);
                    }

                    destroyWiFiListener();

                    var wiFiComm = getWiFiComm();
                    if (wiFiComm == null) {
                        return;
                    }

                    Log.d(TAG, String.format("Starting WiFi Listener on %s:%s...", serverSocket.getInetAddress().getHostAddress(), wiFiComm.port));

                    serverSocket = new ServerSocket();
                }

                serverSocket.setReuseAddress(true); //get ready to reuse this socket
                serverSocket.bind(new InetSocketAddress(Inet4Address.getByAddress(wiFiComm.ipAdr), wiFiComm.port));

                invalidateWiFiCommOnError = false;
                onSuccess.run();

                while (!Thread.currentThread().isInterrupted()
                        && serverSocket != null
                        && !serverSocket.isClosed()) {
                    try {
                        onWiFiConnection(serverSocket.accept(), messageConsumer);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            } catch (Exception ex) {
                //server socket is broken
                ex.printStackTrace();
                if (invalidateWiFiCommOnError) {
                    //pairing configuration is not valid any more
                    setWiFiComm(null);

                    this.getMainExecutor().execute(() -> {
                        Toast.makeText(this,
                                Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()),
                                Toast.LENGTH_LONG).show();
                    });
                }
            } finally {
                synchronized (MainActivity.class) {
                    serverSocket = null;
                }
            }
            Log.d(TAG, "WiFi Listener has exited");
        });

        thread.setDaemon(true);
        thread.start();
    }

    private void onWiFiConnection(Socket socket, Consumer<String> messageConsumer) {
        Thread t = new Thread(() -> {
            Log.d(TAG, "Incoming socket connection");
            try {
                var dataInputStream = new OmsDataInputStream(socket.getInputStream());
                //a transaction consists of a request and a response. End of request is signalled by shutdownOutput on the socket
                var envelope = MessageComposer.readRsaAesEnvelope(dataInputStream);

                //stream has been shut down
                var encryptedMessage = dataInputStream.readByteArray();

                Log.d(TAG, String.format("Message has been received, %s bytes", encryptedMessage.length));

                // decrypt AES key
                var aesSecretKeyData = RSAUtils.process(Cipher.DECRYPT_MODE, getWiFiComm().privateKey,
                        envelope.rsaTransormation(), envelope.encryptedAesSecretKey());
                var aesSecretKey = new SecretKeySpec(aesSecretKeyData, "AES");

                // (7) AES-encrypted message
                var decryptedMessage = AESUtil.process(Cipher.DECRYPT_MODE, encryptedMessage, aesSecretKey,
                        new IvParameterSpec(envelope.iv()), envelope.aesTransformation());

                synchronized (MainActivity.class) {
                    if (serverSocket == null || serverSocket.isClosed())
                        return; //smth. already happened, this process is obsolete

                    //keep socket open, wait for the reply
                    socketWaitingForReply = socket;
                }

                messageConsumer.accept(MessageComposer.encodeAsOmsText(decryptedMessage));
            } catch (Exception ex) {
                ex.printStackTrace();
                this.getMainExecutor().execute(() -> {
                    Toast.makeText(this,
                            Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()),
                            Toast.LENGTH_LONG).show();
                });
                Log.d(TAG, "Closing socket");
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}