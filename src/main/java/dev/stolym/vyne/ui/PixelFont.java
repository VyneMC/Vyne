/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;

public final class PixelFont {
    public static final int HEIGHT = 8;
    private static final String ATLAS = "assets/minecraft/textures/font/ascii.png";

    private static volatile PixelFont shared;

    private final boolean[][] glyphs = new boolean[256][];
    private final int[] advances = new int[256];
    private final int cell;

    private PixelFont(BufferedImage atlas) {
        this.cell = atlas.getWidth() / 16;
        for (int c = 0; c < 256; c++) {
            int ox = (c % 16) * cell;
            int oy = (c / 16) * cell;
            boolean[] mask = new boolean[cell * cell];
            int last = -1;
            for (int y = 0; y < cell; y++) {
                for (int x = 0; x < cell; x++) {
                    boolean on = ((atlas.getRGB(ox + x, oy + y) >>> 24) & 0xFF) > 0x80;
                    mask[y * cell + x] = on;
                    if (on && x > last) last = x;
                }
            }
            glyphs[c] = mask;
            int width = c == ' ' ? 3 : last + 1;
            advances[c] = Math.max(1, width * HEIGHT / cell) + 1;
        }
    }

    public static PixelFont get() {
        PixelFont font = shared;
        if (font == null) {
            synchronized (PixelFont.class) {
                font = shared;
                if (font == null) {
                    font = new PixelFont(loadAtlas());
                    shared = font;
                }
            }
        }
        return font;
    }

    private static BufferedImage loadAtlas() {
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader(),
                PixelFont.class.getClassLoader(),
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try (InputStream in = loader.getResourceAsStream(ATLAS)) {
                if (in == null) continue;
                BufferedImage image = ImageIO.read(in);
                if (image != null && image.getWidth() == image.getHeight() && image.getWidth() % 16 == 0) {
                    return image;
                }
            } catch (Exception ignored) {
            }
        }
        return fallbackAtlas();
    }

    private static BufferedImage fallbackAtlas() {
        BufferedImage atlas = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
            g.setColor(Color.WHITE);
            for (int c = 33; c < 127; c++) {
                g.drawString(String.valueOf((char) c), (c % 16) * 8, (c / 16) * 8 + 7);
            }
        } finally {
            g.dispose();
        }
        return atlas;
    }

    private int index(char ch) {
        return ch >= 32 && ch < 127 ? ch : '?';
    }

    public int advance(char ch) {
        return advances[index(ch)];
    }

    public int width(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); i++) width += advance(text.charAt(i));
        return Math.max(0, width - 1);
    }

    public String fit(String text, int maxWidth) {
        if (width(text) <= maxWidth) return text;
        String cut = text;
        while (cut.length() > 1 && width(cut + "...") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "...";
    }

    public void draw(int[] pixels, int stride, int height, String text, int x, int y, int color) {
        int penX = x;
        for (int i = 0; i < text.length(); i++) {
            int c = index(text.charAt(i));
            boolean[] mask = glyphs[c];
            for (int gy = 0; gy < HEIGHT; gy++) {
                int py = y + gy;
                if (py < 0 || py >= height) continue;
                int sy = gy * cell / HEIGHT;
                for (int gx = 0; gx < HEIGHT; gx++) {
                    int sx = gx * cell / HEIGHT;
                    if (!mask[sy * cell + sx]) continue;
                    int px = penX + gx;
                    if (px < 0 || px >= stride) continue;
                    pixels[py * stride + px] = color;
                }
            }
            penX += advances[c];
        }
    }

    public BufferedImage render(String text, int color) {
        int width = Math.max(1, width(text));
        BufferedImage image = new BufferedImage(width, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * HEIGHT];
        draw(pixels, width, HEIGHT, text, 0, 0, color);
        image.setRGB(0, 0, width, HEIGHT, pixels, 0, width);
        return image;
    }
}
