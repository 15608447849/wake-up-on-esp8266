package lsp.wol.app;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import lsp.wol.app.model.Device;
import lsp.wol.app.utils.DeviceSPUtil;
import lsp.wol.app.utils.SocketClient;
import lsp.wol.app.viewholder.DeviceListAdapter;
import lsp.wol.app.views.AddDeviceDialog;
import lsp.wol.app.model.DeviceEventCallback;

public class MainActivity extends AppCompatActivity{


    private DeviceListAdapter mAdapter;
    private final SocketClient socketClient = new SocketClient(this);
    private final DeviceEventCallback deviceChangeCallback = new DeviceEventCallback() {
        @Override
        public void onChange(Device device) {
            // 刷新列表
           if (mAdapter!=null)
               mAdapter.addDevices(DeviceSPUtil.getDeviceList(MainActivity.this));
        }

        @Override
        public void wakeOnLan(Device device) {
            boolean isSend = socketClient.sendTcpMessage("forward",device.macAddress);
            if (!isSend){
                Toast.makeText(MainActivity.this, "网络唤醒消息发送失败", Toast.LENGTH_SHORT).show();
                // 尝试局域网广播

            }
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 适配内边距，避开状态栏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 设置顶部内边距，等于状态栏高度
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("设备列表");

        RecyclerView mRecyclerView = findViewById(R.id.recyclerView);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mRecyclerView.setItemAnimator(new androidx.recyclerview.widget.DefaultItemAnimator());
        mAdapter = new DeviceListAdapter(this, deviceChangeCallback);
        mRecyclerView.setAdapter(mAdapter);
        // 初始化数据
        mAdapter.addDevices(DeviceSPUtil.getDeviceList(MainActivity.this));

        // 浮动按钮
        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(view -> {
            // 打开设备添加弹窗
            new AddDeviceDialog(view.getContext(),"添加设备",null,deviceChangeCallback);
        });
        Log.i(String.valueOf(R.string.app_name), "onCreate: 完成");

        socketClient.startConnect();

    }


}