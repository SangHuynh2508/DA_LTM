package lpp.common;

import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.util.Arrays;

/**
 * Biểu đồ kết quả: câu hỏi, trạng thái + tổng phiếu, mỗi lựa chọn một hàng (huy hiệu, nhãn, số phiếu, %, thanh ngang).
 * Khi có phiếu mới, thanh chạy dần tới độ dài mới; khi câu hỏi đóng, các lựa chọn không dẫn đầu được làm nhạt.
 */
public final class BarChart extends JPanel {
    private String title = "", hint = "";
    private String[] labels = new String[0];
    private int[] counts = new int[0];
    private double[] shown = new double[0]; // tỉ lệ đang vẽ (0..1), tiến dần tới tỉ lệ thật
    private boolean closed;
    private boolean card;
    private final Timer anim = new Timer(15, e -> step());

    public BarChart() {
        setBackground(Ui.SURFACE);
        setBorder(new EmptyBorder(28, 32, 28, 32));
        setPreferredSize(new Dimension(600, 420));
    }

    /** Vẽ nền trắng bo góc có viền (dùng khi đặt biểu đồ trên nền ngà). */
    public void setCard(boolean card) {
        this.card = card;
        setOpaque(!card);
    }

    /** Trạng thái chưa có câu hỏi: tiêu đề và một dòng gợi ý ở giữa. */
    public void setEmpty(String title, String hint) {
        this.title = title;
        this.hint = hint;
        labels = new String[0];
        counts = new int[0];
        shown = new double[0];
        anim.stop();
        repaint();
    }

    public void setData(String title, String[] labels, int[] counts, boolean closed) {
        if (!title.equals(this.title) || !Arrays.equals(labels, this.labels)) shown = new double[labels.length]; // câu mới: thanh mọc từ 0
        this.title = title;
        this.labels = labels;
        this.counts = counts;
        this.closed = closed;
        anim.start();
        repaint();
    }

    private int total() {
        int t = 0;
        for (int c : counts) t += c;
        return t;
    }

    private int count(int i) { return i < counts.length ? counts[i] : 0; }

    private void step() {
        int total = total();
        boolean moving = false;
        for (int i = 0; i < shown.length; i++) {
            double target = total == 0 ? 0 : (double) count(i) / total;
            double d = target - shown[i];
            if (Math.abs(d) < 0.002) shown[i] = target;
            else {
                shown[i] += d * 0.2; // giảm dần: nhanh lúc đầu, chậm lại khi gần đích
                moving = true;
            }
        }
        repaint();
        if (!moving) anim.stop();
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = Ui.aa(g0);
        if (card) {
            g.setColor(Ui.SURFACE);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(Ui.LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
        }
        Insets in = getInsets();
        int x0 = in.left, w = getWidth() - in.left - in.right;

        if (labels.length == 0) {
            g.setFont(Ui.font(600, 22f));
            FontMetrics fm = g.getFontMetrics();
            int cy = getHeight() / 2 - 16;
            g.setColor(Ui.INK);
            g.drawString(title, (getWidth() - fm.stringWidth(title)) / 2f, cy);
            g.setFont(Ui.font(400, 14f));
            fm = g.getFontMetrics();
            g.setColor(Ui.INK_2);
            int y = cy + 14;
            for (String line : Ui.wrapBalanced(hint, fm, Math.min(w, 480), 3)) {
                y += fm.getHeight();
                g.drawString(line, (getWidth() - fm.stringWidth(line)) / 2f, y);
            }
            g.dispose();
            return;
        }

        int y = in.top;
        g.setFont(Ui.font(600, 26f));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(Ui.INK);
        for (String line : Ui.wrapBalanced(title, fm, w, 3)) {
            g.drawString(line, x0, y + fm.getAscent());
            y += fm.getHeight();
        }

        int total = total();
        y += 8;
        g.setFont(Ui.font(500, 14f));
        fm = g.getFontMetrics();
        g.setColor(closed ? Ui.INK_2 : Ui.LIVE);
        g.fillOval(x0, y + (fm.getHeight() - 8) / 2, 8, 8);
        g.setColor(Ui.INK_2);
        g.drawString((closed ? "Đã đóng" : "Đang mở") + "  ·  " + total + " phiếu", x0 + 16, y + fm.getAscent());
        y += fm.getHeight() + 22;

        int n = labels.length;
        int rowH = Math.max(52, Math.min(92, (getHeight() - in.bottom - y) / n));
        int max = 0;
        for (int i = 0; i < n; i++) max = Math.max(max, count(i));
        int badge = 26, track = Math.max(10, Math.min(16, rowH / 6));
        for (int i = 0; i < n; i++) {
            int ry = y + i * rowH;
            int c = count(i), pct = total == 0 ? 0 : Math.round(100f * c / total);
            boolean faded = closed && max > 0 && c < max;

            String num = String.valueOf(c), pctText = pct + "%";
            g.setFont(Ui.font(500, 15f));
            FontMetrics fp = g.getFontMetrics();
            g.setFont(Ui.font(700, 18f));
            FontMetrics fn = g.getFontMetrics();
            int rightW = fn.stringWidth(num) + 10 + Math.max(fp.stringWidth("100%"), fp.stringWidth(pctText));
            int base = ry + badge / 2 + (fn.getAscent() - fn.getDescent()) / 2;

            Ui.badge(g, i, x0, ry, badge);
            g.setFont(Ui.font(500, 17f));
            FontMetrics fl = g.getFontMetrics();
            g.setColor(faded ? Ui.INK_2 : Ui.INK);
            g.drawString(Ui.ellipsize(labels[i], fl, w - badge - 14 - rightW - 16), x0 + badge + 14, base);

            g.setFont(Ui.font(700, 18f));
            g.setColor(Ui.INK);
            int px = x0 + w - fp.stringWidth(pctText);
            g.drawString(num, px - 10 - fn.stringWidth(num), base);
            g.setFont(Ui.font(500, 15f));
            g.setColor(Ui.INK_2);
            g.drawString(pctText, px, base);

            int ty = ry + badge + 10;
            g.setColor(Ui.TRACK);
            g.fillRoundRect(x0, ty, w, track, track, track);
            double s = i < shown.length ? shown[i] : 0;
            int fw = (int) Math.round(w * s);
            if (fw > 0) {
                Color col = Ui.answer(i);
                g.setColor(faded ? Ui.mix(col, Ui.SURFACE, 0.6f) : col);
                g.fillRoundRect(x0, ty, Math.max(fw, track), track, track, track);
            }
        }
        g.dispose();
    }
}
