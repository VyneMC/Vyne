/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 */

package dev.stolym.vyne.mappings;

public final class IntermediaryStubSuperHints {
    private IntermediaryStubSuperHints() {}

    private static final java.util.Set<String> OBSERVED_INTERFACES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.Set<String> OBSERVED_NEOFORGE_CLIENT_INTERFACES =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static final java.util.concurrent.ConcurrentHashMap<String, String[]>
        OBSERVED_CLASS_INTERFACES =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.concurrent.ConcurrentHashMap<String, String>
        OBSERVED_CLASS_SUPER =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void observeSuperInterface(String interInternal) {
        if (interInternal != null && !interInternal.isEmpty()) {
            OBSERVED_INTERFACES.add(interInternal);
        }
    }

    public static void observeNeoForgeClientInterface(String slashName) {
        if (slashName != null && !slashName.isEmpty()) {
            OBSERVED_NEOFORGE_CLIENT_INTERFACES.add(slashName);
        }
    }

    public static void observeClassShape(String className, String superName, String[] ifaces) {
        if (className == null || className.isEmpty()) return;
        if (superName != null && !superName.isEmpty()
                && !"java/lang/Object".equals(superName)) {
            OBSERVED_CLASS_SUPER.putIfAbsent(className, superName);
        }
        if (ifaces != null && ifaces.length > 0) {
            OBSERVED_CLASS_INTERFACES.merge(className, ifaces, (existing, incoming) -> {
                if (incoming.length > existing.length) return incoming;
                return existing;
            });
        }
    }

    public static String[] getObservedInterfacesFor(String interInternal) {
        return OBSERVED_CLASS_INTERFACES.get(interInternal);
    }

    public static String getObservedSuperFor(String interInternal) {
        return OBSERVED_CLASS_SUPER.get(interInternal);
    }

    private static final java.util.Set<String> KNOWN_CLIENT_INTERFACES_MOJMAP =
        java.util.Set.of(
            "net/minecraft/client/resources/model/BakedModel",
            "net/minecraft/client/color/block/BlockColor",
            "net/minecraft/client/color/item/ItemColor",
            "net/minecraft/client/gui/components/events/GuiEventListener",
            "net/minecraft/client/resources/sounds/SoundInstance",
            "net/minecraft/client/renderer/MultiBufferSource"
        );

    private static final java.util.Set<String> KNOWN_CLIENT_INTERFACES_INTERMEDIARY =
        java.util.Set.of(
            "net/minecraft/class_322",
            "net/minecraft/class_323",
            "net/minecraft/class_1087"
        );

    public static boolean isKnownClientInterface(String interInternal) {
        if (interInternal == null) {
            return false;
        }

        if (OBSERVED_INTERFACES.contains(interInternal)) {
            return true;
        }
        if (OBSERVED_NEOFORGE_CLIENT_INTERFACES.contains(interInternal)) {
            return true;
        }

        if (KNOWN_CLIENT_INTERFACES_INTERMEDIARY.contains(interInternal)) {
            return true;
        }

        String dots = interInternal.replace('/', '.');
        if (KNOWN_CLIENT_INTERFACES_INTERMEDIARY.contains(dots)) {
            return true;
        }

        if (!MappingRegistry.isReady()) {
            return false;
        }

        MappingComposer.ComposedMappings m = MappingRegistry.get();
        String moj = m.unmapClass(interInternal);
        if (moj == null || moj.equals(interInternal)) {
            moj = m.unmapClass(dots);
        }

        if (moj == null) {
            return false;
        }

        String mojSlash = moj.indexOf('.') >= 0 ? moj.replace('.', '/') : moj;

        if (KNOWN_CLIENT_INTERFACES_MOJMAP.contains(mojSlash)) {
            return true;
        }

        String simple = mojSlash.contains("/") ? mojSlash.substring(mojSlash.lastIndexOf('/') + 1) : mojSlash;
        if (mojSlash.startsWith("net/minecraft/client/color/")) {
            if (simple.endsWith("Color")) return true;
        }
        if (mojSlash.startsWith("net/minecraft/client/gui/")) {
            if (simple.endsWith("Listener") || simple.endsWith("Provider") || simple.endsWith("Consumer")) return true;
        }
        if (mojSlash.startsWith("net/minecraft/client/renderer/")) {
            if (simple.endsWith("Provider") || simple.endsWith("Source")) return true;
        }

        return false;
    }

    public static String inferDirectSuperIntermediary(String interInternal) {
        if (interInternal == null || !interInternal.startsWith("net/minecraft/")) {
            return null;
        }
        if (!MappingRegistry.isReady()) {
            return null;
        }
        MappingComposer.ComposedMappings m = MappingRegistry.get();
        String moj = m.unmapClass(interInternal);
        if (moj == null || moj.equals(interInternal)) {
            return null;
        }
        String mojSlash = moj.indexOf('.') >= 0 ? moj.replace('.', '/') : moj;
        if ("net/minecraft/client/multiplayer/ClientLevel".equals(mojSlash)) {
            String levelInter = m.mapClass("net/minecraft/world/level/Level");
            if (levelInter != null) {
                return levelInter.indexOf('.') >= 0
                    ? levelInter.replace('.', '/')
                    : levelInter;
            }
            return "net/minecraft/class_1937";
        }
        if (mojSlash.startsWith("net/minecraft/client/player/")) {
            String playerInter = m.mapClass("net/minecraft/world/entity/player/Player");
            if (playerInter != null) {
                return playerInter.indexOf('.') >= 0
                    ? playerInter.replace('.', '/')
                    : playerInter;
            }
        }
        return null;
    }
}
