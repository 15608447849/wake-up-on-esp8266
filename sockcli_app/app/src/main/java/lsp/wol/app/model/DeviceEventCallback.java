package lsp.wol.app.model;

public interface DeviceEventCallback {
    void onChange(Device device);
    void wakeOnLan(Device device);
}
