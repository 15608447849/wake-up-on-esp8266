package lsp.wol.app.utils;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;

class PacketBuilder {

    static DatagramPacket buildMagicPacket(String broadcastAddress, String macAddress, int port) throws UnknownHostException {

        byte[] macBytes = getMacBytes(macAddress);

        // Packet is 6 times 0xff, 16 times MAC Address of target and 0, 4 or 6 character password
        byte[] bytes = new byte[6 + (16 * macBytes.length)];

        // Append 6 times 0xff
        for (int i = 0; i < 6; i++) {
            bytes[i] = (byte) 0xff;
        }

        // Append MAC address 16 times
        for (int i = 0; i < 16; i++) {
            appendMacAddress(macBytes, bytes, i);
        }

        InetAddress address = InetAddress.getByName(broadcastAddress);
        return new DatagramPacket(bytes, bytes.length, address, port);
    }

    private static void appendMacAddress(byte[] macBytes, byte[] bytes, int iteration) {
        System.arraycopy(macBytes, 0, bytes, (iteration + 1) * 6, macBytes.length);
    }

    private static byte[] getMacBytes(String macStr) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = macStr.split("(\\:|\\-)");
        if (hex.length != 6) {
            throw new IllegalArgumentException("Invalid MAC address.");
        }
        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex digit in MAC address.");
        }
        return bytes;
    }

}
