/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mixin;

import dev.stolym.vyne.mappings.MappingComposer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MixinAnnotationRemapperTest {
    private static final String PLAYER = "net/minecraft/world/entity/player/Player";
    private static final String FOOD = "net/minecraft/world/food/FoodData";

    private static MappingComposer.ComposedMappings mappings() {
        Map<String, String> classes = Map.of(PLAYER, "net/minecraft/class_1657", FOOD, "net/minecraft/class_1702");
        Map<String, Map<String, String>> methods = Map.of(PLAYER, Map.of(
                "canEat(Z)Z", "method_7332",
                "getFoodData()L" + FOOD + ";", "method_7344"));
        Map<String, Map<String, String>> fields = Map.of(PLAYER, Map.of("sleepCounter", "field_7487"));
        Map<String, String> reverse = Map.of("net/minecraft/class_1657", PLAYER, "net/minecraft/class_1702", FOOD);
        return new MappingComposer.ComposedMappings(classes, methods, fields, reverse, Map.of());
    }

    private static ClassNode remapped(Class<?> sample) throws Exception {
        byte[] bytes;
        try (InputStream in = sample.getResourceAsStream(sample.getSimpleName() + ".class")) {
            bytes = in.readAllBytes();
        }
        ClassNode node = new ClassNode();
        new ClassReader(MixinAnnotationRemapper.remap(bytes, mappings())).accept(node, 0);
        return node;
    }

    private static Object value(AnnotationNode ann, String key) {
        for (int i = 0; i + 1 < ann.values.size(); i += 2) if (key.equals(ann.values.get(i))) return ann.values.get(i + 1);
        return null;
    }

    private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String desc) {
        List<AnnotationNode> all = new ArrayList<>();
        if (visible != null) all.addAll(visible);
        if (invisible != null) all.addAll(invisible);
        return all.stream().filter(a -> a.desc.equals(desc)).findFirst().orElseThrow();
    }

    @Test
    void injectSelectorAndAtTargetAreRemapped() throws Exception {
        ClassNode node = remapped(dev.stolym.vyne.mixin.fixture.PlayerMixinSample.class);
        MethodNode handler = node.methods.stream().filter(m -> m.name.equals("vyneTest$canEat")).findFirst().orElseThrow();
        AnnotationNode inject = annotation(handler.visibleAnnotations, handler.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of("method_7332(Z)Z"), value(inject, "method"));

        Object at = value(inject, "at");
        AnnotationNode atNode = at instanceof List<?> l ? (AnnotationNode) l.get(0) : (AnnotationNode) at;
        assertEquals("Lnet/minecraft/class_1657;method_7344()Lnet/minecraft/class_1702;", value(atNode, "target"));
    }

    @Test
    void shadowFieldIsRenamedAlongWithItsUses() throws Exception {
        ClassNode node = remapped(dev.stolym.vyne.mixin.fixture.PlayerMixinSample.class);
        assertTrue(node.fields.stream().anyMatch(f -> f.name.equals("field_7487")));
        assertTrue(node.fields.stream().noneMatch(f -> f.name.equals("sleepCounter")));
        MethodNode handler = node.methods.stream().filter(m -> m.name.equals("vyneTest$canEat")).findFirst().orElseThrow();
        for (AbstractInsnNode insn : handler.instructions) {
            if (insn instanceof FieldInsnNode f) assertEquals("field_7487", f.name);
        }
    }

    @Test
    void accessorWithoutValueGetsExplicitIntermediaryTarget() throws Exception {
        ClassNode node = remapped(dev.stolym.vyne.mixin.fixture.PlayerAccessorSample.class);
        MethodNode getter = node.methods.stream().filter(m -> m.name.equals("getSleepCounter")).findFirst().orElseThrow();
        AnnotationNode accessor = annotation(getter.visibleAnnotations, getter.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/gen/Accessor;");
        assertEquals("field_7487", value(accessor, "value"));
    }

    @Test
    void mixinTargetStringIsRemapped() throws Exception {
        ClassNode node = remapped(dev.stolym.vyne.mixin.fixture.PlayerAccessorSample.class);
        AnnotationNode mixin = annotation(node.visibleAnnotations, node.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/Mixin;");
        assertEquals(List.of("net/minecraft/class_1657"), value(mixin, "targets"));
    }

    @Test
    void nameOnlySelectorResolvesOverrideDeclaredInSuperclass() throws Exception {
        String scoreboard = "net/minecraft/world/scores/Scoreboard";
        String team = "net/minecraft/world/scores/PlayerTeam";
        Map<String, String> classes = Map.of(PLAYER, "net/minecraft/class_1657", scoreboard, "net/minecraft/class_269", team, "net/minecraft/class_268");
        Map<String, Map<String, String>> methods = Map.of(scoreboard, Map.of("canEat(Ljava/lang/String;L" + team + ";)Z", "method_1172"));
        MappingComposer.ComposedMappings m = new MappingComposer.ComposedMappings(classes, methods, Map.of(), Map.of(), Map.of());
        byte[] bytes;
        try (InputStream in = dev.stolym.vyne.mixin.fixture.PlayerMixinSample.class.getResourceAsStream("PlayerMixinSample.class")) {
            bytes = in.readAllBytes();
        }
        ClassNode node = new ClassNode();
        new ClassReader(MixinAnnotationRemapper.remap(bytes, m, owner -> owner.equals(PLAYER) ? List.of(scoreboard) : List.of())).accept(node, 0);
        MethodNode handler = node.methods.stream().filter(x -> x.name.equals("vyneTest$canEat")).findFirst().orElseThrow();
        AnnotationNode inject = annotation(handler.visibleAnnotations, handler.invisibleAnnotations, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of("method_1172(Ljava/lang/String;Lnet/minecraft/class_268;)Z"), value(inject, "method"));
    }

    @Test
    void nonMixinClassesAreReturnedUnchanged() throws Exception {
        byte[] bytes;
        try (InputStream in = MixinAnnotationRemapperTest.class.getResourceAsStream("MixinAnnotationRemapperTest.class")) {
            bytes = in.readAllBytes();
        }
        assertSame(bytes, MixinAnnotationRemapper.remap(bytes, mappings()));
    }
}
