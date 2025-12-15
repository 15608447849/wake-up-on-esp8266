package lsp.wol.app.viewholder;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import lsp.wol.app.R;
import lsp.wol.app.model.Device;
import lsp.wol.app.model.DeviceChangeCallback;
import lsp.wol.app.utils.DeviceSPUtil;
import lsp.wol.app.views.AddDeviceDialog;

public class DeviceItemViewHolder extends RecyclerView.ViewHolder {
    private Device device;

    private final TextView deviceName;
    private final TextView deviceMacAddress;
    private final Button editButton;
    private final Button deleteButton;
    private final Button sendWolButton;
    private final DeviceChangeCallback callback;

    public DeviceItemViewHolder(@NonNull View itemView, DeviceChangeCallback callback) {
        super(itemView);
        this.callback = callback;
        deviceName = itemView.findViewById(R.id.device_name);
        deviceMacAddress = itemView.findViewById(R.id.device_mac);
        editButton = itemView.findViewById(R.id.edit);
        deleteButton = itemView.findViewById(R.id.delete);
        sendWolButton = itemView.findViewById(R.id.send_wol);
        initOnEvent();
    }

    private void initOnEvent() {
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(String.valueOf(R.string.app_name), "onClick: 删除");
                DeviceSPUtil.deleteDevice(view.getContext(),device);
                callback.onChange(device);
            }
        });

        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = view.getContext();
                // 进入编辑页
                Log.i(String.valueOf(R.string.app_name), "onClick: 编辑");
                // 打开设备添加弹窗
                new AddDeviceDialog(view.getContext(),"修改设备",device, new DeviceChangeCallback() {
                    @Override
                    public void onChange(Device device) {
                         callback.onChange(device);
                    }
                });
            }
        });

        sendWolButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Context context = view.getContext();
                // 网络唤醒
                Log.i(String.valueOf(R.string.app_name), "onClick: 网络唤醒");

            }
        });
    }

    public synchronized void buildDevice(Device device) {
        this.device = device;
        deviceName.setText(device.name);
        deviceMacAddress.setText(device.macAddress);
    }


}
