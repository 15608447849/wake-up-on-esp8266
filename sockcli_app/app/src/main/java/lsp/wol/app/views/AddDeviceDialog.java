package lsp.wol.app.views;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import lsp.wol.app.R;
import lsp.wol.app.model.Device;
import lsp.wol.app.model.DeviceEventCallback;
import lsp.wol.app.utils.DeviceSPUtil;

public class AddDeviceDialog  extends AlertDialog {
    // 输入控件
    private EditText etName;
    private EditText etMac;

    public AddDeviceDialog(Context context, String title, Device oldDevice, DeviceEventCallback callback) {
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
            String cleanText = cleanMacText(mac.toString());
            String formattedText = formatMacText(cleanText);
            if (formattedText.length() > 17) {
                formattedText = formattedText.substring(0, 17);
            }
            mac = formattedText;

            if (!isMacValid(mac)){
                Toast.makeText(getContext(), "MAC格式不正确", Toast.LENGTH_SHORT).show();
                return;
            }

            Device newDevice = new Device(name, mac);


            // 删除设备
            if (oldDevice!=null){
                if (oldDevice.macAddress.equals(newDevice.macAddress)
                        && oldDevice.name.equals(newDevice.name)){
                    return;
                }

                DeviceSPUtil.deleteDevice(context, oldDevice);
                Log.i(String.valueOf(R.string.app_name), "AddDeviceDialog: 删除设备: "+ oldDevice.name+" "+ oldDevice.macAddress);
            }

           // 保存设备

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

    /**
     * 清洗MAC文本：只保留十六进制字符，转大写
     * @param text 原始输入文本
     * @return 清洗后的纯十六进制大写字符串
     */
    private static String cleanMacText(String text) {
        if (TextUtils.isEmpty(text)) return "";
        // 正则匹配十六进制字符（0-9, a-f, A-F），其余过滤
        return text.replaceAll("[^0-9a-fA-F]", "")
                .toUpperCase();
    }
    /**
     * 格式化清洗后的MAC文本：每2个字符加分隔符
     * @param cleanText 清洗后的纯十六进制字符串
     * @return 格式化后的MAC字符串（如 AA:BB:CC）
     */
    private static String formatMacText(String cleanText) {
        StringBuilder sb = new StringBuilder();
        int length = cleanText.length();

        for (int i = 0; i < length; i++) {
            sb.append(cleanText.charAt(i));
            // 每2个字符加分隔符（最后一组不加）
            if ((i + 1) % 2 == 0 && i != length - 1) {
                sb.append(":");
            }
        }

        return sb.toString();
    }

    /**
     * 校验MAC地址是否合法（格式化后长度为17）
     * @param mac 格式化后的MAC字符串
     * @return true=合法，false=不合法
     */
    public static boolean isMacValid(String mac) {
        if (TextUtils.isEmpty(mac)) return false;
        // 正则校验：AA:BB:CC:DD:EE:FF 格式
        String macRegex = "^([0-9A-F]{2}:){5}[0-9A-F]{2}$";
        return mac.matches(macRegex);
    }

}
