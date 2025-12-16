package lsp.wol.app.utils;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BroadcastHelper {

    private static final List<String> INTERFACE_LIST = List.of("wlan", "eth", "tun");

    private boolean isAllowedInterfaceName(NetworkInterface networkInterface) {
        return INTERFACE_LIST.stream().anyMatch(interfaceName -> networkInterface.getName().startsWith(interfaceName));
    }

    protected Enumeration<NetworkInterface> getNetworkInterfaces() {
        try {
            return NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            return Collections.emptyEnumeration();
        }
    }


    public final Optional<InetAddress> getBroadcastAddress() {
        return Collections.list(getNetworkInterfaces()).stream()
                .filter(this::isAllowedInterfaceName)
                .map(NetworkInterface::getInterfaceAddresses)
                .flatMap(Collection::stream)
                .map(InterfaceAddress::getBroadcast)
                .filter(Objects::nonNull)
                .filter(addr -> addr instanceof Inet4Address)
                .findFirst();
    }



}
