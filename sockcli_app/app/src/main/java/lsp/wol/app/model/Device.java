package lsp.wol.app.model;

public class Device {
    public String name;
    public String macAddress;

    public Device() {
    }

    public Device(String name, String macAddress) {
        this.name = name;
        this.macAddress = macAddress;
    }
}
