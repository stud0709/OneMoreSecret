package com.onemoresecret;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.onemoresecret.crypto.BTCAddress;
import com.onemoresecret.crypto.MessageComposer;
import com.onemoresecret.databinding.ActivityMainBinding;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.interfaces.RSAPrivateKey;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private AppBarConfiguration appBarConfiguration;
    public record WiFiComm(byte[] ipAdr, int port, RSAPrivateKey privateKey) {
    }
    private WiFiComm wiFiComm = null;
    public void setWiFiComm(WiFiComm wiFiComm) {
        this.wiFiComm = wiFiComm;
    }
    public WiFiComm getWiFiComm() {
        return wiFiComm;
    }
    private Thread wifiListener = null;
    private Socket socketWaitingForReply = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Thread.setDefaultUncaughtExceptionHandler(new OmsUncaughtExceptionHandler(this));

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

    public Socket getSocketWaitingForReply() {
        return socketWaitingForReply;
    }

    public void setSocketWaitingForReply(Socket socketWaitingForReply) {
        this.socketWaitingForReply = socketWaitingForReply;
    }

    public void destroyWiFiListener() {
        synchronized (MainActivity.class) {
            if (wifiListener == null) return;
            Log.d(TAG, "Destroying WiFi Listener...");
            wifiListener.interrupt();
            wifiListener = null;
        }
    }

    public void enableWiFiListener(Consumer<String> messageConsumer) {

        synchronized (MainActivity.class) {
            var socket = getSocketWaitingForReply();
            if (socket != null && !socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                } finally {
                    setSocketWaitingForReply(null);
                }
            }

            destroyWiFiListener();

            wifiListener = new Thread(() -> {

                var wiFiComm = getWiFiComm();
                if (wiFiComm == null) return;

                Log.d(TAG, "Starting WiFi Listener...");

                while (!Thread.currentThread().isInterrupted()) {
                    try (ServerSocket serverSocket = new ServerSocket(wiFiComm.port())) {
                        while (!Thread.currentThread().isInterrupted()) {
                            try {
                                onWiFiConnection(serverSocket.accept(), messageConsumer);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                Log.d(TAG, "WiFi Listener has exited");
            });

            wifiListener.setDaemon(true);
            wifiListener.start();
        }
    }

    private void onWiFiConnection(Socket socket, Consumer<String> messageConsumer) {
        Thread t = new Thread(() -> {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                //a transaction consists of a request and a response. End of request is signalled by shutdownOutput on the socket
                var bArr = new byte[1024];
                int cnt;

                while ((cnt = socket.getInputStream().read(bArr)) != -1) {
                    baos.write(bArr, 0, cnt);
                }

                //stream has been shut down
                var msgBytes = baos.toByteArray();
                var text = new String(msgBytes);
                if (MessageComposer.decode(text) == null) {
                    Log.d(TAG, "Received a valid message via WiFi");
                    //it is an OMS message
                    synchronized (MainActivity.class) {
                        if (wifiListener == null)
                            return; //smth. already happened, this process is obsolete
                        setSocketWaitingForReply(socket);
                        destroyWiFiListener();
                    }
                    messageConsumer.accept(text);
                } else {
                    Log.d(TAG, String.format("%d bytes received via WiFi, not a valid message", msgBytes.length));
                    socket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                if (!socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}