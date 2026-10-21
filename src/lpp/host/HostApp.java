package lpp.host;

import lpp.common.BarChart;
import lpp.common.LineConnection;
import lpp.common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
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
    private final JTextField serverField = new JTextField("localhost", 14);
    private final JButton connectBtn = new JButton("Kết nối & tạo phòng");
    private final JLabel roomLabel = new JLabel();
    private final JTextField questionField = new JTextField();
    private final JTextArea optionsArea = new JTextArea("Lựa chọn 1\nLựa chọn 2", 6, 20);
    private final JButton openBtn = new JButton("Mở câu hỏi");
    private final JButton closeBtn = new JButton("Đóng câu hỏi");
    private final JButton endBtn = new JButton("Kết thúc & xuất CSV");
    private final DefaultListModel<String> people = new DefaultListModel<>();
    private final JLabel onlineLabel = new JLabel();
    private final BarChart chart = new BarChart();

    private LineConnection conn;
    private boolean ending;
    private int nextPollId = 1;
    private int openPollId = -1;
    private int[] lastCounts;                                         // RESULT gần nhất của câu đang mở
    private final Map<Integer, String[]> polls = new HashMap<>(); // id → {câu hỏi, lựa chọn...}
    private final List<String[]> history = new ArrayList<>();      // {câu hỏi, lựa chọn, số phiếu}

    private HostApp() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Server:"));
        top.add(serverField);
        top.add(connectBtn);
        top.add(Box.createHorizontalStrut(24));
        roomLabel.setFont(roomLabel.getFont().deriveFont(Font.BOLD, 22f));
        top.add(roomLabel);

        JPanel editor = new JPanel(new BorderLayout(4, 4));
        editor.setBorder(BorderFactory.createTitledBorder("Câu hỏi"));
        editor.setPreferredSize(new Dimension(280, 0));
        editor.add(questionField, BorderLayout.NORTH);
        JPanel opts = new JPanel(new BorderLayout());
        opts.add(new JLabel("Lựa chọn (mỗi dòng một, 2-6):"), BorderLayout.NORTH);
        opts.add(new JScrollPane(optionsArea), BorderLayout.CENTER);
        editor.add(opts, BorderLayout.CENTER);
        JPanel btns = new JPanel(new GridLayout(3, 1, 4, 4));
        btns.add(openBtn);
        btns.add(closeBtn);
        btns.add(endBtn);
        editor.add(btns, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout());
        right.setBorder(BorderFactory.createTitledBorder("Người tham gia"));
        right.setPreferredSize(new Dimension(180, 0));
        right.add(onlineLabel, BorderLayout.NORTH);
        right.add(new JScrollPane(new JList<>(people)), BorderLayout.CENTER);

        frame.add(top, BorderLayout.NORTH);
        frame.add(editor, BorderLayout.WEST);
        frame.add(chart, BorderLayout.CENTER);
        frame.add(right, BorderLayout.EAST);

        connectBtn.addActionListener(e -> connect());
        openBtn.addActionListener(e -> openPoll());
        closeBtn.addActionListener(e -> conn.send("CLOSE_POLL", String.valueOf(openPollId)));
        endBtn.addActionListener(e -> endRoom());

        resetUi();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
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
                roomLabel.setText("Mã phòng: " + f[1]);
                openBtn.setEnabled(true);
                endBtn.setEnabled(true);
            }
            case "JOINED" -> { people.addElement(f[1]); updateOnline(); }
            case "LEFT" -> { people.removeElement(f[1]); updateOnline(); }
            case "RESULT" -> {
                int id = Integer.parseInt(f[1]);
                if (id == openPollId) {
                    lastCounts = counts(f);
                    showChart(id, lastCounts, "");
                }
            }
            case "POLL_CLOSED" -> {
                int id = Integer.parseInt(f[1]);
                String[] p = polls.get(id);
                int[] c = counts(f);
                for (int i = 0; i < c.length; i++) history.add(new String[]{p[0], p[i + 1], String.valueOf(c[i])});
                if (id == openPollId) {
                    showChart(id, c, " (đã đóng)");
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

    private void showChart(int id, int[] counts, String suffix) {
        String[] p = polls.get(id);
        chart.setData(p[0] + suffix, Arrays.copyOfRange(p, 1, p.length), counts);
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
        roomLabel.setText("Mã phòng: ------");
        chart.setData("Chưa có câu hỏi", new String[0], new int[0]);
        connectBtn.setEnabled(true);
        serverField.setEnabled(true);
        openBtn.setEnabled(false);
        closeBtn.setEnabled(false);
        endBtn.setEnabled(false);
    }

    private void updateOnline() { onlineLabel.setText("Online: " + people.size()); }

    private void error(String msg) { JOptionPane.showMessageDialog(frame, msg, "LPP Host", JOptionPane.ERROR_MESSAGE); }

    private static int[] counts(String[] f) {
        int[] c = new int[f.length - 2];
        for (int i = 0; i < c.length; i++) c[i] = Integer.parseInt(f[i + 2]);
        return c;
    }

    private static String csv(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }

    public static void main(String[] args) { SwingUtilities.invokeLater(HostApp::new); }
}
