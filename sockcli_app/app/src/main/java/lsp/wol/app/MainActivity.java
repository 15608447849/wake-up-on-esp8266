package lsp.wol.app;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import lsp.wol.app.model.Device;
import lsp.wol.app.utils.DeviceSPUtil;
import lsp.wol.app.viewholder.DeviceListAdapter;
import lsp.wol.app.views.AddDeviceDialog;
import lsp.wol.app.model.DeviceChangeCallback;

public class MainActivity extends AppCompatActivity{


    private DeviceListAdapter mAdapter;

    private DeviceChangeCallback deviceChangeCallback = new DeviceChangeCallback() {
        @Override
        public void onChange(Device device) {
            // 刷新列表
           if (mAdapter!=null)
               mAdapter.addDevices(DeviceSPUtil.getDeviceList(MainActivity.this));
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

//        // 初始化数据
//        deviceList.add(new Device("设备1", "00:1A:2B:3C:4D:5E"));
//        deviceList.add(new Device("设备2", "00:1A:2B:3C:4D:5F"));
//        deviceList.add(new Device("设备3", "00:1A:2B:3C:4D:6F"));
//        deviceList.add(new Device("设备4", "00:1A:2B:3C:4D:7F"));
//        deviceList.add(new Device("设备5", "00:1A:2B:3C:4D:8F"));
//        deviceList.add(new Device("设备6", "00:1A:2B:3C:4D:9F"));
//        deviceList.add(new Device("设备7", "00:1A:2B:3C:4D:10F"));
//        deviceList.add(new Device("设备8", "00:1A:2B:3C:4D:11F"));
        // 添加数据
//        mAdapter.addDevices(deviceList);

        mAdapter.addDevices(DeviceSPUtil.getDeviceList(MainActivity.this));


        FloatingActionButton fabAdd = findViewById(R.id.fabAdd);
        fabAdd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 打开设备添加弹窗
                new AddDeviceDialog(view.getContext(),"添加设备",null,deviceChangeCallback);

            }
        });
        Log.i(String.valueOf(R.string.app_name), "onCreate: 完成");

    }


}