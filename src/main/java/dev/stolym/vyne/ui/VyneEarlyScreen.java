/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.ui;

import dev.stolym.vyne.containment.VyneModStateRegistry;

import java.util.List;

public final class VyneEarlyScreen {
    private static final int[] CYCLE = {0, 1, 3, 2};
    private static final int TILE = 7;
    private static final int TILE_GAP = 2;
    private static final int MAX_TILE_ROWS = 6;

    private final PixelFont font = PixelFont.get();
    private final VyneLoadingTheme theme = VyneLoadingTheme.current();
    private final String version;

    private volatile int logoX;
    private volatile int logoY;
    private volatile int logoSize;
    private volatile int paintedWidth;
    private volatile int paintedHeight;

    public VyneEarlyScreen(String version) {
        this.version = version;
    }

    public static int scaleFor(int width, int height) {
        int scale = 1;
        while (width / (scale + 1) >= 320 && height / (scale + 1) >= 240) scale++;
        return scale;
    }

    public float[] logoFraction() {
        int w = paintedWidth;
        int h = paintedHeight;
        if (w <= 0 || h <= 0 || logoSize <= 0) return null;
        return new float[] {(float) logoX / w, (float) logoY / h, (float) logoSize / w, (float) logoSize / h};
    }

    public void paint(int[] px, int w, int h, long now) {
        java.util.Arrays.fill(px, theme.background);

        int barWidth = Math.min(w - 40, 320);
        int barX = (w - barWidth) / 2;

        if (theme.showMemory) {
            Runtime runtime = Runtime.getRuntime();
            long max = runtime.maxMemory();
            long used = runtime.totalMemory() - runtime.freeMemory();
            float memory = max > 0 ? (float) used / max : 0f;
            int memoryColor = memory < 0.6f ? theme.barFill : memory < 0.85f ? 0xFFE0B020 : theme.modFailed;
            bar(px, w, h, barX, 8, barWidth, 12, memory, memoryColor);
            String memoryText = "Memory: " + (used >> 20) + " MB / " + (max >> 20) + " MB";
            font.draw(px, w, h, memoryText, (w - font.width(memoryText)) / 2, 10, theme.text);
        }

        List<VyneLoadingScreen.ModTile> tiles = theme.showMods ? VyneLoadingScreen.modTiles() : List.of();
        int columns = Math.max(1, (barWidth + TILE_GAP) / (TILE + TILE_GAP));
        int tileRows = tiles.isEmpty() ? 0 : Math.min(MAX_TILE_ROWS, (tiles.size() + columns - 1) / columns);
        int modsHeight = tiles.isEmpty() ? 0 : 14 + tileRows * (TILE + TILE_GAP) + 4;
        int activityHeight = theme.showActivity ? 3 * 10 : 0;

        int cellSize = 18;
        int gap = 4;
        int size = cellSize * 2 + gap;
        int barsHeight = 48;
        int blockHeight = size + 18 + barsHeight + modsHeight + activityHeight;
        int top = Math.max(theme.showMemory ? 28 : 8, (h - 12 - blockHeight) / 2);
        int lx = (w - size) / 2;
        int step = (int) ((now / 220L) % 4);
        int lit = CYCLE[step];
        int trail = CYCLE[(step + 3) % 4];
        for (int i = 0; i < 4; i++) {
            int cx = lx + (i % 2) * (cellSize + gap);
            int cy = top + (i / 2) * (cellSize + gap);
            int color = i == lit ? theme.logoLit : i == trail ? theme.logoTrail : theme.logoIdle;
            cell(px, w, h, cx, cy, cellSize, color);
        }
        logoX = lx;
        logoY = top;
        logoSize = size;
        paintedWidth = w;
        paintedHeight = h;

        int y = top + size + 18;
        String stage = VyneLoadingScreen.status.get();
        int percent = Math.max(0, Math.min(100, VyneLoadingScreen.progress.get()));
        labelledBar(px, w, h, barX, y, barWidth, stage == null ? "" : stage, percent + "%", percent / 100f);

        String task = VyneLoadingScreen.taskLabel();
        int total = VyneLoadingScreen.taskTotal();
        if (task != null && total > 0) {
            int current = Math.min(total, VyneLoadingScreen.taskCurrent());
            labelledBar(px, w, h, barX, y + 24, barWidth, task, current + "/" + total, (float) current / total);
        }
        y += barsHeight;

        if (!tiles.isEmpty()) {
            paintTiles(px, w, h, barX, y, barWidth, columns, tileRows, tiles, now);
            y += modsHeight;
        }

        if (theme.showActivity) {
            List<String> lines = VyneLoadingScreen.recentActivity();
            for (int i = 0; i < lines.size(); i++) {
                int age = lines.size() - 1 - i;
                int color = age == 0 ? theme.text : age == 1 ? blend(theme.text, theme.muted) : theme.muted;
                font.draw(px, w, h, font.fit(lines.get(i), barWidth), barX, y + i * 10, color);
            }
        }

        font.draw(px, w, h, "Vyne " + version, 4, h - 11, theme.muted);
        String mc = "Minecraft " + dev.stolym.vyne.core.VyneLauncher.TARGET_MC;
        font.draw(px, w, h, mc, w - 4 - font.width(mc), h - 11, theme.muted);
    }

    private void paintTiles(int[] px, int w, int h, int x, int y, int width, int columns, int rows,
                            List<VyneLoadingScreen.ModTile> tiles, long now) {
        int fabric = 0;
        int neoforge = 0;
        int forge = 0;
        int done = 0;
        for (VyneLoadingScreen.ModTile tile : tiles) {
            switch (tile.loader()) {
                case "FABRIC" -> fabric++;
                case "NEOFORGE" -> neoforge++;
                case "FORGE" -> forge++;
                default -> { }
            }
            if (VyneLoadingScreen.modState(tile.id()) == VyneLoadingScreen.MOD_DONE) done++;
        }
        StringBuilder summary = new StringBuilder().append(tiles.size()).append(tiles.size() == 1 ? " mod" : " mods");
        if (neoforge > 0) summary.append(", ").append(neoforge).append(" NeoForge");
        if (forge > 0) summary.append(", ").append(forge).append(" Forge");
        if (fabric > 0) summary.append(", ").append(fabric).append(" Fabric");
        font.draw(px, w, h, font.fit(summary.toString(), width - 40), x, y, theme.muted);
        String count = done + "/" + tiles.size();
        font.draw(px, w, h, count, x + width - font.width(count), y, theme.muted);

        boolean blink = (now / 300L) % 2 == 0;
        int visible = Math.min(tiles.size(), columns * rows);
        int rowWidth = Math.min(visible, columns) * (TILE + TILE_GAP) - TILE_GAP;
        int startX = x + (width - rowWidth) / 2;
        for (int i = 0; i < visible; i++) {
            VyneLoadingScreen.ModTile tile = tiles.get(i);
            int tx = startX + (i % columns) * (TILE + TILE_GAP);
            int ty = y + 14 + (i / columns) * (TILE + TILE_GAP);
            int color = theme.modDone;
            int state = VyneLoadingScreen.modState(tile.id());
            if (!VyneModStateRegistry.isRunnable(tile.id())) {
                rect(px, w, h, tx, ty, TILE, TILE, theme.modFailed);
            } else if (state == VyneLoadingScreen.MOD_DONE) {
                rect(px, w, h, tx, ty, TILE, TILE, color);
            } else if (state == VyneLoadingScreen.MOD_WORKING) {
                rect(px, w, h, tx, ty, TILE, TILE, blink ? color : blend(color, theme.background));
            } else if (state == VyneLoadingScreen.MOD_READY) {
                outline(px, w, h, tx, ty, TILE, color);
                rect(px, w, h, tx + 2, ty + 2, TILE - 4, TILE - 4, blend(color, theme.background));
            } else {
                outline(px, w, h, tx, ty, TILE, blend(color, theme.background));
            }
        }
        if (tiles.size() > visible) {
            String more = "+" + (tiles.size() - visible);
            font.draw(px, w, h, more, x + width - font.width(more),
                    y + 14 + (rows - 1) * (TILE + TILE_GAP), theme.muted);
        }
    }

    private void labelledBar(int[] px, int w, int h, int x, int y, int width, String label, String right,
                             float fraction) {
        int rightWidth = font.width(right);
        font.draw(px, w, h, font.fit(label, width - rightWidth - 8), x, y, theme.text);
        font.draw(px, w, h, right, x + width - rightWidth, y, theme.text);
        bar(px, w, h, x, y + 10, width, 9, fraction, theme.barFill);
    }

    private void bar(int[] px, int w, int h, int x, int y, int width, int height, float fraction, int fill) {
        rect(px, w, h, x, y, width, height, theme.barBorder);
        rect(px, w, h, x + 1, y + 1, width - 2, height - 2, theme.barBackground);
        int inner = Math.round((width - 2) * Math.max(0f, Math.min(1f, fraction)));
        rect(px, w, h, x + 1, y + 1, inner, height - 2, fill);
    }

    private static int blend(int a, int b) {
        int r = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
        int g = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
        int bl = ((a & 0xFF) + (b & 0xFF)) / 2;
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static void cell(int[] px, int w, int h, int x, int y, int size, int color) {
        rect(px, w, h, x + 1, y, size - 2, size, color);
        rect(px, w, h, x, y + 1, 1, size - 2, color);
        rect(px, w, h, x + size - 1, y + 1, 1, size - 2, color);
    }

    private static void outline(int[] px, int w, int h, int x, int y, int size, int color) {
        rect(px, w, h, x, y, size, 1, color);
        rect(px, w, h, x, y + size - 1, size, 1, color);
        rect(px, w, h, x, y + 1, 1, size - 2, color);
        rect(px, w, h, x + size - 1, y + 1, 1, size - 2, color);
    }

    private static void rect(int[] px, int w, int h, int x, int y, int width, int height, int color) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(w, x + width);
        int y1 = Math.min(h, y + height);
        for (int yy = y0; yy < y1; yy++) {
            int row = yy * w;
            for (int xx = x0; xx < x1; xx++) px[row + xx] = color;
        }
    }
}
