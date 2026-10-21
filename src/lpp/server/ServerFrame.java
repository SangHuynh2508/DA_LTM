package lpp.server;

import lpp.common.Protocol;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.function.Consumer;

/** Chương trình Server: mở TCP 5000 + UDP 5001, hiện log các thông điệp nhận được. */
public final class ServerFrame {
    public static void main(String[] args) throws IOException {
        JTextArea area = new JTextArea(25, 90);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        Consumer<String> log = msg -> {
            String line = LocalTime.now().format(fmt) + "  " + msg;
            System.out.println(line);
            SwingUtilities.invokeLater(() -> area.append(line + "\n"));
        };

        String name = InetAddress.getLocalHost().getHostName();
        try {
            new LppServer(log, Protocol.TIMEOUT_MS).start(Protocol.TCP_PORT);
            Thread udp = new Thread(new DiscoveryResponder(Protocol.UDP_PORT, Protocol.TCP_PORT, name, log), "discovery");
            udp.setDaemon(true);
            udp.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Không mở được cổng " + Protocol.TCP_PORT + "/" + Protocol.UDP_PORT
                    + ": " + e.getMessage(), "LPP Server", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("LPP Server – " + name);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(new JScrollPane(area));
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
        log.accept("Server chạy: TCP " + Protocol.TCP_PORT + ", UDP " + Protocol.UDP_PORT);
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                if (a instanceof Inet4Address) log.accept("IP " + ni.getDisplayName() + ": " + a.getHostAddress());
            }
        }
    }
}
