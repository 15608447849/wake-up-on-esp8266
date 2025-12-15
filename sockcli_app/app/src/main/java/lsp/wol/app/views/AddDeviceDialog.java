package lsp.wol.app.views;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import lsp.wol.app.R;
import lsp.wol.app.model.Device;
import lsp.wol.app.model.DeviceChangeCallback;
import lsp.wol.app.utils.DeviceSPUtil;

public class AddDeviceDialog  extends AlertDialog {
    // 输入控件
    private EditText etName;
    private EditText etMac;

    public AddDeviceDialog(Context context, String title, Device oldDevice, DeviceChangeCallback callback) {
        super(context);
        // 加载对话框布局
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_device, null);
        etName = dialogView.findViewById(R.id.etName);
        etMac = dialogView.findViewById(R.id.etMac);

        setTitle(title);
        setView(dialogView);

        // 确定逻辑
        // 确定按钮逻辑
        setButton(BUTTON_POSITIVE, "保存", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String mac = etMac.getText().toString().trim();

            // 输入校验
            if (name.isEmpty() || mac.isEmpty()) {
                Toast.makeText(getContext(), "名称和MAC不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 输入的mac自动变成 aa:bb:cc 大写格式

            // 删除设备
            if (oldDevice!=null){
                DeviceSPUtil.deleteDevice(context, oldDevice);
                Log.i(String.valueOf(R.string.app_name), "AddDeviceDialog: 添加设备: "+ oldDevice.name+" "+ oldDevice.macAddress);
            }

           // 保存设备
            Device newDevice = new Device(name, mac);
            DeviceSPUtil.addDevice(context, new Device(name,mac));
            Log.i(String.valueOf(R.string.app_name), "AddDeviceDialog: 添加设备: "+ newDevice.name+" "+ newDevice.macAddress);
            callback.onChange(newDevice);

            // 关闭对话框
            dismiss();
        });

        // 取消按钮逻辑
        setButton(BUTTON_NEGATIVE, "取消", (dialog, which) -> {
            // 关闭对话框
            dismiss();
        });

        if (oldDevice!=null){
            etName.setText(oldDevice.name);
            etMac.setText(oldDevice.macAddress);
        }
        setCancelable(false);

        show();

    }


}
