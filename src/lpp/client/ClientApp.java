package lpp.client;

import lpp.common.BarChart;
import lpp.common.Discovery;
import lpp.common.LineConnection;
import lpp.common.Protocol;
import lpp.common.Ui;

import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Chương trình Client: tìm server (UDP), vào phòng, vote, xem kết quả khi câu hỏi đóng. */
public final class ClientApp {
    private final JFrame frame = new JFrame("LPP Client");
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    // màn hình kết nối
    private final JComboBox<Discovery.Found> serverBox = Ui.combo();
    private final JButton findBtn = Ui.button("Tìm lại", Ui.Kind.SECONDARY);
    private final JLabel findHint = Ui.label(" ", 400, 13f, Ui.INK_2);
    private final JTextField ipField = Ui.textField("");
    private final JTextField roomField = Ui.textField("");
    private final JTextField nameField = Ui.textField("");
    private final JButton joinBtn = Ui.button("Vào phòng", Ui.Kind.PRIMARY);

    // màn hình poll
    private final JLabel roomMeta = Ui.label(" ", 500, 13f, Ui.INK_2);
    private final JTextArea questionArea = new JTextArea();
    private final JPanel center = new JPanel(new BorderLayout());
    private final JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 0, 10));
    private final BarChart resultChart = new BarChart();
    private final JLabel statusLabel = Ui.label(" ", 400, 13f, Ui.INK_2);
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
        frame.setMinimumSize(new Dimension(520, 560));
        frame.setSize(640, 680);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        findServers();
    }

    private JPanel buildConnectScreen() {
        JPanel form = new JPanel(new GridBagLayout()) {
            @Override public Dimension getPreferredSize() { return new Dimension(400, super.getPreferredSize().height); }
        };
        form.setOpaque(false);
        roomField.setFont(Ui.font(600, 22f).deriveFont(Map.of(TextAttribute.TRACKING, 0.12f)));
        JPanel serverRow = new JPanel(new BorderLayout(8, 0));
        serverRow.setOpaque(false);
        serverRow.add(serverBox, BorderLayout.CENTER);
        serverRow.add(findBtn, BorderLayout.EAST);

        Ui.stack(form, Ui.label("Vào phòng bình chọn", 700, 26f, Ui.INK), 0, false);
        Ui.stack(form, Ui.label("Nhập mã phòng đang hiện trên màn hình của giáo viên.", 400, 14f, Ui.INK_2), 6, false);
        Ui.stack(form, Ui.label("Máy chủ", 500, 13f, Ui.INK_2), 28, false);
        Ui.stack(form, serverRow, 6, false);
        Ui.stack(form, findHint, 6, false);
        Ui.stack(form, Ui.label("Địa chỉ IP", 500, 13f, Ui.INK_2), 16, false);
        Ui.stack(form, ipField, 6, false);
        Ui.stack(form, Ui.label("Mã phòng", 500, 13f, Ui.INK_2), 16, false);
        Ui.stack(form, roomField, 6, false);
        Ui.stack(form, Ui.label("Tên của bạn", 500, 13f, Ui.INK_2), 16, false);
        Ui.stack(form, nameField, 6, false);
        Ui.stack(form, joinBtn, 26, false);

        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(24, 24, 24, 24));
        p.add(form);

        serverBox.addActionListener(e -> {
            Discovery.Found f = (Discovery.Found) serverBox.getSelectedItem();
            if (f != null) ipField.setText(f.host());
        });
        findBtn.addActionListener(e -> findServers());
        joinBtn.addActionListener(e -> join());
        roomField.addActionListener(e -> join());
        nameField.addActionListener(e -> join());
        return p;
    }

    private JPanel buildPollScreen() {
        questionArea.setEditable(false);
        questionArea.setFocusable(false);
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setOpaque(false);
        questionArea.setFont(Ui.font(600, 24f));
        questionArea.setForeground(Ui.INK);
        roomMeta.setIcon(Ui.dot(Ui.LIVE, 8));
        roomMeta.setIconTextGap(8);
        resultChart.setCard(true);
        center.setOpaque(false);
        optionsPanel.setOpaque(false);

        JPanel head = new JPanel(new BorderLayout(0, 10));
        head.setOpaque(false);
        head.add(roomMeta, BorderLayout.NORTH);
        head.add(questionArea, BorderLayout.CENTER);

        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setBorder(new EmptyBorder(24, 28, 20, 28));
        p.add(head, BorderLayout.NORTH);
        p.add(center, BorderLayout.CENTER);
        p.add(statusLabel, BorderLayout.SOUTH);
        return p;
    }

    private void findServers() {
        findBtn.setEnabled(false);
        findHint.setText("Đang tìm máy chủ trong mạng LAN…");
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
                findHint.setText(result.isEmpty() ? "Không tìm thấy máy chủ — hãy nhập địa chỉ IP bên dưới."
                        : "Tìm thấy " + result.size() + " máy chủ. Chọn đúng máy của giáo viên.");
            });
        }, "discovery").start();
    }

    private void join() {
        if (!joinBtn.isEnabled()) return;
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
                String name = nameField.getText().trim();
                frame.setTitle("LPP Client – " + name + " @ phòng " + f[1]);
                roomMeta.setText("Phòng " + f[1] + "  ·  " + name);
                questionArea.setVisible(false);
                resultChart.setEmpty("Chờ giáo viên mở câu hỏi", "Câu hỏi sẽ hiện ở đây ngay khi được mở.");
                setCenter(resultChart);
                statusLabel.setText(" ");
                cards.show(root, "poll");
            }
            case "POLL" -> showPoll(Integer.parseInt(f[1]), f[2], Arrays.copyOfRange(f, 3, f.length));
            case "VOTE_OK" -> {
                if (Integer.parseInt(f[1]) != pollId) return;
                int chosen = Integer.parseInt(f[2]);
                for (int i = 0; i < optionButtons.size(); i++) ((Tile) optionButtons.get(i)).setChosen(i == chosen);
                statusLabel.setText("Đã ghi nhận lựa chọn " + (char) ('A' + chosen) + ". Bấm đáp án khác để đổi khi câu hỏi còn mở.");
            }
            case "POLL_CLOSED" -> {
                if (Integer.parseInt(f[1]) != pollId) return;
                int[] c = new int[f.length - 2];
                for (int i = 0; i < c.length; i++) c[i] = Integer.parseInt(f[i + 2]);
                questionArea.setVisible(false);
                resultChart.setData(question, options, c, true);
                setCenter(resultChart);
                statusLabel.setText("Câu hỏi đã đóng. Chờ câu tiếp theo.");
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
        questionArea.setVisible(true);
        optionsPanel.removeAll();
        optionButtons.clear();
        for (int i = 0; i < opts.length; i++) {
            int idx = i;
            Tile b = new Tile(i, opts[i]);
            b.addActionListener(e -> conn.send("VOTE", String.valueOf(pollId), String.valueOf(idx)));
            optionButtons.add(b);
            optionsPanel.add(b);
        }
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(optionsPanel, BorderLayout.NORTH);
        setCenter(Ui.plainScroll(top, Ui.BG));
        statusLabel.setText("Chọn một đáp án.");
    }

    private void setCenter(java.awt.Component c) {
        center.removeAll();
        center.add(c, BorderLayout.CENTER);
        center.revalidate();
        center.repaint();
    }

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

    /** Ô đáp án: huy hiệu màu A–F (trùng màu cột trên biểu đồ của Host), nội dung, dấu tích khi Server đã ghi nhận. */
    private static final class Tile extends JButton {
        private final int index;
        private boolean chosen;

        Tile(int index, String text) {
            super(text);
            this.index = index;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setFont(Ui.font(500, 18f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(0, 66));
        }

        void setChosen(boolean chosen) {
            this.chosen = chosen;
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.aa(g0);
            ButtonModel m = getModel();
            Color col = Ui.answer(index);
            int w = getWidth(), h = getHeight(), r = 14;
            Color fill = chosen ? Ui.mix(col, Ui.SURFACE, 0.88f)
                    : m.isArmed() && m.isPressed() ? Ui.TRACK : m.isRollover() ? new Color(0xFCFBF8) : Ui.SURFACE;
            g.setColor(fill);
            g.fillRoundRect(1, 1, w - 3, h - 3, r, r);
            if (chosen) {
                g.setColor(col);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(1, 1, w - 3, h - 3, r, r);
            } else {
                g.setColor(m.isRollover() ? Ui.mix(Ui.LINE, Ui.INK_2, 0.45f) : Ui.LINE);
                g.drawRoundRect(1, 1, w - 3, h - 3, r, r);
            }
            if (isFocusOwner()) {
                g.setColor(Ui.FOCUS);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(4, 4, w - 9, h - 9, r - 4, r - 4);
            }
            int d = 34;
            Ui.badge(g, index, 18, (h - d) / 2, d);
            int textX = 18 + d + 16, checkW = chosen ? 40 : 0;
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Ui.INK);
            g.drawString(Ui.ellipsize(getText(), fm, w - textX - 18 - checkW),
                    textX, (h - fm.getAscent() - fm.getDescent()) / 2f + fm.getAscent());
            if (chosen) Ui.check(g, col, w - 46, (h - 26) / 2f, 26);
            g.dispose();
        }
    }

    public static void main(String[] args) {
        Ui.install();
        SwingUtilities.invokeLater(ClientApp::new);
    }
}
