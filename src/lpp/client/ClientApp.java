package lpp.client;

import lpp.common.BarChart;
import lpp.common.Discovery;
import lpp.common.LineConnection;
import lpp.common.Protocol;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Chương trình Client: tìm server (UDP), vào phòng, vote, xem kết quả khi câu hỏi đóng. */
public final class ClientApp {
    private final JFrame frame = new JFrame("LPP Client");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    // màn hình kết nối
    private final JComboBox<Discovery.Found> serverBox = new JComboBox<>();
    private final JButton findBtn = new JButton("Tìm server");
    private final JTextField ipField = new JTextField(14);
    private final JTextField roomField = new JTextField(8);
    private final JTextField nameField = new JTextField(14);
    private final JButton joinBtn = new JButton("Vào phòng");

    // màn hình poll
    private final JTextArea questionArea = new JTextArea();
    private final JPanel center = new JPanel(new BorderLayout());
    private final JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 8, 8));
    private final BarChart resultChart = new BarChart();
    private final JLabel statusLabel = new JLabel(" ");
    private final List<JButton> optionButtons = new ArrayList<>();

    private LineConnection conn;
    private boolean leaving;
    private boolean joined;
    private int pollId = -1;
    private String question;
    private String[] options = new String[0];

    private ClientApp() {
        root.add(buildConnectScreen(), "connect");
        root.add(buildPollScreen(), "poll");
        frame.add(root);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 480);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        findServers();
    }

    private JPanel buildConnectScreen() {
        JPanel p = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        addRow(p, c, 0, "Server tìm thấy:", serverBox, findBtn);
        addRow(p, c, 1, "IP server:", ipField, null);
        addRow(p, c, 2, "Mã phòng:", roomField, null);
        addRow(p, c, 3, "Tên của bạn:", nameField, null);
        c.gridx = 1; c.gridy = 4;
        p.add(joinBtn, c);

        serverBox.addActionListener(e -> {
            Discovery.Found f = (Discovery.Found) serverBox.getSelectedItem();
            if (f != null) ipField.setText(f.host());
        });
        findBtn.addActionListener(e -> findServers());
        joinBtn.addActionListener(e -> join());
        return p;
    }

    private static void addRow(JPanel p, GridBagConstraints c, int row, String label,
                               java.awt.Component field, java.awt.Component extra) {
        c.gridy = row;
        c.gridx = 0; p.add(new JLabel(label), c);
        c.gridx = 1; p.add(field, c);
        if (extra != null) { c.gridx = 2; p.add(extra, c); }
    }

    private JPanel buildPollScreen() {
        questionArea.setEditable(false);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setOpaque(false);
        questionArea.setFont(questionArea.getFont().deriveFont(Font.BOLD, 20f));
        JPanel p = new JPanel(new BorderLayout(8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        p.add(questionArea, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        p.add(statusLabel, BorderLayout.SOUTH);
        return p;
    }

    private void findServers() {
        findBtn.setEnabled(false);
        findBtn.setText("Đang tìm...");
        new Thread(() -> {
            List<Discovery.Found> found;
            try {
                found = Discovery.find(Protocol.UDP_PORT, 2000);
            } catch (IOException e) {
                found = List.of();
            }
            List<Discovery.Found> result = found;
            SwingUtilities.invokeLater(() -> {
                serverBox.removeAllItems();
                result.forEach(serverBox::addItem);
                findBtn.setEnabled(true);
                findBtn.setText("Tìm server");
                if (result.isEmpty()) statusLabel.setText("Không tìm thấy server, hãy nhập IP.");
            });
        }, "discovery").start();
    }

    private void join() {
        String host = ipField.getText().trim(), code = roomField.getText().trim(), name = nameField.getText().trim();
        if (host.isEmpty() || code.isEmpty() || name.isEmpty()) {
            error("Nhập đủ IP server, mã phòng và tên");
            return;
        }
        try {
            conn = new LineConnection(host, Protocol.TCP_PORT);
        } catch (IOException e) {
            error("Không kết nối được " + host + ": " + e.getMessage());
            return;
        }
        conn.start(f -> SwingUtilities.invokeLater(() -> onMessage(f)),
                () -> SwingUtilities.invokeLater(this::onClosed));
        conn.send("JOIN", code, name);
        joinBtn.setEnabled(false);
    }

    private void onMessage(String[] f) {
        switch (f[0]) {
            case "JOIN_OK" -> {
                joined = true;
                frame.setTitle("LPP Client – " + nameField.getText().trim() + " @ phòng " + f[1]);
                questionArea.setText("Chờ giáo viên mở câu hỏi...");
                center.removeAll();
                center.revalidate();
                center.repaint();
                statusLabel.setText(" ");
                cards.show(root, "poll");
            }
            case "POLL" -> showPoll(Integer.parseInt(f[1]), f[2], Arrays.copyOfRange(f, 3, f.length));
            case "VOTE_OK" -> {
                if (Integer.parseInt(f[1]) != pollId) return;
                int chosen = Integer.parseInt(f[2]);
                for (int i = 0; i < optionButtons.size(); i++) {
                    optionButtons.get(i).setText((i == chosen ? "✓ " : "") + label(i));
                }
                statusLabel.setText("Đã chọn " + (char) ('A' + chosen) + ". Có thể đổi khi câu hỏi còn mở.");
            }
            case "POLL_CLOSED" -> {
                if (Integer.parseInt(f[1]) != pollId) return;
                int[] c = new int[f.length - 2];
                for (int i = 0; i < c.length; i++) c[i] = Integer.parseInt(f[i + 2]);
                resultChart.setData("Kết quả", options, c);
                setCenter(resultChart);
                statusLabel.setText("Câu hỏi đã đóng.");
                pollId = -1;
            }
            case "ROOM_ENDED" -> {
                leaving = true;
                conn.close();
                JOptionPane.showMessageDialog(frame, "Phòng đã kết thúc.", "LPP Client", JOptionPane.INFORMATION_MESSAGE);
            }
            case "ERROR" -> {
                if (!joined) { leaving = true; conn.close(); }
                error(f.length > 2 ? f[2] : String.join("|", f));
            }
            default -> { }
        }
    }

    private void showPoll(int id, String q, String[] opts) {
        pollId = id;
        question = q;
        options = opts;
        questionArea.setText(question);
        optionsPanel.removeAll();
        optionButtons.clear();
        for (int i = 0; i < opts.length; i++) {
            int idx = i;
            JButton b = new JButton(label(i));
            b.setFont(b.getFont().deriveFont(16f));
            b.setPreferredSize(new Dimension(0, 48));
            b.addActionListener(e -> conn.send("VOTE", String.valueOf(pollId), String.valueOf(idx)));
            optionButtons.add(b);
            optionsPanel.add(b);
        }
        setCenter(optionsPanel);
        statusLabel.setText("Chọn một đáp án.");
    }

    private void setCenter(java.awt.Component c) {
        center.removeAll();
        center.add(c, BorderLayout.CENTER);
        center.revalidate();
        center.repaint();
    }

    private String label(int i) { return (char) ('A' + i) + ". " + options[i]; }

    private void onClosed() {
        if (!leaving) error("Mất kết nối server");
        conn = null;
        leaving = false;
        joined = false;
        pollId = -1;
        joinBtn.setEnabled(true);
        frame.setTitle("LPP Client");
        cards.show(root, "connect");
    }

    private void error(String msg) { JOptionPane.showMessageDialog(frame, msg, "LPP Client", JOptionPane.ERROR_MESSAGE); }

    public static void main(String[] args) { SwingUtilities.invokeLater(ClientApp::new); }
}
