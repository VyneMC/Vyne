# Small fixes and quality-of-life backlog

Items noticed on 2026-11-14 while adding the mod converter and fixing the registry-lookup stall. None of them were changed; each is small enough to do on its own. Line references are to the working tree of that day.

## Boot time

- **`RealNeoForgeApiMount.prepareStrippedApiJar` redoes its analysis every boot.** It hashes the universal jar and runs `vyneProvidedClasses()` (reads every relevant class in the Vyne jar and walks references) before it can even find its cache directory. Showed up as ~1.5 s in thread dumps. Key the cache on size + mtime of both jars, the way `ModConverter` does.
- **The NeoForge API is mounted and converted even when no NeoForge/Forge mod is installed** (`fabric-only` scenario: 3.6 s of conversion on first boot for nothing). Check whether anything needs it in that case; if not, skip the mount.
- **`HierarchyScanner` rescans every jar on every boot** (`VyneLauncher`, "METADATA INDEXING"). The converter already keeps a size/mtime index per jar; the class headers could be stored next to it.
- **`VyneRegistryInjector.resolveRegistry` still does three `Class.forName` calls and a mapping lookup per call**, and a miss (datapack registries) is not cached. It is cheap now, but it runs for every holder decode.
- **An ~8 s silent gap remains** between the last `[VyneGate]` warning and `Booting VyneModLifecycle`. Not investigated; a thread-dump sampler pointed at ordinary class loading, but it deserves a proper look.
- **`RemapCache.jarSha256` hashes the whole jar** for every jar the gate still handles (the Vyne jar on each boot).

## Log noise

- **Every Vyne log line is printed twice**, once through Log4j and once as a bare `[Vyne] ...` line (`VyneLogger`).
- **`[FieldRemap]` and inherited-method remap messages are logged at INFO.** First-boot conversion of the NeoForge API prints ~230 of them.
- **`[Vyne-Recovery] fillAllRegistryGapsIntoVyneGeneric: checking 0 registers`** is printed to stderr repeatedly during boot.
- **`RegistryDatapackLookupFallback.vyneTraceElementCodecRecover()` is hard-coded to `true`**, so its trace output is always on at INFO.
- **`[VynePacks] Prerequisite classes unavailable (Fabric API missing?)` is logged at ERROR** whenever Fabric API is not installed, which is a normal setup. It is the only ERROR line in three of the five smoke scenarios.
- **Phase labels are stale after hand-over to Knot:** converter and mount messages are tagged `METADATA INDEXING`, the entrypoint messages `VCL INITIALIZATION`.

## Possible bugs to confirm

- **`[VyneGate] WARNING: net.minecraft.class_2385 / class_2378 is missing from metadata index! Interception will be skipped`** appears on every server boot. The index is built from `fabric.gameJarPath.server`, which does not hold Intermediary names, so the check always fails. Either the warning is wrong or the `freeze()` hollowing in the gate never gets the hierarchy it expects.
- **A class name present in two jars** gets a single entry in `VyneMetadataIndex` (last scanned wins). The gate, and now the converted-jar check, decide by that entry, not by the jar the bytes actually came from.
- **`MappingRegistry.init` falls back to empty mappings** when download and retry both fail, and the boot continues. The converter then skips itself with a warning, but every NeoForge mod will fail later with confusing errors. Fail fast with one clear message instead.

## Tests and tooling

- **The smoke tasks delete the run directory first**, so the warm-cache path is never exercised. Add a second boot per scenario that asserts the converter log line reports `0 converted`.
- **`gradle/smoke.gradle` still launches with `-Djava.system.class.loader=dev.stolym.vyne.vcl.BridgeClassLoader`**, a class from the deprecated VCL stack.
- **The smoke check only fails on a fixed list of patterns.** `kernel-architecture.md` section 7 already asks for it to fail on ERROR-level registration failures.
- **Old `.vyne/cache/remap` and `.vyne/cache/realneo` buckets are never pruned** (the new `converted` cache is).

## Converter follow-ups (small)

- Mods detected as `UNKNOWN` loader type are mounted by `VyneModLifecycle` but not converted.
- Vyne's own `net/neoforged` and `net/minecraftforge` shim classes (loader type `VYNE`) still go through the gate on every boot.
- Record time-to-`Done` and peak memory in the smoke log so step 0 of the kernel plan has numbers per scenario, not one hand-measured scenario.
- Patched mode: log4j prints `Error processing element Queue ... CLASS_NOT_FOUND` and `Unable to locate appender "ServerGuiConsole"` on its startup console before the game log opens (the game log itself has no ERROR lines). Point log4j at the right plugin packages or a Vyne log config.
