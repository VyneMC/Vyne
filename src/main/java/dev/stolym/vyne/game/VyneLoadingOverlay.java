/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.game;

import dev.stolym.vyne.ui.PixelFont;
import dev.stolym.vyne.util.VyneLogger;
import net.minecraft.class_1011;
import net.minecraft.class_1043;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;

public final class VyneLoadingOverlay {
    private static final long MORPH_MS = 450L;

    private static final String WORD = "Vyne";
    private static final long HOLD_MS = 350L;
    private static final long SLIDE_MS = 700L;
    private static final long LETTER_MS = 110L;

    private static final int LOGO_WIDTH = 254;
    private static final int LOGO_HEIGHT = 258;
    private static final class_2960 LOGO = class_2960.method_60655("vyne", "textures/gui/loading_logo.png");
    private static final class_2960 WORDMARK = class_2960.method_60655("vyne", "textures/gui/loading_wordmark.png");

    private static boolean registered;
    private static boolean unavailable;
    private static boolean wordmarkReady;
    private static int wordmarkWidth;
    private static int[] letterEnds = new int[0];

    private VyneLoadingOverlay() {}

    public static boolean isActive() {
        return registered && !unavailable;
    }

    private static void register() {
        if (registered || unavailable) return;
        try (InputStream in = VyneLoadingOverlay.class.getResourceAsStream("/vyne/assets/vyne_logo.png")) {
            if (in == null) {
                unavailable = true;
                return;
            }
            class_310.method_1551().method_1531().method_4616(LOGO, new class_1043(class_1011.method_4309(in)));
            registered = true;
        } catch (Throwable t) {
            unavailable = true;
            VyneLogger.warn("[LoadingScreen] Vyne logo unavailable, keeping the default: " + t);
            return;
        }
        try {
            PixelFont font = PixelFont.get();
            BufferedImage word = font.render(WORD, 0xFFFFFFFF);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(word, "png", png);
            class_310.method_1551().method_1531().method_4616(WORDMARK,
                    new class_1043(class_1011.method_4309(new ByteArrayInputStream(png.toByteArray()))));
            wordmarkWidth = word.getWidth();
            letterEnds = new int[WORD.length()];
            int pen = 0;
            for (int i = 0; i < WORD.length(); i++) {
                pen += font.advance(WORD.charAt(i));
                letterEnds[i] = Math.min(wordmarkWidth, pen - 1);
            }
            wordmarkReady = true;
        } catch (Throwable t) {
            VyneLogger.warn("[LoadingScreen] Vyne wordmark unavailable, showing the logo only: " + t);
        }
    }

    public static int background() {
        return dev.stolym.vyne.ui.VyneLoadingTheme.current().background;
    }

    public static boolean drawLogo(class_332 graphics, int left, int top, int halfWidth, int height, long elapsed,
                                   float[] from) {
        register();
        if (!isActive()) return false;
        long morph = from != null ? MORPH_MS : 0L;

        int centerX = left + halfWidth;
        int logoHeight = Math.min(height * 2, halfWidth * 2);
        int logoWidth = logoHeight * LOGO_WIDTH / LOGO_HEIGHT;
        int logoY = top + height / 2 - logoHeight / 2 - height / 4;

        int textScale = Math.max(1, Math.round(logoHeight * 0.42f / PixelFont.HEIGHT));
        int textWidth = wordmarkReady ? wordmarkWidth * textScale : 0;
        int gap = Math.round(logoWidth * 0.18f);
        int startX = centerX - logoWidth / 2;
        int endX = wordmarkReady ? centerX - (logoWidth + gap + textWidth) / 2 : startX;

        if (from != null && elapsed < morph) {
            float t = smooth(clamp(elapsed / (float) morph));
            int guiWidth = graphics.method_51421();
            int guiHeight = graphics.method_51443();
            int fromX = Math.round(from[0] * guiWidth);
            int fromY = Math.round(from[1] * guiHeight);
            int fromSize = Math.round(from[2] * guiWidth);
            int toX = startX;
            int size = Math.round(fromSize + (logoHeight - fromSize) * t);
            int x = Math.round(fromX + (toX - fromX) * t);
            int y = Math.round(fromY + (logoY - fromY) * t);
            graphics.method_25293(LOGO, x, y, size * LOGO_WIDTH / LOGO_HEIGHT, size, 0f, 0f,
                    LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
            return true;
        }
        elapsed -= morph;

        float slide = wordmarkReady ? clamp((elapsed - HOLD_MS) / (float) SLIDE_MS) : 0f;
        float eased = smooth(slide);
        int logoX = Math.round(startX + (endX - startX) * eased);

        graphics.method_25293(LOGO, logoX, logoY, logoWidth, logoHeight, 0f, 0f,
                LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);

        if (wordmarkReady && slide >= 1f) {
            long typing = elapsed - HOLD_MS - SLIDE_MS;
            int letters = (int) Math.min(letterEnds.length, typing / LETTER_MS + 1);
            if (letters > 0) {
                int shown = letterEnds[letters - 1];
                int textX = logoX + logoWidth + gap;
                int textY = logoY + (logoHeight - PixelFont.HEIGHT * textScale) / 2;
                graphics.method_25293(WORDMARK, textX, textY, shown * textScale, PixelFont.HEIGHT * textScale,
                        0f, 0f, shown, PixelFont.HEIGHT, wordmarkWidth, PixelFont.HEIGHT);
            }
        }
        return true;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
