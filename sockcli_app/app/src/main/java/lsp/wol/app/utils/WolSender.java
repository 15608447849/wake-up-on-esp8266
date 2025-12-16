package lsp.wol.app.utils;

import android.util.Log;



import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import lsp.wol.app.R;
import lsp.wol.app.model.Device;


public class WolSender {

    public static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    private static void sendPacket(String broadcastAddress,String macAddress) {
        if (broadcastAddress==null || broadcastAddress.isEmpty()) {
            return;
        }

        try {
            Log.i(String.valueOf(R.string.app_name), "sendMagicPacket: "+broadcastAddress+" -> "+ macAddress);
            DatagramPacket packet = PacketBuilder.buildMagicPacket(broadcastAddress, macAddress, 9);
            DatagramSocket socket = new DatagramSocket();
            socket.send(packet);
            socket.close();
        } catch (Exception e) {
            Log.e(String.valueOf(R.string.app_name), "sendMagicPacket", e);
        }
    }

    public static void sendMagicPacket(Device device) {
        Runnable sendWolRunnable = new Runnable() {
            @Override
            public void run() {
                new BroadcastHelper().getBroadcastAddress().ifPresent(inetAddress ->
                        sendPacket(inetAddress.getHostAddress(), device.macAddress));
                sendPacket("255.255.255.255", device.macAddress);
            }
        };
        EXECUTOR.execute(sendWolRunnable);
    }

}
