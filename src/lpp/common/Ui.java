package lpp.common;

import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Giao diện chung của Server, Host, Client: bảng màu, font Be Vietnam Pro, nút, ô nhập, thanh cuộn, huy hiệu đáp án. */
public final class Ui {
    public static final Color BG = new Color(0xF5F3EE);        // nền giấy ngà
    public static final Color SURFACE = Color.WHITE;
    public static final Color INK = new Color(0x1C2340);       // chữ chính, nút chính, thanh trên cùng
    public static final Color INK_2 = new Color(0x5A6075);     // chữ phụ
    public static final Color ON_INK_2 = new Color(0xB4BAD0);  // chữ phụ trên nền mực
    public static final Color LINE = new Color(0xDDD8CE);
    public static final Color TRACK = new Color(0xECE8E0);
    public static final Color FOCUS = new Color(0x3B5BDB);
    public static final Color LIVE = new Color(0x2B9A66);
    public static final Color DANGER = new Color(0xC2412D);
    private static final Color SELECTION = new Color(0xD3DBF7);
    /** Màu đáp án A–F, dùng chung cho cột biểu đồ của Host và nút bấm của Client. */
    private static final Color[] ANSWER = {
            new Color(0xE5484D), new Color(0x3E63DD), new Color(0xE2A336),
            new Color(0x30A46C), new Color(0x8E4EC6), new Color(0x12A594)};

    private static final Font[] FACES = new Font[4]; // 400, 500, 600, 700
    private static final int RADIUS = 10;

    private Ui() { }

    /** Gọi đầu tiên trong main, trước khi tạo thành phần Swing nào. */
    public static void install() {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        String[] files = {"Regular", "Medium", "SemiBold", "Bold"};
        for (int i = 0; i < files.length; i++) {
            try (InputStream in = Ui.class.getResourceAsStream("fonts/BeVietnamPro-" + files[i] + ".ttf")) {
                if (in == null) continue;
                FACES[i] = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(FACES[i]);
            } catch (Exception ignored) { } // thiếu font thì dùng font hệ thống
        }

        FontUIResource base = new FontUIResource(font(400, 14f));
        for (String k : List.of("Label.font", "Button.font", "ToggleButton.font", "TextField.font", "FormattedTextField.font",
                "PasswordField.font", "TextArea.font", "TextPane.font", "EditorPane.font", "List.font", "ComboBox.font",
                "OptionPane.messageFont", "OptionPane.buttonFont", "ToolTip.font", "Table.font", "TableHeader.font",
                "CheckBox.font", "RadioButton.font", "Menu.font", "MenuItem.font", "PopupMenu.font", "TitledBorder.font",
                "FileChooser.listFont", "Panel.font", "Viewport.font", "ScrollPane.font", "Spinner.font")) {
            UIManager.put(k, base);
        }
        put(BG, "Panel.background", "OptionPane.background", "Viewport.background", "ScrollPane.background",
                "TextField.inactiveBackground", "TextArea.inactiveBackground", "FileChooser.background");
        put(INK, "Label.foreground", "OptionPane.messageForeground", "TextField.foreground", "TextArea.foreground",
                "TextPane.foreground", "List.foreground", "ComboBox.foreground", "Button.foreground",
                "TextField.caretForeground", "TextArea.caretForeground", "TextPane.caretForeground",
                "TextField.selectionForeground", "TextArea.selectionForeground", "TextPane.selectionForeground",
                "List.selectionForeground", "ComboBox.selectionForeground");
        put(SURFACE, "TextField.background", "TextArea.background", "TextPane.background", "List.background",
                "ComboBox.background", "Button.background");
        put(SELECTION, "TextField.selectionBackground", "TextArea.selectionBackground", "TextPane.selectionBackground",
                "List.selectionBackground", "ComboBox.selectionBackground");
        put(INK_2, "TextField.inactiveForeground", "TextArea.inactiveForeground", "Label.disabledForeground");
        // nút của hộp thoại hệ thống (JOptionPane, JFileChooser): phẳng, viền mảnh
        List<Object> flat = Arrays.asList(0.3f, 0f, SURFACE, SURFACE, SURFACE);
        UIManager.put("Button.gradient", flat);
        UIManager.put("ToggleButton.gradient", flat);
        UIManager.put("Button.select", new ColorUIResource(TRACK));
        UIManager.put("Button.focus", new ColorUIResource(FOCUS));
        UIManager.put("Button.border", new javax.swing.border.CompoundBorder(new FieldBorder(), new EmptyBorder(0, 6, 0, 6)));
        UIManager.put("TextField.border", new FieldBorder());
        UIManager.put("ScrollBarUI", ThinScrollBar.class.getName());

        // vẽ lại viền ô nhập khi đổi tiêu điểm (viền xanh khi đang gõ)
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", e -> {
            for (Object o : new Object[]{e.getOldValue(), e.getNewValue()}) {
                if (o instanceof Component c) {
                    c.repaint();
                    if (c.getParent() instanceof JViewport v && v.getParent() != null) v.getParent().repaint();
                }
            }
        });
    }

    private static void put(Color c, String... keys) {
        for (String k : keys) UIManager.put(k, new ColorUIResource(c));
    }

    /** weight: 400, 500, 600 hoặc 700. */
    public static Font font(int weight, float size) {
        int i = Math.max(0, Math.min(3, weight / 100 - 4));
        if (FACES[i] != null) return FACES[i].deriveFont(size);
        return new Font("Segoe UI", weight >= 600 ? Font.BOLD : Font.PLAIN, Math.round(size));
    }

    public static Font mono(float size) {
        List<String> have = Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        for (String f : List.of("Cascadia Mono", "Consolas", "Menlo", "DejaVu Sans Mono")) {
            if (have.contains(f)) return new Font(f, Font.PLAIN, Math.round(size));
        }
        return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size));
    }

    public static JLabel label(String text, int weight, float size, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font(weight, size));
        l.setForeground(color);
        return l;
    }

    public static Graphics2D aa(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    public static Color mix(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ---------- đáp án ----------

    public static Color answer(int i) { return ANSWER[i % ANSWER.length]; }

    /** Vòng tròn màu của đáp án kèm chữ cái A, B, C… */
    public static void badge(Graphics2D g, int i, int x, int y, int d) {
        g.setColor(answer(i));
        g.fillOval(x, y, d, d);
        g.setColor(i % ANSWER.length == 2 ? INK : Color.WHITE); // vàng hổ phách cần chữ tối
        g.setFont(font(700, d * 0.48f));
        FontMetrics fm = g.getFontMetrics();
        String s = String.valueOf((char) ('A' + i));
        g.drawString(s, x + (d - fm.stringWidth(s)) / 2f, y + (d - fm.getAscent() - fm.getDescent()) / 2f + fm.getAscent());
    }

    public static void check(Graphics2D g, Color c, float x, float y, float s) {
        Path2D p = new Path2D.Float();
        p.moveTo(x + s * 0.18, y + s * 0.53);
        p.lineTo(x + s * 0.41, y + s * 0.75);
        p.lineTo(x + s * 0.83, y + s * 0.28);
        g.setColor(c);
        g.setStroke(new BasicStroke(s * 0.13f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }

    public static Icon dot(Color c, int d) {
        return new Icon() {
            @Override public void paintIcon(Component comp, Graphics g0, int x, int y) {
                Graphics2D g = aa(g0);
                g.setColor(c);
                g.fillOval(x, y, d, d);
                g.dispose();
            }
            @Override public int getIconWidth() { return d; }
            @Override public int getIconHeight() { return d; }
        };
    }

    // ---------- chữ ----------

    public static String ellipsize(String s, FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) return s;
        String dots = "…";
        int n = s.length();
        while (n > 0 && fm.stringWidth(s.substring(0, n) + dots) > width) n--;
        return s.substring(0, n).stripTrailing() + dots;
    }

    /** Ngắt dòng theo từ, tối đa maxLines dòng (dòng cuối có dấu … nếu còn thừa chữ). */
    public static List<String> wrap(String s, FontMetrics fm, int width, int maxLines) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        String[] words = s.split(" ");
        for (int i = 0; i < words.length; i++) {
            String next = cur.length() == 0 ? words[i] : cur + " " + words[i];
            if (fm.stringWidth(next) <= width || cur.length() == 0) {
                cur = new StringBuilder(next);
                continue;
            }
            if (lines.size() == maxLines - 1) {
                lines.add(ellipsize(cur + " " + String.join(" ", Arrays.copyOfRange(words, i, words.length)), fm, width));
                return lines;
            }
            lines.add(cur.toString());
            cur = new StringBuilder(words[i]);
        }
        if (cur.length() > 0) lines.add(ellipsize(cur.toString(), fm, width));
        return lines;
    }

    /** Như wrap nhưng thu hẹp bề rộng tới mức vẫn giữ số dòng, để các dòng dài gần bằng nhau (tránh một chữ rơi xuống dòng cuối). */
    public static List<String> wrapBalanced(String s, FontMetrics fm, int width, int maxLines) {
        int lines = wrap(s, fm, width, maxLines).size(), best = width;
        if (lines < 2) return wrap(s, fm, width, maxLines);
        for (int w = width - 8; w > width / 2; w -= 8) {
            if (wrap(s, fm, w, maxLines + 1).size() > lines) break;
            best = w;
        }
        return wrap(s, fm, best, maxLines);
    }

    // ---------- bố cục ----------

    /** Xếp thành phần theo cột trong panel GridBagLayout: gap là khoảng trống phía trên, grow cho phép giãn theo chiều dọc. */
    public static void stack(JPanel p, Component c, int gap, boolean grow) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = p.getComponentCount();
        g.weightx = 1;
        g.weighty = grow ? 1 : 0;
        g.fill = grow ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTHWEST;
        g.insets = new Insets(gap, 0, 0, 0);
        p.add(c, g);
    }

    // ---------- ô nhập ----------

    public static JTextField textField(String text) {
        JTextField f = new JTextField(text) {
            @Override protected void paintComponent(Graphics g) { paintFieldBackground(this, g); super.paintComponent(g); }
        };
        f.setOpaque(false);
        f.setBorder(new FieldBorder());
        f.setSelectionColor(SELECTION);
        return f;
    }

    /** Khung cuộn có viền bo tròn như ô nhập, dùng cho JTextArea. */
    public static JScrollPane fieldScroll(JComponent view) {
        JScrollPane sp = new JScrollPane(view) {
            @Override protected void paintComponent(Graphics g) { paintFieldBackground(this, g); super.paintComponent(g); }
        };
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        view.setOpaque(false);
        sp.setBorder(new FieldBorder());
        return sp;
    }

    /** Khung cuộn không viền, nền theo màu cho trước. */
    public static JScrollPane plainScroll(JComponent view, Color bg) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBorder(new EmptyBorder(0, 0, 0, 0));
        sp.getViewport().setBackground(bg);
        view.setBackground(bg);
        return sp;
    }

    public static <T> JComboBox<T> combo() {
        JComboBox<T> box = new JComboBox<>() {
            @Override protected void paintComponent(Graphics g) { paintFieldBackground(this, g); super.paintComponent(g); }
        };
        box.setOpaque(false);
        box.setBorder(new FieldBorder());
        box.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override protected void paintComponent(Graphics g0) {
                        Graphics2D g = aa(g0);
                        int cx = getWidth() / 2, cy = getHeight() / 2;
                        Path2D p = new Path2D.Float();
                        p.moveTo(cx - 5, cy - 2);
                        p.lineTo(cx, cy + 3);
                        p.lineTo(cx + 5, cy - 2);
                        g.setColor(isEnabled() ? INK_2 : LINE);
                        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g.draw(p);
                        g.dispose();
                    }
                };
                b.setBorder(new EmptyBorder(0, 0, 0, 0));
                b.setContentAreaFilled(false);
                b.setFocusable(false);
                b.setPreferredSize(new Dimension(26, 0));
                return b;
            }
            @Override public void paintCurrentValueBackground(Graphics g, Rectangle r, boolean hasFocus) { }
            @Override public void paintCurrentValue(Graphics g, Rectangle r, boolean hasFocus) {
                super.paintCurrentValue(g, r, false);
            }
        });
        return box;
    }

    private static void paintFieldBackground(JComponent c, Graphics g0) {
        Graphics2D g = aa(g0);
        g.setColor(c.isEnabled() ? SURFACE : TRACK);
        g.fillRoundRect(1, 1, c.getWidth() - 2, c.getHeight() - 2, RADIUS, RADIUS);
        g.dispose();
    }

    private static boolean focused(Component c) {
        if (c.hasFocus()) return true;
        if (c instanceof JScrollPane sp && sp.getViewport().getView() != null) return sp.getViewport().getView().hasFocus();
        if (c instanceof JComboBox<?> box) return box.isPopupVisible();
        return false;
    }

    /** Viền bo tròn 1px, chuyển sang viền xanh 2px khi ô đang được gõ. */
    static final class FieldBorder extends AbstractBorder {
        @Override public Insets getBorderInsets(Component c, Insets i) {
            if (c instanceof JScrollPane) i.set(4, 4, 4, 4);
            else if (c instanceof JButton) i.set(6, 10, 6, 10);
            else i.set(9, 12, 9, 12);
            return i;
        }

        @Override public void paintBorder(Component c, Graphics g0, int x, int y, int w, int h) {
            Graphics2D g = aa(g0);
            if (focused(c) && c.isEnabled()) {
                g.setColor(FOCUS);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(x + 1, y + 1, w - 3, h - 3, RADIUS, RADIUS);
            } else {
                g.setColor(LINE);
                g.drawRoundRect(x + 1, y + 1, w - 3, h - 3, RADIUS, RADIUS);
            }
            g.dispose();
        }
    }

    // ---------- nút ----------

    public enum Kind { PRIMARY, SECONDARY, DANGER, ON_DARK }

    public static JButton button(String text, Kind kind) { return new Btn(text, kind); }

    private static final class Btn extends JButton {
        private final Kind kind;

        Btn(String text, Kind kind) {
            super(text);
            this.kind = kind;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setFont(font(600, 14f));
            setBorder(new EmptyBorder(12, 20, 12, 20));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0);
            ButtonModel m = getModel();
            boolean down = m.isArmed() && m.isPressed(), hover = m.isRollover();
            Color fill, line = null, text;
            switch (kind) {
                case PRIMARY -> {
                    fill = down ? mix(INK, Color.BLACK, 0.25f) : hover ? mix(INK, Color.WHITE, 0.14f) : INK;
                    text = Color.WHITE;
                }
                case DANGER -> {
                    fill = down ? new Color(0xF4D9D3) : hover ? new Color(0xFBECE8) : SURFACE;
                    line = new Color(0xE6B4A9);
                    text = DANGER;
                }
                case ON_DARK -> {
                    fill = down ? new Color(0xD5DAE8) : hover ? Color.WHITE : new Color(0xE9ECF4);
                    text = INK;
                }
                default -> {
                    fill = down ? TRACK : hover ? new Color(0xFAF8F4) : SURFACE;
                    line = hover ? mix(LINE, INK_2, 0.45f) : LINE;
                    text = INK;
                }
            }
            if (!isEnabled()) g.setComposite(AlphaComposite.SrcOver.derive(0.4f));
            int w = getWidth(), h = getHeight();
            g.setColor(fill);
            g.fillRoundRect(2, 2, w - 4, h - 4, RADIUS, RADIUS);
            if (line != null) {
                g.setColor(line);
                g.drawRoundRect(2, 2, w - 5, h - 5, RADIUS, RADIUS);
            }
            if (isFocusOwner() && isEnabled()) {
                g.setColor(kind == Kind.ON_DARK ? Color.WHITE : FOCUS);
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(0, 0, w - 1, h - 1, RADIUS + 4, RADIUS + 4);
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            String s = getText();
            g.setColor(text);
            g.drawString(s, (w - fm.stringWidth(s)) / 2f, (h - fm.getAscent() - fm.getDescent()) / 2f + fm.getAscent());
            g.dispose();
        }
    }

    // ---------- thanh cuộn mảnh ----------

    public static final class ThinScrollBar extends BasicScrollBarUI {
        public static ComponentUI createUI(JComponent c) { return new ThinScrollBar(); }

        @Override protected void installDefaults() {
            super.installDefaults();
            scrollbar.setOpaque(false);
        }

        @Override protected JButton createDecreaseButton(int orientation) { return none(); }
        @Override protected JButton createIncreaseButton(int orientation) { return none(); }

        private static JButton none() {
            JButton b = new JButton();
            Dimension zero = new Dimension(0, 0);
            b.setPreferredSize(zero);
            b.setMinimumSize(zero);
            b.setMaximumSize(zero);
            return b;
        }

        @Override public Dimension getPreferredSize(JComponent c) {
            return scrollbar.getOrientation() == VERTICAL ? new Dimension(12, 48) : new Dimension(48, 12);
        }

        @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) { }

        @Override protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = aa(g0);
            g.setColor(isThumbRollover() || isDragging ? mix(LINE, INK_2, 0.7f) : mix(LINE, INK_2, 0.3f));
            boolean v = scrollbar.getOrientation() == VERTICAL;
            int t = 6;
            if (v) g.fillRoundRect(r.x + (r.width - t) / 2, r.y + 2, t, r.height - 4, t, t);
            else g.fillRoundRect(r.x + 2, r.y + (r.height - t) / 2, r.width - 4, t, t, t);
            g.dispose();
        }
    }
}
