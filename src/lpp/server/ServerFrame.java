package lpp.server;

import lpp.common.Protocol;
import lpp.common.Ui;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Chương trình Server: mở TCP 5000 + UDP 5001, hiện các địa chỉ IP và log các thông điệp nhận được. */
public final class ServerFrame {
    public static void main(String[] args) throws IOException {
        Ui.install();
        LogPane logPane = new LogPane();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        Consumer<String> log = msg -> {
            String time = LocalTime.now().format(fmt);
            System.out.println(time + "  " + msg);
            SwingUtilities.invokeLater(() -> logPane.append(time, msg));
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

        List<String[]> ips = addresses();
        SwingUtilities.invokeLater(() -> {
            JFrame f = window(name, ips, logPane);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
        log.accept("Server chạy: TCP " + Protocol.TCP_PORT + ", UDP " + Protocol.UDP_PORT);
        for (String[] a : ips) log.accept("IP " + a[1] + ": " + a[0]);
    }

    /** Các địa chỉ IPv4 của máy: {địa chỉ, tên card mạng}. */
    static List<String[]> addresses() throws SocketException {
        List<String[]> out = new ArrayList<>();
        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                if (a instanceof Inet4Address) out.add(new String[]{a.getHostAddress(), ni.getDisplayName()});
            }
        }
        return out;
    }

    static JFrame window(String name, List<String[]> ips, LogPane logPane) {
        JPanel head = new JPanel(new BorderLayout(0, 14));
        head.setBackground(Ui.INK);
        head.setBorder(new EmptyBorder(20, 28, 20, 28));

        JPanel title = new JPanel(new GridBagLayout());
        title.setOpaque(false);
        Ui.stack(title, Ui.label("Máy chủ bình chọn", 700, 19f, Color.WHITE), 0, false);
        JLabel status = Ui.label("Đang chạy  ·  TCP " + Protocol.TCP_PORT + "  ·  UDP " + Protocol.UDP_PORT + "  ·  " + name,
                400, 13f, Ui.ON_INK_2);
        status.setIcon(Ui.dot(new Color(0x4CC38A), 8));
        status.setIconTextGap(8);
        Ui.stack(title, status, 4, false);

        JPanel chips = new JPanel();
        chips.setLayout(new BoxLayout(chips, BoxLayout.X_AXIS));
        chips.setOpaque(false);
        chips.add(Ui.label(ips.isEmpty() ? "Không tìm thấy card mạng nào đang bật." : "Địa chỉ để Client nhập:", 500, 13f, Ui.ON_INK_2));
        for (String[] a : ips) {
            JLabel chip = new Chip(a[0]);
            chip.setToolTipText(a[1]);
            chips.add(Box.createHorizontalStrut(10));
            chips.add(chip);
        }
        JPanel chipRow = new JPanel(new BorderLayout());
        chipRow.setOpaque(false);
        chipRow.add(chips, BorderLayout.WEST);

        head.add(title, BorderLayout.NORTH);
        head.add(chipRow, BorderLayout.CENTER);

        JFrame f = new JFrame("LPP Server – " + name);
        f.add(head, BorderLayout.NORTH);
        f.add(Ui.plainScroll(logPane, Ui.SURFACE), BorderLayout.CENTER);
        f.setSize(new Dimension(980, 640));
        return f;
    }

    /** Địa chỉ IP dạng thẻ bo tròn trên thanh màu mực. */
    private static final class Chip extends JLabel {
        Chip(String text) {
            super(text);
            setFont(Ui.font(600, 14f));
            setForeground(Color.WHITE);
            setBorder(new EmptyBorder(5, 12, 5, 12));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.aa(g0);
            g.setColor(Ui.mix(Ui.INK, Color.WHITE, 0.14f));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** Log tô màu: giờ và địa chỉ nhạt, thông điệp đậm, kết nối xanh, lỗi đỏ. */
    static final class LogPane extends JTextPane {
        private final SimpleAttributeSet muted = style(Ui.INK_2, false);
        private final SimpleAttributeSet text = style(Ui.INK, false);
        private final SimpleAttributeSet strong = style(Ui.INK, true);
        private final SimpleAttributeSet ok = style(Ui.LIVE, false);
        private final SimpleAttributeSet bad = style(Ui.DANGER, false);

        LogPane() {
            setEditable(false);
            setMargin(new Insets(14, 20, 14, 20));
            setBackground(Ui.SURFACE);
        }

        private static SimpleAttributeSet style(Color c, boolean bold) {
            SimpleAttributeSet s = new SimpleAttributeSet();
            Font f = Ui.mono(13f);
            StyleConstants.setFontFamily(s, f.getFamily());
            StyleConstants.setFontSize(s, f.getSize());
            StyleConstants.setForeground(s, c);
            StyleConstants.setBold(s, bold);
            StyleConstants.setLineSpacing(s, 0.25f);
            return s;
        }

        void append(String time, String msg) {
            StyledDocument doc = getStyledDocument();
            try {
                doc.insertString(doc.getLength(), time + "  ", muted);
                int arrow = msg.indexOf(" → ");
                if (arrow >= 0) {
                    doc.insertString(doc.getLength(), msg.substring(0, arrow + 3), muted);
                    doc.insertString(doc.getLength(), msg.substring(arrow + 3), strong);
                } else if (msg.endsWith(" ngắt kết nối")) {
                    doc.insertString(doc.getLength(), msg, muted);
                } else if (msg.endsWith(" kết nối")) {
                    int cut = msg.length() - " kết nối".length();
                    doc.insertString(doc.getLength(), msg.substring(0, cut), muted);
                    doc.insertString(doc.getLength(), " kết nối", ok);
                } else if (msg.contains("lỗi") || msg.contains("Lỗi")) {
                    doc.insertString(doc.getLength(), msg, bad);
                } else if (msg.startsWith("UDP:")) {
                    doc.insertString(doc.getLength(), msg, muted);
                } else {
                    doc.insertString(doc.getLength(), msg, text);
                }
                doc.insertString(doc.getLength(), "\n", text);
                setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) { } // chèn vào cuối tài liệu luôn hợp lệ
        }
    }
}
