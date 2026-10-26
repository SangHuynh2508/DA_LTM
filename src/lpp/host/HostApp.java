package lpp.host;

import lpp.common.BarChart;
import lpp.common.LineConnection;
import lpp.common.Protocol;
import lpp.common.Ui;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Chương trình Host (giáo viên): tạo phòng, mở/đóng câu poll, xem biểu đồ realtime, xuất CSV. */
public final class HostApp {
    private final JFrame frame = new JFrame("LPP Host");
    private final JTextField serverField = Ui.textField("localhost");
    private final JButton connectBtn = Ui.button("Kết nối & tạo phòng", Ui.Kind.ON_DARK);
    private final JLabel roomLabel = Ui.label("", 700, 40f, Color.WHITE);
    private final JTextField questionField = Ui.textField("");
    private final JTextArea optionsArea = new JTextArea("Lựa chọn 1\nLựa chọn 2", 6, 20);
    private final JButton openBtn = Ui.button("Mở câu hỏi", Ui.Kind.PRIMARY);
    private final JButton closeBtn = Ui.button("Đóng câu hỏi", Ui.Kind.SECONDARY);
    private final JButton endBtn = Ui.button("Kết thúc & xuất CSV", Ui.Kind.DANGER);
    private final DefaultListModel<String> people = new DefaultListModel<>();
    private final JLabel onlineLabel = Ui.label("", 400, 13f, Ui.INK_2);
    private final BarChart chart = new BarChart();

    private LineConnection conn;
    private boolean ending;
    private int nextPollId = 1;
    private int openPollId = -1;
    private int[] lastCounts;                                         // RESULT gần nhất của câu đang mở
    private final Map<Integer, String[]> polls = new HashMap<>(); // id → {câu hỏi, lựa chọn...}
    private final List<String[]> history = new ArrayList<>();      // {câu hỏi, lựa chọn, số phiếu}

    private HostApp() {
        frame.getContentPane().setBackground(Ui.BG);
        frame.add(topBar(), BorderLayout.NORTH);
        frame.add(editor(), BorderLayout.WEST);
        chart.setBorder(new CompoundBorder(new MatteBorder(0, 1, 0, 1, Ui.LINE), new EmptyBorder(32, 40, 32, 40)));
        frame.add(chart, BorderLayout.CENTER);
        frame.add(participants(), BorderLayout.EAST);

        connectBtn.addActionListener(e -> connect());
        openBtn.addActionListener(e -> openPoll());
        closeBtn.addActionListener(e -> conn.send("CLOSE_POLL", String.valueOf(openPollId)));
        endBtn.addActionListener(e -> endRoom());

        resetUi();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1040, 640));
        frame.setSize(1240, 760);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    /** Thanh màu mực: tên chương trình, ô địa chỉ máy chủ, mã phòng cỡ lớn để chiếu cho cả lớp. */
    private JPanel topBar() {
        JPanel bar = new JPanel(new BorderLayout(32, 0));
        bar.setBackground(Ui.INK);
        bar.setBorder(new EmptyBorder(18, 28, 18, 28));

        JPanel brand = new JPanel(new GridBagLayout());
        brand.setOpaque(false);
        Ui.stack(brand, Ui.label("Bình chọn LAN", 700, 19f, Color.WHITE), 0, false);
        Ui.stack(brand, Ui.label("Bảng điều khiển của giáo viên", 400, 13f, Ui.ON_INK_2), 2, false);

        JPanel server = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        server.setOpaque(false);
        server.add(Ui.label("Máy chủ", 500, 13f, Ui.ON_INK_2));
        serverField.setColumns(13);
        server.add(serverField);
        server.add(connectBtn);
        JPanel middle = new JPanel(new GridBagLayout());
        middle.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        middle.add(server, c);

        JPanel room = new JPanel(new GridBagLayout());
        room.setOpaque(false);
        JLabel caption = Ui.label("Mã phòng", 500, 13f, Ui.ON_INK_2);
        caption.setHorizontalAlignment(JLabel.RIGHT);
        roomLabel.setHorizontalAlignment(JLabel.RIGHT);
        Ui.stack(room, caption, 0, false);
        Ui.stack(room, roomLabel, 0, false);

        bar.add(brand, BorderLayout.WEST);
        bar.add(middle, BorderLayout.CENTER);
        bar.add(room, BorderLayout.EAST);
        return bar;
    }

    private JPanel editor() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(28, 28, 28, 24));
        p.setPreferredSize(new Dimension(330, 0));
        optionsArea.setMargin(new Insets(8, 10, 8, 10));
        optionsArea.setLineWrap(true);
        optionsArea.setWrapStyleWord(true);
        optionsArea.setFont(Ui.font(400, 15f));
        questionField.setFont(Ui.font(400, 15f));
        Ui.stack(p, Ui.label("Soạn câu hỏi", 600, 18f, Ui.INK), 0, false);
        Ui.stack(p, Ui.label("Nội dung", 500, 13f, Ui.INK_2), 18, false);
        Ui.stack(p, questionField, 6, false);
        Ui.stack(p, Ui.label("Lựa chọn · mỗi dòng một, từ 2 đến 6", 500, 13f, Ui.INK_2), 18, false);
        Ui.stack(p, Ui.fieldScroll(optionsArea), 6, true);
        Ui.stack(p, openBtn, 22, false);
        Ui.stack(p, closeBtn, 8, false);
        Ui.stack(p, endBtn, 28, false);
        return p;
    }

    private JPanel participants() {
        JPanel p = new JPanel(new BorderLayout(0, 14));
        p.setBorder(new EmptyBorder(28, 24, 28, 28));
        p.setPreferredSize(new Dimension(250, 0));
        JPanel head = new JPanel(new GridBagLayout());
        head.setOpaque(false);
        Ui.stack(head, Ui.label("Người tham gia", 600, 18f, Ui.INK), 0, false);
        Ui.stack(head, onlineLabel, 4, false);
        JList<String> list = new JList<>(people);
        list.setFocusable(false);
        list.setFixedCellHeight(38);
        list.setCellRenderer(new PersonRenderer());
        p.add(head, BorderLayout.NORTH);
        p.add(Ui.plainScroll(list, Ui.BG), BorderLayout.CENTER);
        return p;
    }

    /** Một hàng trong danh sách: vòng tròn chữ cái đầu của tên + tên. */
    private static final class PersonRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, false, false);
            String[] words = String.valueOf(value).trim().split(" ");
            String last = words[words.length - 1];
            String initial = last.isEmpty() ? "?" : last.substring(0, 1).toUpperCase();
            l.setIcon(new Icon() {
                @Override public void paintIcon(Component c, Graphics g0, int x, int y) {
                    Graphics2D g = Ui.aa(g0);
                    g.setColor(Ui.TRACK);
                    g.fillOval(x, y, 28, 28);
                    g.setColor(Ui.INK_2);
                    g.setFont(Ui.font(600, 13f));
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(initial, x + (28 - fm.stringWidth(initial)) / 2f, y + (28 - fm.getAscent() - fm.getDescent()) / 2f + fm.getAscent());
                    g.dispose();
                }
                @Override public int getIconWidth() { return 28; }
                @Override public int getIconHeight() { return 28; }
            });
            l.setIconTextGap(12);
            l.setFont(Ui.font(500, 15f));
            l.setForeground(Ui.INK);
            l.setOpaque(false);
            l.setBorder(new EmptyBorder(0, 0, 0, 0));
            return l;
        }
    }

    private void connect() {
        try {
            conn = new LineConnection(serverField.getText().trim(), Protocol.TCP_PORT);
        } catch (IOException e) {
            error("Không kết nối được server: " + e.getMessage());
            return;
        }
        conn.start(f -> SwingUtilities.invokeLater(() -> onMessage(f)),
                () -> SwingUtilities.invokeLater(this::onClosed));
        conn.send("CREATE_ROOM");
        connectBtn.setEnabled(false);
        serverField.setEnabled(false);
    }

    private void onMessage(String[] f) {
        switch (f[0]) {
            case "ROOM_CREATED" -> {
                roomLabel.setText(f[1].length() == 6 ? f[1].substring(0, 3) + " " + f[1].substring(3) : f[1]);
                roomLabel.setForeground(Color.WHITE);
                chart.setEmpty("Phòng đã sẵn sàng", "Đọc mã phòng cho cả lớp, soạn câu hỏi ở khung bên trái rồi bấm Mở câu hỏi.");
                openBtn.setEnabled(true);
                endBtn.setEnabled(true);
            }
            case "JOINED" -> { people.addElement(f[1]); updateOnline(); }
            case "LEFT" -> { people.removeElement(f[1]); updateOnline(); }
            case "RESULT" -> {
                int id = Integer.parseInt(f[1]);
                if (id == openPollId) {
                    lastCounts = counts(f);
                    showChart(id, lastCounts, false);
                }
            }
            case "POLL_CLOSED" -> {
                int id = Integer.parseInt(f[1]);
                String[] p = polls.get(id);
                int[] c = counts(f);
                for (int i = 0; i < c.length; i++) history.add(new String[]{p[0], p[i + 1], String.valueOf(c[i])});
                if (id == openPollId) {
                    showChart(id, c, true);
                    openPollId = -1;
                    closeBtn.setEnabled(false);
                }
            }
            case "ROOM_ENDED" -> {
                exportCsv();
                history.clear(); // đã xuất; onClosed sắp chạy không xuất lại
                openPollId = -1;
                conn.close();
            }
            case "ERROR" -> error(f.length > 2 ? f[2] : String.join("|", f));
            default -> { }
        }
    }

    private void onClosed() {
        // câu đang mở chưa có POLL_CLOSED: giữ số phiếu cuối cùng nhận được
        if (openPollId != -1 && lastCounts != null) {
            String[] p = polls.get(openPollId);
            for (int i = 0; i < lastCounts.length; i++) {
                history.add(new String[]{p[0] + " (chưa đóng)", p[i + 1], String.valueOf(lastCounts[i])});
            }
        }
        if (!history.isEmpty()) {
            error("Mất kết nối server. Hãy lưu kết quả đã thu được.");
            exportCsv();
        } else if (!ending) {
            error("Mất kết nối server");
        }
        resetUi();
    }

    private void openPoll() {
        String q = questionField.getText().trim();
        String[] opts = Arrays.stream(optionsArea.getText().split("\n"))
                .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
        if (q.isEmpty() || opts.length < 2 || opts.length > 6) {
            error("Cần nội dung câu hỏi và 2-6 lựa chọn (mỗi dòng một lựa chọn)");
            return;
        }
        openPollId = nextPollId++;
        lastCounts = null;
        String[] p = new String[opts.length + 1];
        p[0] = q;
        System.arraycopy(opts, 0, p, 1, opts.length);
        polls.put(openPollId, p);

        String[] msg = new String[opts.length + 3];
        msg[0] = "OPEN_POLL";
        msg[1] = String.valueOf(openPollId);
        msg[2] = q;
        System.arraycopy(opts, 0, msg, 3, opts.length);
        conn.send(msg);
        closeBtn.setEnabled(true);
    }

    private void endRoom() {
        int ok = JOptionPane.showConfirmDialog(frame, "Kết thúc phòng và xuất kết quả?", "LPP Host", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        if (openPollId != -1) conn.send("CLOSE_POLL", String.valueOf(openPollId));
        ending = true;
        conn.send("END_ROOM");
    }

    private void showChart(int id, int[] counts, boolean closed) {
        String[] p = polls.get(id);
        chart.setData(p[0], Arrays.copyOfRange(p, 1, p.length), counts, closed);
    }

    private void exportCsv() {
        if (history.isEmpty()) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("ket-qua-poll.csv"));
        if (fc.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try (Writer w = new OutputStreamWriter(new FileOutputStream(fc.getSelectedFile()), StandardCharsets.UTF_8)) {
            w.write('﻿'); // BOM để Excel hiện đúng tiếng Việt
            w.write("Câu hỏi,Lựa chọn,Số phiếu\n");
            for (String[] r : history) w.write(csv(r[0]) + "," + csv(r[1]) + "," + r[2] + "\n");
        } catch (IOException e) {
            error("Không ghi được file: " + e.getMessage());
        }
    }

    private void resetUi() {
        conn = null;
        ending = false;
        openPollId = -1;
        polls.clear();
        history.clear();
        people.clear();
        updateOnline();
        roomLabel.setText("––– –––");
        roomLabel.setForeground(Ui.mix(Ui.INK, Color.WHITE, 0.3f)); // chỗ giữ chỗ, nhạt hơn mã thật
        chart.setEmpty("Chưa kết nối", "Nhập địa chỉ máy chủ ở thanh trên cùng rồi bấm Kết nối & tạo phòng.");
        connectBtn.setEnabled(true);
        serverField.setEnabled(true);
        openBtn.setEnabled(false);
        closeBtn.setEnabled(false);
        endBtn.setEnabled(false);
    }

    private void updateOnline() {
        onlineLabel.setText(people.isEmpty() ? "Chưa có ai vào phòng" : people.size() + " người đang trong phòng");
    }

    private void error(String msg) { JOptionPane.showMessageDialog(frame, msg, "LPP Host", JOptionPane.ERROR_MESSAGE); }

    private static int[] counts(String[] f) {
        int[] c = new int[f.length - 2];
        for (int i = 0; i < c.length; i++) c[i] = Integer.parseInt(f[i + 2]);
        return c;
    }

    private static String csv(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }

    public static void main(String[] args) {
        Ui.install();
        SwingUtilities.invokeLater(HostApp::new);
    }
}
