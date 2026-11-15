/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VyneConfigBridgeTest {
    @TempDir
    Path dir;

    private ModConfigSpec buildSpec() {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("general");
        b.comment("Enable the thing");
        ModConfigSpec.BooleanValue enabled = b.define("enabled", false);
        b.comment("How many");
        ModConfigSpec.IntValue count = b.defineInRange("count", 5, 0, 100);
        ModConfigSpec.EnumValue<Mode> mode = b.defineEnum("mode", Mode.SLOW);
        b.pop();
        ModConfigSpec.ConfigValue<String> note = b.define("note", "hello");
        ModConfigSpec spec = b.build();
        assertTrue(enabled.get() != null && count.get() != null && mode.get() != null && note.get() != null);
        return spec;
    }

    private enum Mode { SLOW, FAST }

    @Test
    void defaultsAreWrittenAndLoaded() throws Exception {
        Path file = dir.resolve("test.toml");
        ModConfigSpec spec = buildSpec();

        Files.writeString(file, dev.stolym.vyne.config.VyneConfigBridge.buildTomlForTests(spec));
        dev.stolym.vyne.config.VyneConfigBridge.loadIntoSpecForTests(file, spec);

        var entries = spec.getEntries();
        assertTrue(Files.readString(file).contains("enabled = false"), "default must be written to the file");
        assertEquals(false, spec.valueFor(entries.get("general.enabled")));
        assertEquals(5, ((Number) spec.valueFor(entries.get("general.count"))).intValue());
        assertEquals(Mode.SLOW, spec.valueFor(entries.get("general.mode")));
        assertEquals("hello", spec.valueFor(entries.get("note")));
    }

    @Test
    void userValuesOverrideDefaults() throws Exception {
        Path file = dir.resolve("test.toml");
        ModConfigSpec spec = buildSpec();
        Files.writeString(file, dev.stolym.vyne.config.VyneConfigBridge.buildTomlForTests(spec));
        String edited = Files.readString(file)
                .replace("enabled = false", "enabled = true")
                .replace("count = 5", "count = 42")
                .replace("mode = \"SLOW\"", "mode = \"FAST\"")
                .replace("note = \"hello\"", "note = \"world\"");
        Files.writeString(file, edited);
        dev.stolym.vyne.config.VyneConfigBridge.loadIntoSpecForTests(file, spec);

        var entries = spec.getEntries();
        assertEquals(true, spec.valueFor(entries.get("general.enabled")));
        assertEquals(42, ((Number) spec.valueFor(entries.get("general.count"))).intValue());
        assertEquals(Mode.FAST, spec.valueFor(entries.get("general.mode")));
        assertEquals("world", spec.valueFor(entries.get("note")));
    }
}
