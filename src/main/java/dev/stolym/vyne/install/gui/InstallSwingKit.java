/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.install.gui;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InstallSwingKit {
    private InstallSwingKit() {}

    private static final Map<Integer, Icon> FOLDER_ICONS = new ConcurrentHashMap<>();

    public static final Color BG = new Color(0x14, 0x16, 0x19);
    public static final Color BG_CARD = new Color(0x1D, 0x1F, 0x23);
    public static final Color BG_HOVER = new Color(0x27, 0x2A, 0x2F);
    public static final Color BG_SEL = new Color(0x1F, 0x2B, 0x23);
    public static final Color BORDER = new Color(0x2C, 0x2F, 0x34);
    public static final Color BORDER_HI = new Color(0x3B, 0x3E, 0x45);
    public static final Color ACCENT = new Color(0x20, 0xB0, 0x4B);
    public static final Color ACCENT_HI = new Color(0x2C, 0xC9, 0x5D);
    public static final Color ACCENT_SH = new Color(0x16, 0x8B, 0x3A);
    public static final Color TEXT = new Color(0xF4, 0xF5, 0xF7);
    public static final Color TEXT_DIM = new Color(0x9D, 0xA2, 0xAB);
    public static final Color TEXT_FAINT = new Color(0x6C, 0x70, 0x78);

    private static final String BTN_KIND = "vyne.install.btnKind";

    public static Icon folderGlyphIcon(int size) {
        return FOLDER_ICONS.computeIfAbsent(Math.max(12, size), InstallSwingKit::buildFolderGlyph);
    }

    private static Icon buildFolderGlyph(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        float pad = Math.max(1.5f, s * 0.1f);
        float bodyW = s - pad * 2f;
        float tabW = bodyW * 0.5f;
        float tabH = Math.max(4f, s * 0.22f);
        float arcBody = Math.min(5f, s * 0.22f);
        float arcTab = Math.min(3.5f, s * 0.16f);
        float tabX = pad;
        float tabY = pad;
        float bodyX = pad;
        float bodyY = tabY + tabH * 0.55f;
        float bodyH = s - pad - bodyY;

        var body = new RoundRectangle2D.Float(bodyX, bodyY, bodyW, bodyH, arcBody, arcBody);
        var tab = new RoundRectangle2D.Float(tabX, tabY, tabW, tabH, arcTab, arcTab);

        g.setColor(new Color(BG_CARD.getRed(), BG_CARD.getGreen(), BG_CARD.getBlue(), 250));
        g.fill(body);
        Color tabFill = new Color(
            (BG_CARD.getRed() + BORDER_HI.getRed()) / 2,
            (BG_CARD.getGreen() + BORDER_HI.getGreen()) / 2,
            (BG_CARD.getBlue() + BORDER_HI.getBlue()) / 2);
        g.setColor(tabFill);
        g.fill(tab);

        g.setStroke(new BasicStroke(Math.max(1f, s / 18f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(BORDER_HI);
        g.draw(body);
        g.draw(tab);

        g.setStroke(new BasicStroke(Math.max(1f, s / 22f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 200));
        float slotY = bodyY + bodyH * 0.42f;
        g.draw(new RoundRectangle2D.Float(
            bodyX + bodyW * 0.12f,
            slotY,
            bodyW * 0.76f,
            Math.max(2f, bodyH * 0.14f),
            2f, 2f));
        g.dispose();
        return new ImageIcon(img);
    }

    public static JButton primaryButton(String text, ActionListener onClick) {
        JButton b = new JButton(text);
        b.putClientProperty(BTN_KIND, "primary");
        b.setUI(new VyneFlatButtonUI());
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(b.getFont().deriveFont(Font.BOLD, 13f));
        b.setForeground(Color.WHITE);
        b.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        b.addActionListener(onClick);
        return b;
    }

    public static JButton ghostButton(String text, ActionListener onClick) {
        JButton b = new JButton(text);
        b.putClientProperty(BTN_KIND, "ghost");
        b.setUI(new VyneFlatButtonUI());
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setRolloverEnabled(true);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 13f));
        b.setForeground(TEXT);
        b.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        b.addActionListener(onClick);
        return b;
    }

    public static final class RoundedBorder extends AbstractBorder {
        private final Color color;
        private final int thickness;
        private final int radius;

        public RoundedBorder(Color color, int thickness, int radius) {
            this.color = color;
            this.thickness = thickness;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x, y, w - 1, h - 1, radius, radius);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 1, thickness + 1, thickness + 1, thickness + 1);
        }
    }

    public static final class GreenScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = BORDER_HI;
            thumbDarkShadowColor = BORDER_HI;
            thumbHighlightColor = BORDER_HI;
            thumbLightShadowColor = BORDER_HI;
            trackColor = BG;
            trackHighlightColor = BG;
        }

        @Override
        protected JButton createDecreaseButton(int o) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int o) {
            return zeroButton();
        }

        private static JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BORDER_HI);
            g2.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 6, 6);
            g2.dispose();
        }
    }

    public static final class GreenProgressBarUI extends BasicProgressBarUI {
        @Override
        protected void paintDeterminate(Graphics g, JComponent c) {
            JProgressBar bar = (JProgressBar) c;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth(), h = c.getHeight();
            g2.setColor(BG_CARD);
            g2.fillRoundRect(0, 0, w, h, 10, 10);
            int fill = (int) (w * bar.getPercentComplete());
            if (fill > 0) {
                g2.setColor(ACCENT);
                g2.fillRoundRect(0, 0, fill, h, 10, 10);
            }
            String s = bar.getString();
            if (bar.isStringPainted() && s != null && !s.isEmpty()) {
                g2.setFont(c.getFont());
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(s)) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(s, tx, ty);
            }
            g2.dispose();
        }
    }

    private static final class VyneFlatButtonUI extends BasicButtonUI {
        private static final int ARC = 10;

        @Override
        public Dimension getPreferredSize(JComponent c) {
            AbstractButton b = (AbstractButton) c;
            Dimension sup = super.getPreferredSize(c);
            Font font = b.getFont();
            FontMetrics fm = b.getFontMetrics(font);
            Insets ins = b.getInsets();
            String text = b.getText();
            int tw = (text == null || text.isEmpty()) ? 0 : fm.stringWidth(text);
            int th = fm.getHeight();
            int w = ins.left + ins.right + tw + 8;
            int h = ins.top + ins.bottom + Math.max(th + 4, 22);
            if (sup != null) {
                w = Math.max(w, sup.width);
                h = Math.max(h, sup.height);
            }
            return new Dimension(w, h);
        }

        @Override
        public Dimension getMinimumSize(JComponent c) {
            return getPreferredSize(c);
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            AbstractButton b = (AbstractButton) c;
            String kind = (String) b.getClientProperty(BTN_KIND);
            boolean primary = "primary".equals(kind);
            ButtonModel m = b.getModel();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = c.getWidth();
            int h = c.getHeight();

            if (primary) {
                Color fill = ACCENT;
                Color edge = ACCENT_SH;
                if (!b.isEnabled()) {
                    fill = new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 110);
                    edge = BORDER;
                } else if (m.isPressed()) {
                    fill = ACCENT_SH;
                } else if (m.isRollover()) {
                    fill = ACCENT_HI;
                }
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC));
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(edge);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, ARC, ARC));
            } else {
                Color fill = BG_CARD;
                Color edge = BORDER;
                if (!b.isEnabled()) {
                    fill = BG;
                    edge = BORDER;
                } else if (m.isPressed()) {
                    fill = new Color(0x22, 0x25, 0x2A);
                    edge = BORDER_HI;
                } else if (m.isRollover()) {
                    fill = BG_HOVER;
                    edge = BORDER_HI;
                }
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h, ARC, ARC));
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(edge);
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, ARC, ARC));
            }

            if (b.isFocusOwner()) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 90));
                g2.draw(new RoundRectangle2D.Float(2, 2, w - 4, h - 4, ARC - 2, ARC - 2));
            }

            Font font = b.getFont();
            g2.setFont(font);
            String text = b.getText();
            if (text != null && !text.isEmpty()) {
                FontMetrics fm = g2.getFontMetrics(font);
                int tw = fm.stringWidth(text);
                Insets ins = b.getInsets();
                int innerW = Math.max(0, w - ins.left - ins.right);
                int tx = ins.left + (innerW - tw) / 2;
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                Color fg = b.getForeground();
                if (!b.isEnabled()) {
                    fg = primary ? new Color(255, 255, 255, 140) : TEXT_DIM;
                }
                g2.setColor(fg);
                if (tw <= innerW) {
                    g2.drawString(text, tx, ty);
                } else {
                    String ell = "…";
                    int ew = fm.stringWidth(ell);
                    int max = innerW - ew;
                    int cut = text.length();
                    while (cut > 0 && fm.stringWidth(text.substring(0, cut)) > max) {
                        cut--;
                    }
                    g2.drawString((cut > 0 ? text.substring(0, cut) : "") + ell,
                        ins.left, ty);
                }
            }
            g2.dispose();
        }
    }
}
