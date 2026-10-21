package lpp.common;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Gửi UDP broadcast "DISCOVER_LPP" và gom các Server trả lời trong waitMs. */
public final class Discovery {
    public record Found(String host, int port, String name) {
        @Override public String toString() { return name + " (" + host + ":" + port + ")"; }
    }

    private Discovery() {}

    public static List<Found> find(int udpPort, int waitMs) throws IOException {
        Map<String, Found> found = new LinkedHashMap<>();
        byte[] msg = Protocol.DISCOVER.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket s = new DatagramSocket()) {
            s.setBroadcast(true);
            for (InetAddress target : targets()) {
                try {
                    s.send(new DatagramPacket(msg, msg.length, target, udpPort));
                } catch (IOException ignored) {
                    // một card mạng lỗi không được chặn các card khác
                }
            }
            long deadline = System.currentTimeMillis() + waitMs;
            byte[] buf = new byte[512];
            while (true) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) break;
                s.setSoTimeout((int) left);
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    s.receive(p);
                } catch (SocketTimeoutException e) {
                    break;
                }
                String[] f = Protocol.decode(new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8));
                if (f.length != 3 || !f[0].equals(Protocol.SERVER_REPLY)) continue;
                String host = p.getAddress().getHostAddress();
                try {
                    found.putIfAbsent(host, new Found(host, Integer.parseInt(f[1]), f[2]));
                } catch (NumberFormatException ignored) { }
            }
        }
        return new ArrayList<>(found.values());
    }

    /** 255.255.255.255 + broadcast của từng card mạng đang bật (máy có VPN/VirtualBox) + loopback (server cùng máy). */
    private static Set<InetAddress> targets() throws IOException {
        Set<InetAddress> targets = new LinkedHashSet<>();
        targets.add(InetAddress.getByName("255.255.255.255"));
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (InterfaceAddress a : ni.getInterfaceAddresses()) {
                if (a.getBroadcast() != null) targets.add(a.getBroadcast());
            }
        }
        targets.add(InetAddress.getLoopbackAddress());
        return targets;
    }
}
