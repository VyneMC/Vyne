/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.mappings;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MappingComposerTest {
    @Test
    public void mapClassNormalizesDescriptorShapedNames() {
        MappingComposer.ComposedMappings mappings = emptyMappings();

        assertEquals(
            "net/minecraft/class_1761$class_7913",
            mappings.mapClass("Lnet/minecraft/world/item/CreativeModeTab$Builder;"));
        assertEquals(
            "net/minecraft/class_1761$class_7913",
            mappings.mapClass("Lnet/minecraft/world/item/CreativeModeTab$Builder"));
        assertEquals(
            "net/minecraft/class_1761$class_7913",
            mappings.mapClass("Lnet/minecraft/class_1761$class_7913;"));
    }

    @Test
    public void mapClassFallsBackToNormalizedInternalName() {
        MappingComposer.ComposedMappings mappings = emptyMappings();

        assertEquals(
            "example/Missing",
            mappings.mapClass("Lexample/Missing;"));
    }

    private static MappingComposer.ComposedMappings emptyMappings() {
        return new MappingComposer.ComposedMappings(
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of());
    }
}
