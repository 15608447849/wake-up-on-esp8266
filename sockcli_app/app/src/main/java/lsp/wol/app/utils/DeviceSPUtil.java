package lsp.wol.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

import lsp.wol.app.model.Device;

public class DeviceSPUtil {
    private static final String SP_NAME = "device_info";
    private static final String KEY_DEVICE_LIST = "device_list";
    private static SharedPreferences sp;
    private static Gson gson = new Gson();

    // 初始化SP
    private static SharedPreferences getSP(Context context) {
        if (sp == null) {
            sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        }
        return sp;
    }
    /**
     * 保存设备列表
     */
    public static void saveDeviceList(Context context, List<Device> deviceList) {
        // 将List转为JSON字符串存储
        String json = gson.toJson(deviceList);
        getSP(context).edit().putString(KEY_DEVICE_LIST, json).apply();
    }

    /**
     * 读取设备列表
     */
    public static List<Device> getDeviceList(Context context) {
        String json = getSP(context).getString(KEY_DEVICE_LIST, "");
        if (json.isEmpty()) {
            return new ArrayList<>(); // 空列表，避免空指针
        }
        // JSON字符串转回List
        return gson.fromJson(json, new TypeToken<List<Device>>() {}.getType());
    }

    /**
     * 新增单个设备
     */
    public static void addDevice(Context context, Device device) {
        List<Device> list = getDeviceList(context);
        list.add(device);
        saveDeviceList(context, list);
    }

    /**
     * 删除指定MAC的设备
     */
    public static void deleteDevice(Context context, Device device) {
        List<Device> list = getDeviceList(context);
        list.removeIf(it -> it.name.equals(device.name)
                && it.macAddress.equals(device.macAddress) );
        saveDeviceList(context, list);
    }

}
