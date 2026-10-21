package lpp.common;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Biểu đồ cột ngang: tiêu đề, tổng phiếu, mỗi lựa chọn một cột kèm số phiếu và %. */
public final class BarChart extends JPanel {
    private static final Color[] COLORS = {
            new Color(0xE21B3C), new Color(0x1368CE), new Color(0xD89E00),
            new Color(0x26890C), new Color(0x864CBF), new Color(0x0AA3A3)};
    private String title = "";
    private String[] labels = new String[0];
    private int[] counts = new int[0];

    public BarChart() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(560, 380));
    }

    public void setData(String title, String[] labels, int[] counts) {
        this.title = title;
        this.labels = labels;
        this.counts = counts;
        repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int pad = 16;
        int total = 0;
        for (int c : counts) total += c;

        g.setColor(Color.DARK_GRAY);
        g.setFont(getFont().deriveFont(Font.BOLD, 18f));
        g.drawString(title, pad, pad + 18);
        g.setFont(getFont().deriveFont(14f));
        if (labels.length == 0) return;
        g.drawString("Tổng: " + total + " phiếu", pad, pad + 42);

        int top = pad + 60;
        int rowH = Math.max(24, Math.min(56, (getHeight() - top - pad) / labels.length));
        int labelW = 170;
        int barMax = Math.max(0, getWidth() - labelW - 3 * pad - 90);
        for (int i = 0; i < labels.length; i++) {
            int y = top + i * rowH;
            int count = i < counts.length ? counts[i] : 0;
            int barW = Math.max(2, total == 0 ? 0 : (int) Math.round((double) barMax * count / total));
            int pct = total == 0 ? 0 : Math.round(100f * count / total);
            g.setColor(Color.DARK_GRAY);
            g.drawString(shorten((char) ('A' + i) + ". " + labels[i]), pad, y + rowH / 2 + 5);
            g.setColor(COLORS[i % COLORS.length]);
            g.fillRoundRect(pad + labelW, y + 6, barW, rowH - 12, 8, 8);
            g.setColor(Color.DARK_GRAY);
            g.drawString(count + " (" + pct + "%)", pad + labelW + barW + 8, y + rowH / 2 + 5);
        }
    }

    private static String shorten(String s) { return s.length() <= 22 ? s : s.substring(0, 21) + "…"; }
}
