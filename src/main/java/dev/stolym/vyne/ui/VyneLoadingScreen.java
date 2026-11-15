/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.ui;

import dev.stolym.vyne.util.VyneLogger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class VyneLoadingScreen {
    public static final AtomicReference<String> status = new AtomicReference<>("Initializing Vyne...");
    public static final AtomicInteger progress = new AtomicInteger(0);
    private static volatile String taskLabel;
    private static volatile int taskCurrent;
    private static volatile int taskTotal;

    public static void update(String msg, int pct) {
        status.set(msg);
        progress.set(pct);
        taskLabel = null;
        taskTotal = 0;
        taskCurrent = 0;
        pumpWindow();
    }

    public static void task(String label, int current, int total) {
        taskCurrent = current;
        taskTotal = total;
        taskLabel = label;
        pumpWindow();
    }

    public static String taskLabel() {
        return taskLabel;
    }

    public static int taskCurrent() {
        return taskCurrent;
    }

    public static int taskTotal() {
        return taskTotal;
    }

    private static volatile boolean windowAvailable = true;

    private static void pumpWindow() {
        if (!windowAvailable) return;
        try {
            VyneEarlyWindow.pump();
        } catch (Throwable t) {
            windowAvailable = false;
        }
    }

    public static final int MOD_PENDING = 0;
    public static final int MOD_READY = 1;
    public static final int MOD_WORKING = 2;
    public static final int MOD_DONE = 3;

    public record ModTile(String id, String loader) {}

    private static final java.util.List<ModTile> MOD_TILES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.Map<String, Integer> MOD_STATES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.ArrayDeque<String> ACTIVITY = new java.util.ArrayDeque<>();
    private static final int ACTIVITY_LINES = 3;
    private static volatile float[] handoffLogo;

    public static void registerMods(java.util.List<ModTile> tiles) {
        MOD_TILES.clear();
        if (tiles != null) MOD_TILES.addAll(tiles);
    }

    public static java.util.List<ModTile> modTiles() {
        return MOD_TILES;
    }

    public static int modState(String modId) {
        Integer state = MOD_STATES.get(modId);
        return state != null ? state : MOD_PENDING;
    }

    public static void modState(String modId, int state) {
        if (modId == null) return;
        MOD_STATES.merge(modId, state, (old, now) -> old == MOD_DONE && now != MOD_DONE ? old : now);
    }

    public static void modsDone(String loader) {
        for (ModTile tile : MOD_TILES) {
            if (tile.loader().equals(loader)) modState(tile.id(), MOD_DONE);
        }
    }

    public static void activity(String line) {
        if (line == null || line.isBlank()) return;
        synchronized (ACTIVITY) {
            ACTIVITY.addLast(line);
            while (ACTIVITY.size() > ACTIVITY_LINES) ACTIVITY.removeFirst();
        }
    }

    public static java.util.List<String> recentActivity() {
        synchronized (ACTIVITY) {
            return new java.util.ArrayList<>(ACTIVITY);
        }
    }

    public static void handoffLogo(float x, float y, float width, float height) {
        handoffLogo = new float[] {x, y, width, height};
    }

    public static float[] consumeHandoffLogo() {
        float[] rect = handoffLogo;
        handoffLogo = null;
        return rect;
    }

    private static final ThreadLocal<String> LOADING_MOD = new ThreadLocal<>();

    public static void setLoadingMod(String modId) {
        if (modId == null) LOADING_MOD.remove();
        else LOADING_MOD.set(modId);
    }

    public static String loadingMod() {
        return LOADING_MOD.get();
    }
}
