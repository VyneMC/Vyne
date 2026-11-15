/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the Licenses/LICENSE_VYNE file in the project root.
 */

package dev.stolym.vyne.install.gui;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GuiAssets {
    private GuiAssets() {}

    private static final Map<String, BufferedImage> RAW = new ConcurrentHashMap<>();
    private static final Map<String, ImageIcon> SCALED = new ConcurrentHashMap<>();

    private static final String BASE = "/vyne/assets/";

    public static BufferedImage load(String relative) {
        return RAW.computeIfAbsent(relative, GuiAssets::readSafely);
    }

    public static ImageIcon icon(String relative, int size) {
        String key = relative + "@" + size;
        return SCALED.computeIfAbsent(key, k -> {
            BufferedImage raw = load(relative);
            if (raw == null) return new ImageIcon(placeholder(size));
            Image scaled = raw.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        });
    }

    public static ImageIcon launcherIcon(LauncherProfile.Kind kind, int size) {
        String file = switch (kind) {
            case PRISM -> "launchers/prism.png";
            case MULTIMC, POLYMC -> "launchers/multimc.png";
            case MODRINTH -> "launchers/modrinth.png";
            case CURSEFORGE -> "launchers/curseforge.png";
            default -> "vyne_logo.png";
        };
        return icon(file, size);
    }

    private static BufferedImage readSafely(String relative) {
        try (InputStream in = GuiAssets.class.getResourceAsStream(BASE + relative)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (Throwable t) {
            return null;
        }
    }

    private static BufferedImage placeholder(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x2B, 0x2E, 0x38));
        g.fillRoundRect(0, 0, size - 1, size - 1, 8, 8);
        g.setColor(new Color(0x20, 0xB0, 0x4B));
        int pad = Math.max(2, size / 8);
        g.fillRoundRect(pad, pad, size - pad * 2, size - pad * 2, 6, 6);
        g.dispose();
        return img;
    }
}
