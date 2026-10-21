package lpp.server;

import lpp.common.Protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Trả lời gói UDP "DISCOVER_LPP" để Client trong LAN tự tìm thấy Server. */
public final class DiscoveryResponder implements Runnable {
    private final DatagramSocket socket;
    private final byte[] reply;
    private final Consumer<String> log;

    public DiscoveryResponder(int udpPort, int tcpPort, String serverName, Consumer<String> log) throws IOException {
        socket = new DatagramSocket(udpPort);
        reply = Protocol.encode(Protocol.SERVER_REPLY, String.valueOf(tcpPort), serverName).getBytes(StandardCharsets.UTF_8);
        this.log = log;
    }

    public int port() { return socket.getLocalPort(); }

    @Override public void run() {
        byte[] buf = new byte[512];
        while (!socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);
                String msg = new String(p.getData(), 0, p.getLength(), StandardCharsets.UTF_8).trim();
                if (!msg.equals(Protocol.DISCOVER)) continue;
                socket.send(new DatagramPacket(reply, reply.length, p.getSocketAddress()));
                log.accept("UDP: trả lời tìm server cho " + p.getSocketAddress());
            } catch (IOException e) {
                log.accept("UDP lỗi: " + e.getMessage());
            }
        }
    }
}
