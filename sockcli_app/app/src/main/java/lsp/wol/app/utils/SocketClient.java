package lsp.wol.app.utils;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lsp.wol.app.R;
import lsp.wol.app.model.TcpData;

public class SocketClient {
    private static final String SERVER_HOST = "espsock.devtask.cn";
//    private static final String SERVER_HOST = "192.168.1.6";
    private static final int SERVER_PORT = 8080;
    private static Gson gson = new Gson();

    private String localIp = "";  // 存储本地IP
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private byte[] buffer = new byte[1024]; // 1KB缓冲区
    // 消息队列（线程安全）
    private final BlockingQueue<TcpData> sendQueue = new LinkedBlockingQueue<>();
    private volatile boolean isRunning = false;
    private long lastHeartbeatTime = 0;// 最后心跳时间
    private final Activity activity;

    public SocketClient(Activity activity) {
        this.activity = activity;
    }

    // 检查网络权限
    private boolean checkNetworkPermission() {
        ConnectivityManager cm = (ConnectivityManager)
                activity.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) {
            Log.i(String.valueOf(R.string.app_name), "SocketClient checkNetworkPermission: ConnectivityManager 获取失败");
            return false;
        }

        Network network = cm.getActiveNetwork();
        if (network == null) {
            Log.i(String.valueOf(R.string.app_name), "SocketClient checkNetworkPermission: ConnectivityManager 获取失败");
            return false;
        }

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(network);
        return capabilities != null &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    //开始连接 循环
    public void startConnect(){
        Thread thread = new Thread(() -> {
            while (true){
                try {
                    if (checkNetworkPermission()){
                        // 清理
                        cleanup();
                        // 连接
                        doConnect();
                        // 处理
                        connectionLoop();
                    }else {
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, "无网络连接权限", Toast.LENGTH_SHORT).show();
                        });
                    }
                    Thread.sleep(30000);
                } catch (Exception e) {
                    Log.e(String.valueOf(R.string.app_name), "SocketClient startConnect", e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void doConnect() {
        try {
            if (isRunning) return;

            Log.i(String.valueOf(R.string.app_name), "SocketClient doConnect: " + SERVER_HOST + ":" + SERVER_PORT);
            // 创建 TCP Socket
            socket = new Socket();
            // 1秒超时
            socket.setSoTimeout(1000);
            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true);
            // 服务器
            InetAddress address = InetAddress.getByName(SERVER_HOST);
            SocketAddress socketAddress = new InetSocketAddress(address, SERVER_PORT);

            // 5秒连接超时
            socket.connect(socketAddress, 5000);
            localIp = socket.getLocalAddress().getHostAddress();
            Log.i(String.valueOf(R.string.app_name), "SocketClient doConnect: TCP Socket 连接成功 本地IP="+localIp );

            // 获取输入流
            inputStream = socket.getInputStream();
            // 获取输出流
            outputStream = socket.getOutputStream();

            isRunning = true;

            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "已连接服务器:"+ SERVER_HOST, Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) {
            Log.e(String.valueOf(R.string.app_name), "SocketClient doConnect", e);
        }

    }



    /**
     * 清理资源
     */
    private void cleanup() {
        isRunning = false;
        try {
            if (inputStream != null) {
                inputStream.close();

            }
        } catch (Exception e) {
            Log.e(String.valueOf(R.string.app_name), "SocketClient cleanup", e);
        }finally {
            inputStream = null;
        }
        try {
            if (outputStream != null) {
                outputStream.close();

            }
        } catch (Exception e) {
            Log.e(String.valueOf(R.string.app_name), "SocketClient cleanup", e);
        }finally {
            outputStream = null;
        }
        try {
            if (socket != null) {
                socket.shutdownOutput();
                socket.shutdownInput();
                socket.close();

            }
        } catch (Exception e) {
            Log.e(String.valueOf(R.string.app_name), "SocketClient cleanup", e);
        }finally {
            socket = null;
        }
    }

    private void doHeartbeat() {
        if (isRunning && socket != null && !socket.isClosed()
                && System.currentTimeMillis() - lastHeartbeatTime > 20 * 1000L){

            TcpData tcpData = new TcpData();
            tcpData.cmd = "heartbeat";
            tcpData.data = String.valueOf(System.currentTimeMillis());
            tcpData.host = localIp;
            doSendBytes(gson.toJson(tcpData));
            lastHeartbeatTime = System.currentTimeMillis();
        }
    }

    private boolean doSendHandle() {
        // 判断是否存在需要发送的消息
        boolean flag = false;
        while (!sendQueue.isEmpty()){
            TcpData tcpData = sendQueue.poll();
            if (tcpData!=null){
                doSendBytes(gson.toJson(tcpData));
                flag = true;
            }
        }
        return flag;
    }

    private void doSendBytes(String message) {
        if (outputStream!=null && isRunning && !socket.isClosed() && socket.isConnected()) {
            try {
                byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
                outputStream.write(bytes);
                outputStream.flush();
                Log.i(String.valueOf(R.string.app_name), "SocketClient doSendBytes 发送消息= "+ message);

            } catch (SocketException e){
                Log.i(String.valueOf(R.string.app_name), "SocketClient doSendBytes SocketException= "+ e);
                cleanup();
            } catch (Exception e) {
                Log.e(String.valueOf(R.string.app_name), "SocketClient doSendBytes", e);
            }
        }
    }

    private void doReceive(){
        if (inputStream!=null && isRunning && socket != null && !socket.isClosed()){
            try{
                socket.setSoTimeout(300);
                // 阻塞读取，有数据时才会继续
                int bytesRead = inputStream.read(buffer);

                if (bytesRead > 0 ){
                    String message = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    Log.i(String.valueOf(R.string.app_name), "SocketClient doReceive: 收到消息= "+message );

                    if (bytesRead>1024){
                        Log.i(String.valueOf(R.string.app_name), "SocketClient doReceive 数据包超长= "+ message);
                    }else{
                        onProcessData(message);
                    }
                }
            } catch (SocketTimeoutException ig){
//                Log.e(String.valueOf(R.string.app_name), "SocketClient doReceive socket timeout", e);
            }
//            catch (SocketException e){
//                Log.i(String.valueOf(R.string.app_name), "SocketClient doReceive SocketException= "+ e);
//                cleanup();
//            }
            catch (Exception e){
                Log.e(String.valueOf(R.string.app_name), "SocketClient doReceive", e);
                cleanup();
            }
        }
    }

    private void connectionLoop() {
        int loopIndex = 0;
        while (isRunning && socket != null && !socket.isClosed() && socket.isConnected()){

            // 接收消息
            doReceive();
            // 非心跳消息发送
            if (!doSendHandle()){
                // 心跳发送
                doHeartbeat();
            }
            loopIndex++;

            //Log.i(String.valueOf(R.string.app_name), "SocketClient connectionLoop: index= " + loopIndex );
        }
    }



    public boolean sendTcpMessage(String cmd ,String data){
        if (!isRunning ) {
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "未连接服务器", Toast.LENGTH_SHORT).show();
            });
            return false;
        }

        TcpData tcpData = new TcpData();
        tcpData.cmd = cmd;
        tcpData.data = data == null? "" : data;
        tcpData.host = localIp;
        try {
            return sendQueue.offer(tcpData,100, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    private void onProcessData(String tcpDataJson) {
        TcpData tcpData = gson.fromJson(tcpDataJson, TcpData.class);
        if (tcpData.cmd.equals("heartbeat")){
            lastHeartbeatTime = System.currentTimeMillis();
        }else if (tcpData.cmd.equals("wol_rec_dev_size")) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog alert = new MaterialAlertDialogBuilder(activity)
                            .setMessage( tcpData.data+"台设备收到网络唤醒命令")
                            .setPositiveButton("确定", null)
                            .setCancelable(false)
                            .create();
                    alert.show();
                }
            });
        } else{
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, "收到TCP消息:"+ tcpData.cmd+" : " + tcpData.data, Toast.LENGTH_LONG).show();
            });
        }

    }
}
