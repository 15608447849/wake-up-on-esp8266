package lsp.wol.app.viewholder;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import lsp.wol.app.R;
import lsp.wol.app.model.Device;
import lsp.wol.app.model.DeviceEventCallback;
import lsp.wol.app.utils.DeviceSPUtil;
import lsp.wol.app.utils.WolSender;
import lsp.wol.app.views.AddDeviceDialog;

public class DeviceItemViewHolder extends RecyclerView.ViewHolder {
    private Device device;

    private final TextView deviceName;
    private final TextView deviceMacAddress;
    private final Button editButton;
    private final Button deleteButton;
    private final Button sendWolButtonEsp8266;
    private final Button sendWolButtonLan;
    private final DeviceEventCallback callback;

    public DeviceItemViewHolder(@NonNull View itemView, DeviceEventCallback callback) {
        super(itemView);
        this.callback = callback;
        deviceName = itemView.findViewById(R.id.device_name);
        deviceMacAddress = itemView.findViewById(R.id.device_mac);
        editButton = itemView.findViewById(R.id.edit);
        deleteButton = itemView.findViewById(R.id.delete);
        sendWolButtonEsp8266 = itemView.findViewById(R.id.send_wol_esp8266);
        sendWolButtonLan = itemView.findViewById(R.id.send_wol_lan);
        initOnEvent();
    }

    private void initOnEvent() {
        deleteButton.setOnClickListener(view -> {
            Log.i(String.valueOf(R.string.app_name), "onClick: 删除");
            DeviceSPUtil.deleteDevice(view.getContext(),device);
            callback.onChange(device);
        });

        editButton.setOnClickListener(view -> {
            Log.i(String.valueOf(R.string.app_name), "onClick: 编辑");
            // 打开设备添加弹窗
            new AddDeviceDialog(view.getContext(),"修改设备",device, callback);
        });

        sendWolButtonEsp8266.setOnClickListener(view -> {
            // 网络唤醒
            Log.i(String.valueOf(R.string.app_name), "onClick: 网络唤醒 esp8266");
            callback.wakeOnLan(device);
            //Toast.makeText(view.getContext(),device.macAddress+" -> ESP8266设备",Toast.LENGTH_SHORT).show();
        });

        sendWolButtonLan.setOnClickListener(view -> {
            // 网络唤醒
            Log.i(String.valueOf(R.string.app_name), "onClick: 网络唤醒 lan");
            WolSender.sendMagicPacket(device);
            Toast.makeText(view.getContext(),device.macAddress+" -> LAN: Magic Packet ",Toast.LENGTH_SHORT).show();
        });
    }

    public synchronized void buildDevice(Device device) {
        this.device = device;
        deviceName.setText(device.name);
        deviceMacAddress.setText(device.macAddress);
    }


}
