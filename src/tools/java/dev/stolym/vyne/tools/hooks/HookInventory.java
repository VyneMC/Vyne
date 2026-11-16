/*
 * This file is part of Stolym Studios project, licensed under the GPL-3.0 License.
 * Full license text is available in the LICENSE file in the project root.
 */

package dev.stolym.vyne.tools.hooks;

import dev.stolym.vyne.convert.ExactRemap;
import dev.stolym.vyne.convert.NeoForgeInstall;
import dev.stolym.vyne.mappings.MojmapDownloader;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public final class HookInventory {
    private HookInventory() {}

    public static void main(String[] args) throws Exception {
        String minecraft = args[0];
        String neoForgeVersion = args[1];
        Path fabricApiJar = Path.of(args[2]);
        String fabricApiVersion = args[3];
        Path outDir = Path.of(args[4]);
        Path work = Path.of(System.getProperty("vyne.gameDir"));

        NeoForgeInstall neoForge = NeoForgeInstall.ensure(neoForgeVersion, false, work);
        Names names = new Names(ExactRemap.mappings(MojmapDownloader.getIntermediaryPath(minecraft),
                MojmapDownloader.getMojmapPath(minecraft)));

        NeoForgeChanges.Result neoResult = NeoForgeChanges.collect(neoForge.patchedGame(), neoForge.vanillaGame());
        List<NeoForgeChanges.Change> changes = neoResult.changes();
        List<FabricApiTargets.Target> targets = FabricApiTargets.collect(fabricApiJar, names);

        Files.createDirectories(outDir);
        Path neoFile = outDir.resolve("neoforge-" + neoForgeVersion + ".tsv");
        Path fabricFile = outDir.resolve("fabric-api-" + fabricApiVersion + ".tsv");
        Path overlapFile = outDir.resolve("overlap-neoforge-" + neoForgeVersion + "-fabric-api-" + fabricApiVersion + ".tsv");
        Path summaryFile = outDir.resolve("summary-neoforge-" + neoForgeVersion + "-fabric-api-" + fabricApiVersion + ".txt");

        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(neoFile))) {
            out.println("class\tmember\tchange\tsynthetic\tneoforge_added\tother_added\tremoved");
            for (NeoForgeChanges.Change c : changes) {
                out.println(c.owner() + '\t' + c.member() + '\t' + c.kind() + '\t' + (c.synthetic() ? "yes" : "")
                        + '\t' + String.join(" ", c.neoForgeAdded()) + '\t' + String.join(" ", c.otherAdded())
                        + '\t' + String.join(" ", c.removed()));
            }
        }
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(fabricFile))) {
            out.println("class\tmethod\tinjector\tat\tmodule\tmixin\thandler");
            for (FabricApiTargets.Target t : targets) {
                out.println(t.owner() + '\t' + t.method() + '\t' + t.kind() + '\t' + t.at() + '\t' + t.module() + '\t' + t.mixin() + '\t' + t.handler());
            }
        }

        Map<String, NeoForgeChanges.Change> neoMethods = new TreeMap<>();
        Set<String> neoClasses = new TreeSet<>();
        for (NeoForgeChanges.Change c : changes) {
            if (!c.kind().equals("class+")) neoClasses.add(c.owner());
            if (c.kind().startsWith("method")) {
                String member = c.member();
                int renamed = member.indexOf(" (now ");
                neoMethods.put(c.owner() + "." + (renamed >= 0 ? member.substring(0, renamed) : member), c);
            }
        }
        Map<String, Set<String>> fabricMethods = new TreeMap<>();
        Set<String> fabricClasses = new TreeSet<>();
        Set<String> mixins = new HashSet<>();
        for (FabricApiTargets.Target t : targets) {
            fabricClasses.add(t.owner());
            mixins.add(t.mixin());
            fabricMethods.computeIfAbsent(t.owner() + "." + t.method(), k -> new TreeSet<>()).add(t.module());
        }
        int shared = 0;
        int sharedClassTargets = 0;
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(overlapFile))) {
            out.println("class\tmethod\tneoforge_change\tneoforge_refs_added\tfabric_api_modules");
            for (Map.Entry<String, Set<String>> entry : fabricMethods.entrySet()) {
                String key = entry.getKey();
                String owner = key.substring(0, key.indexOf('.'));
                if (neoClasses.contains(owner)) sharedClassTargets++;
                NeoForgeChanges.Change change = neoMethods.get(key);
                if (change == null) continue;
                shared++;
                out.println(owner + '\t' + key.substring(owner.length() + 1) + '\t' + change.kind() + '\t'
                        + String.join(" ", change.neoForgeAdded()) + '\t' + String.join(" ", entry.getValue()));
            }
        }

        Map<String, Integer> kinds = new TreeMap<>();
        int syntheticChanged = 0;
        for (NeoForgeChanges.Change c : changes) {
            kinds.merge(c.kind(), 1, Integer::sum);
            if (c.synthetic() && c.kind().startsWith("method")) syntheticChanged++;
        }
        Set<String> sharedClasses = new TreeSet<>(fabricClasses);
        sharedClasses.retainAll(neoClasses);
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(summaryFile))) {
            out.println("Minecraft " + minecraft + ", NeoForge " + neoForgeVersion + ", Fabric API " + fabricApiVersion);
            out.println();
            out.println("NeoForge");
            out.println("  vanilla classes changed: " + neoClasses.size());
            for (Map.Entry<String, Integer> kind : kinds.entrySet()) out.println("  " + kind.getKey() + ": " + kind.getValue());
            out.println("  of the method entries, synthetic (lambdas, bridges): " + syntheticChanged);
            out.println("  methods only recompiled (same behaviour, different bytecode layout; not listed): " + neoResult.recompiledOnly());
            out.println();
            out.println("Fabric API");
            out.println("  mixin classes targeting Minecraft: " + mixins.size());
            out.println("  Minecraft classes targeted: " + fabricClasses.size());
            out.println("  (class, method) targets: " + fabricMethods.size());
            out.println();
            out.println("Both");
            out.println("  classes changed by both: " + sharedClasses.size());
            out.println("  Fabric API method targets in classes NeoForge changes: " + sharedClassTargets);
            out.println("  methods changed by both (same name and descriptor): " + shared);
        }
        System.out.println("Hook inventory written to " + outDir.toAbsolutePath());
        System.out.println(Files.readString(summaryFile));
    }
}
