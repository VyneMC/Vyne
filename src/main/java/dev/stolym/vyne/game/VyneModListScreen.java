/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.game;

import dev.stolym.vyne.core.DetectedMod;
import dev.stolym.vyne.core.VyneLauncher;
import dev.stolym.vyne.containment.VyneModStateRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.class_2561;
import net.minecraft.class_332;
import net.minecraft.class_4185;
import net.minecraft.class_437;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class VyneModListScreen extends class_437 {
    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 34;
    private static final int FOOTER = 72;

    private record Entry(String id, String name, String version, String loader, int loaderColor,
                         String description, boolean frozen) {}

    private final class_437 parent;
    private final List<Entry> entries = new ArrayList<>();
    private int selected = -1;
    private int scroll;
    private class_4185 configButton;

    public VyneModListScreen(class_437 parent) {
        super(class_2561.method_43470("Mods"));
        this.parent = parent;
        collect();
    }

    public static int loadedModCount() {
        return new VyneModListScreen(null).entries.size();
    }

    private void collect() {
        Set<String> seen = new HashSet<>();
        List<DetectedMod> detected = VyneLauncher.getDetectedMods();
        if (detected != null) {
            for (DetectedMod mod : detected) {
                if (mod.getModId() == null || !seen.add(mod.getModId())) continue;
                String loader = switch (mod.getLoaderType()) {
                    case FABRIC -> "Fabric";
                    case NEOFORGE -> "NeoForge";
                    case FORGE -> "Forge";
                    case VYNE -> "Vyne";
                    default -> "Unknown";
                };
                int color = switch (mod.getLoaderType()) {
                    case FABRIC -> 0xFFDBD0B4;
                    case NEOFORGE -> 0xFFE68C37;
                    case FORGE -> 0xFF7A8DB8;
                    case VYNE -> 0xFF00CCFF;
                    default -> 0xFFAAAAAA;
                };
                entries.add(new Entry(mod.getModId(), orElse(mod.getName(), mod.getModId()),
                        orElse(mod.getVersion(), ""), loader, color, orElse(mod.getDescription(), ""),
                        !VyneModStateRegistry.isRunnable(mod.getModId())));
            }
        }
        for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
            ModMetadata meta = container.getMetadata();
            if (container.getContainingMod().isPresent() || !seen.add(meta.getId())) continue;
            boolean builtin = "builtin".equals(meta.getType());
            String id = meta.getId();
            String loader = id.equals("fabricloader") || id.equals("vyne") ? "Vyne" : builtin ? "Built-in" : "Fabric";
            int color = loader.equals("Vyne") ? 0xFF00CCFF : builtin ? 0xFFAAAAAA : 0xFFDBD0B4;
            String name = id.equals("fabricloader") ? "Vyne Loader" : meta.getName();
            String version = id.equals("fabricloader") ? VyneLauncher.VERSION : meta.getVersion().getFriendlyString();
            entries.add(new Entry(id, name, version, loader, color, orElse(meta.getDescription(), ""), false));
        }
        entries.sort(Comparator.comparing((Entry e) -> e.loader().equals("Built-in") || e.loader().equals("Vyne") ? 0 : 1)
                .thenComparing(e -> e.name().toLowerCase(Locale.ROOT)));
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    protected void method_25426() {
        int centerX = field_22789 / 2;
        int y = field_22790 - 28;
        configButton = method_37063(class_4185.method_46430(class_2561.method_43470("Config"), b -> openConfig())
                .method_46434(centerX - 154, y, 150, 20).method_46431());
        method_37063(class_4185.method_46430(class_2561.method_43470("Done"), b -> method_25419())
                .method_46434(centerX + 4, y, 150, 20).method_46431());
        updateConfigButton();
    }

    private int listBottom() {
        return field_22790 - FOOTER;
    }

    private int maxScroll() {
        return Math.max(0, entries.size() * ROW_HEIGHT - (listBottom() - LIST_TOP));
    }

    @Override
    public void method_25394(class_332 graphics, int mouseX, int mouseY, float delta) {
        super.method_25394(graphics, mouseX, mouseY, delta);
        int left = Math.max(20, field_22789 / 2 - 180);
        int right = field_22789 - left;
        int bottom = listBottom();

        graphics.method_25300(field_22793, "Mods", field_22789 / 2, 10, 0xFFFFFFFF);
        graphics.method_25300(field_22793, entries.size() + " loaded · Vyne " + VyneLauncher.VERSION,
                field_22789 / 2, 21, 0xFF9A9A9A);

        graphics.method_25294(left - 2, LIST_TOP - 2, right + 2, bottom + 2, 0x90000000);
        graphics.method_44379(left - 2, LIST_TOP, right + 2, bottom);
        for (int i = 0; i < entries.size(); i++) {
            int top = LIST_TOP + i * ROW_HEIGHT - scroll;
            if (top + ROW_HEIGHT < LIST_TOP || top > bottom) continue;
            Entry entry = entries.get(i);
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= Math.max(top, LIST_TOP)
                    && mouseY < Math.min(top + ROW_HEIGHT, bottom);
            if (i == selected) {
                graphics.method_25294(left, top, right, top + ROW_HEIGHT - 1, 0x6000CCFF);
            } else if (hovered) {
                graphics.method_25294(left, top, right, top + ROW_HEIGHT - 1, 0x30FFFFFF);
            }
            graphics.method_25294(left, top, left + 2, top + ROW_HEIGHT - 1, entry.loaderColor());
            int tagWidth = field_22793.method_1727(entry.loader());
            String title = field_22793.method_27523(entry.name(), right - left - tagWidth - 24);
            graphics.method_25303(field_22793, title, left + 8, top + 3, entry.frozen() ? 0xFFFF6B6B : 0xFFFFFFFF);
            String detail = entry.frozen() ? entry.id() + " · disabled after an error" : entry.id() + " " + entry.version();
            graphics.method_25303(field_22793, field_22793.method_27523(detail, right - left - tagWidth - 24),
                    left + 8, top + 13, 0xFF8A8A8A);
            graphics.method_25303(field_22793, entry.loader(), right - tagWidth - 6, top + 8, entry.loaderColor());
        }
        graphics.method_44380();

        if (selected >= 0 && selected < entries.size()) {
            Entry entry = entries.get(selected);
            String description = entry.description().replace('\n', ' ').trim();
            if (description.isEmpty()) description = "No description provided.";
            int width = right - left;
            String first = field_22793.method_27523(description, width);
            graphics.method_25303(field_22793, first, left, bottom + 8, 0xFFCFCFCF);
            if (first.length() < description.length()) {
                String rest = field_22793.method_27523(description.substring(first.length()).trim(), width);
                graphics.method_25303(field_22793, rest, left, bottom + 19, 0xFFCFCFCF);
            }
        } else {
            graphics.method_25300(field_22793, "Select a mod to see its details", field_22789 / 2, bottom + 12, 0xFF8A8A8A);
        }
    }

    @Override
    public boolean method_25402(double mouseX, double mouseY, int button) {
        int left = Math.max(20, field_22789 / 2 - 180);
        int right = field_22789 - left;
        if (button == 0 && mouseX >= left && mouseX < right && mouseY >= LIST_TOP && mouseY < listBottom()) {
            int index = (int) ((mouseY - LIST_TOP + scroll) / ROW_HEIGHT);
            if (index >= 0 && index < entries.size()) {
                selected = index;
                updateConfigButton();
                return true;
            }
        }
        return super.method_25402(mouseX, mouseY, button);
    }

    @Override
    public boolean method_25401(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = (int) Math.max(0, Math.min(maxScroll(), scroll - scrollY * ROW_HEIGHT));
        return true;
    }

    @Override
    public void method_25419() {
        field_22787.method_1507(parent);
    }

    private void updateConfigButton() {
        if (configButton != null) {
            configButton.field_22763 = selected >= 0 && selected < entries.size()
                    && hasConfig(entries.get(selected).id());
        }
    }

    private void openConfig() {
        if (selected < 0 || selected >= entries.size()) return;
        String id = entries.get(selected).id();
        try {
            class_437 screen = fabricConfigScreen(id);
            if (screen != null) field_22787.method_1507(screen);
        } catch (Throwable t) {
            dev.stolym.vyne.util.VyneLogger.warn("[ModList] Could not open the config screen of " + id + ": " + t);
        }
    }

    private final java.util.Map<String, Boolean> hasConfig = new java.util.HashMap<>();

    private boolean hasConfig(String modId) {
        return hasConfig.computeIfAbsent(modId, id -> {
            try {
                return fabricConfigScreen(id) != null;
            } catch (Throwable t) {
                return false;
            }
        });
    }

    private class_437 fabricConfigScreen(String modId) {
        com.terraformersmc.modmenu.api.ConfigScreenFactory<?> factory = modMenuFactories().get(modId);
        return factory == null ? null : factory.create(this);
    }

    private static java.util.Map<String, com.terraformersmc.modmenu.api.ConfigScreenFactory<?>> modMenuFactories;

    private static synchronized java.util.Map<String, com.terraformersmc.modmenu.api.ConfigScreenFactory<?>> modMenuFactories() {
        if (modMenuFactories != null) return modMenuFactories;
        java.util.Map<String, com.terraformersmc.modmenu.api.ConfigScreenFactory<?>> own = new java.util.HashMap<>();
        java.util.Map<String, com.terraformersmc.modmenu.api.ConfigScreenFactory<?>> provided = new java.util.HashMap<>();
        try {
            for (net.fabricmc.loader.api.entrypoint.EntrypointContainer<com.terraformersmc.modmenu.api.ModMenuApi> container
                    : FabricLoader.getInstance().getEntrypointContainers("modmenu", com.terraformersmc.modmenu.api.ModMenuApi.class)) {
                String id = container.getProvider().getMetadata().getId();
                try {
                    com.terraformersmc.modmenu.api.ModMenuApi api = container.getEntrypoint();
                    com.terraformersmc.modmenu.api.ConfigScreenFactory<?> factory = api.getModConfigScreenFactory();
                    if (factory != null) own.put(id, factory);
                    java.util.Map<String, com.terraformersmc.modmenu.api.ConfigScreenFactory<?>> others = api.getProvidedConfigScreenFactories();
                    if (others != null) others.forEach(provided::putIfAbsent);
                } catch (Throwable t) {
                    dev.stolym.vyne.util.VyneLogger.warn("[ModList] Mod Menu entrypoint of " + id + " failed: " + t);
                }
            }
        } catch (Throwable t) {
            dev.stolym.vyne.util.VyneLogger.debug("[ModList] Mod Menu entrypoints unavailable: " + t);
        }
        provided.putAll(own);
        modMenuFactories = provided;
        return provided;
    }
}
