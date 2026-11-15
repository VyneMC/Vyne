/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.ui;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class VyneLoadingTheme {
    public static final String FILE_NAME = "vyne-loading-screen.properties";

    private static final String DEFAULTS = """
            # Vyne loading screen
            # Colours are RRGGBB hex values. Delete this file to restore the defaults.

            # Show Vyne's window as soon as the game starts
            earlyWindow=true

            background=0D0D1A
            text=E0E0E0
            muted=6B7389

            bar.border=5A5A70
            bar.background=1A1A2B
            bar.fill=23B169

            logo.idle=34344A
            logo.trail=1C6E47
            logo.lit=23B169

            mod.done=23B169
            mod.failed=D03A35

            show.memory=true
            show.mods=true
            show.activity=true
            """;

    private static volatile VyneLoadingTheme current;

    public final boolean earlyWindow;
    public final int background;
    public final int text;
    public final int muted;
    public final int barBorder;
    public final int barBackground;
    public final int barFill;
    public final int logoIdle;
    public final int logoTrail;
    public final int logoLit;
    public final int modDone;
    public final int modFailed;
    public final boolean showMemory;
    public final boolean showMods;
    public final boolean showActivity;

    private VyneLoadingTheme(Properties p) {
        earlyWindow = flag(p, "earlyWindow", true);
        background = color(p, "background", 0x0D0D1A);
        text = color(p, "text", 0xE0E0E0);
        muted = color(p, "muted", 0x6B7389);
        barBorder = color(p, "bar.border", 0x5A5A70);
        barBackground = color(p, "bar.background", 0x1A1A2B);
        barFill = color(p, "bar.fill", 0x23B169);
        logoIdle = color(p, "logo.idle", 0x34344A);
        logoTrail = color(p, "logo.trail", 0x1C6E47);
        logoLit = color(p, "logo.lit", 0x23B169);
        modDone = color(p, "mod.done", 0x23B169);
        modFailed = color(p, "mod.failed", 0xD03A35);
        showMemory = flag(p, "show.memory", true);
        showMods = flag(p, "show.mods", true);
        showActivity = flag(p, "show.activity", true);
    }

    public static VyneLoadingTheme current() {
        VyneLoadingTheme theme = current;
        if (theme == null) {
            String dir = System.getProperty("vyne.gameDir");
            theme = load(dir != null && !dir.isBlank() ? Path.of(dir) : null);
        }
        return theme;
    }

    public static synchronized VyneLoadingTheme load(Path gameDir) {
        if (current != null) return current;
        Properties props = new Properties();
        try {
            props.load(new java.io.StringReader(DEFAULTS));
        } catch (IOException ignored) {
        }
        if (gameDir != null) {
            Path file = gameDir.resolve("config").resolve(FILE_NAME);
            try {
                if (Files.isRegularFile(file)) {
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        props.load(reader);
                    }
                } else {
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, DEFAULTS, StandardCharsets.UTF_8);
                }
            } catch (IOException | IllegalArgumentException ignored) {
            }
        }
        current = new VyneLoadingTheme(props);
        return current;
    }

    private static int color(Properties p, String key, int fallback) {
        String value = p.getProperty(key);
        if (value == null) return 0xFF000000 | fallback;
        value = value.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.startsWith("0x") || value.startsWith("0X")) value = value.substring(2);
        try {
            return 0xFF000000 | (Integer.parseInt(value, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return 0xFF000000 | fallback;
        }
    }

    private static boolean flag(Properties p, String key, boolean fallback) {
        String value = p.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }
}
