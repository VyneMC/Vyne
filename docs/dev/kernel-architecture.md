# Kernel architecture: translate once, run native

Status: **proposed** (2026-11-09). This is the target design for how Vyne loads NeoForge and Forge mods. It replaces the "translate every class while the game runs" approach with "translate each mod once, save it, and load the saved result like a Fabric mod".

Related: [unified-platform.md](unified-platform.md) (where Vyne is heading: Fabric API and NeoForge both bundled on one hook layer), [heavy-mod-roadmap.md](heavy-mod-roadmap.md) (capability milestones), [unified-knot.md](unified-knot.md) (current class-loading design), [improvement-plan.md](improvement-plan.md).

---

## 1. Goal

Vyne should be the lightest loader to run mixed Fabric + NeoForge packs, including on low-end PCs. Concretely:

- **No translation work while the game runs.** Converting a mod costs something once, when Vyne first sees it, and never again.
- **Every boot after the first is cheap.** No re-scanning or re-hashing of jars and no per-class remapping.
- **Only pay for what is used.** NeoForge game hooks that no installed mod listens to are not applied at all.
- **One code path.** No layers of fallbacks; a failure gives one clear error that names the mod.

## 2. How Vyne works today

Vyne is a fork of Fabric Loader. Fabric mods load normally. For NeoForge mods, Vyne currently:

1. On **every boot**: scans every mod jar to build a class-hierarchy index, hashes jars for cache keys, downloads mappings and the NeoForge jar if missing, and works out which NeoForge classes Vyne provides itself.
2. While the game runs, translates each NeoForge class **the first time the game loads it** (the transformation gate), and caches the result per class on disk.
3. Mounts the real NeoForge jar next to Vyne's own hand-written NeoForge classes ("shims").
4. Runs NeoForge mods' lifecycle from Vyne's own code, which is loaded by the JVM's application class loader, while the mods and NeoForge classes are loaded by Knot.

### Known problems (verified on real servers, 2026-11-08/09)

| Problem | Effect | Status |
|---|---|---|
| Two remappers ran back to back; the first left half-translated names | Inherited Minecraft methods (e.g. `player.getHealth()`) stayed unmapped, giving `NoSuchMethodError` | Fixed: the gate now uses the full remapper once |
| NeoForge mods ship no refmap, and Vyne didn't translate mixin annotations | Mods with mixins (Comforts, Farmer's Delight, Waystones, Create) stopped the server at startup | Fixed: `MixinAnnotationRemapper` rewrites mixins at load |
| Bundled libraries (`META-INF/jarjar/`) were never loaded | `NoClassDefFoundError` for SpectreLib, Registrate, … | Fixed in `ModDetector` |
| Stack-map frames merged unknown types to `Object` | `VerifyError` in mod code using multi-catch | Fixed in `MetadataClassWriter` |
| The raw NeoForge jar was mounted ahead of Vyne's own classes | Vyne's versions of `NeoForge`, `DeferredRegister`, `RegisterEvent` were silently shadowed; ~72 ERROR lines per NeoForge boot | Fixed: only classes Vyne's code depends on are Vyne's; the rest come from the real jar |
| **Class-loader split**: Vyne's code (app loader) references NeoForge classes directly | The app loader creates a second, untranslated copy of the class (e.g. `RegistryMirrorUtil` → `DeferredRegister` → `net/minecraft/core/Holder` not found). 22 Vyne classes reference 34 NeoForge/Forge classes this way | **Open**; solved structurally by section 4.1 |
| 44 FML / NeoForge-SPI classes missing (`ModLoader`, `ModLoadingIssue`, `IConfigSpec$ILoadedConfig`, …) | Mods that touch them fail | **Open**; solved by section 4.3 |
| NeoForge's changes to vanilla classes (events fired from inside Minecraft code, extension methods) don't exist on a Fabric game | Mods relying on them misbehave or crash | **Open**; solved by section 4.4 |
| Network payloads are registered twice | `Cannot register payload … already registered` | **Open** |
| Every datapack holder decode scanned all built-in registries by reflection, throwing ~10 exceptions per comparison | ~30 s of a ~58 s dedicated-server boot | Fixed (2026-11-14): `VyneRegistryInjector` indexes built-in registries once; `ReflectionHelper` caches method lookups |
| Many fallback layers and ~375 empty `catch {}` blocks | Extra work on every boot, real errors hidden | **Open**; removed step by step in section 6 |

## 3. Key idea

The JVM matches a method call to its target **once**, when a class is first used. After that the call is direct and costs nothing extra. So translation can happen at three points:

| When | Cost |
|---|---|
| On every call (a runtime "router") | Paid millions of times per tick. Ruled out. |
| When a class first loads (today) | Paid once per class per launch, plus scanning |
| **Once per mod, saved to disk (this design)** | **Zero at runtime.** The saved jar is an ordinary jar with Intermediary names. |

## 4. Target architecture

```
Vyne kernel (Fabric Loader fork)
 ├─ 4.1 reads fabric.mod.json AND neoforge.mods.toml / mods.toml natively; runs the FML lifecycle
 ├─ 4.2 built-in converter: Mojmap → Intermediary, mixins, jar-in-jar
 │      runs once per new or changed mod → .vyne/cache/converted/<key>.jar + index
 ├─ 4.3 bundled NeoForge API (real NeoForge, FML and bus jars, converted once per version)
 └─ 4.4 NeoForge game hooks as patches, applied only if an installed mod listens

Every boot: read the index → mount converted jars → run. No translation at runtime.
```

### 4.1 The loader layer (FML) lives in the kernel

The kernel understands NeoForge mod metadata and lifecycle the same way it understands Fabric's: `mods.toml` parsing, `@Mod` entrypoints, the mod event bus, `ModList`/`ModContainer` (`FMLModContainer`), configs and lifecycle events.

All code that touches NeoForge types runs **inside the game's class loader** (Knot). The launcher that starts in the app loader stays minimal and only passes plain Java values (paths, strings, lists) into the kernel. This removes the class-loader split and the reflection the bridge uses today, so the JIT can optimize those calls.

### 4.2 The converter (once per mod)

**Input:** a NeoForge/Forge mod jar. **Output:** a jar that runs on the Fabric-hosted game with no further translation.

Steps, all in one pass over the jar with the full class hierarchy known:

1. Expand bundled jars (`META-INF/jarjar/`). When several mods bundle the same library, the highest version wins, and a standalone copy in `mods/` beats any bundled one.
2. Remap all classes Mojmap → Intermediary, with hierarchy-aware member lookups. The current gate code (the `VyneRemapper` used by the gate) is the starting point.
3. Rewrite mixin classes (`MixinAnnotationRemapper`) so no refmap is needed.
4. Recompute stack-map frames.
5. Write `.vyne/cache/converted/<key>.jar` and an index entry: mod id, version, entrypoints, mixin configs, event subscriptions, and each class's superclass and interfaces.

**Cache key:** SHA-256 of the input jar + Vyne converter version + Minecraft version + NeoForge API version. If any of them changes, the mod is converted again.

**Boot-time check:** compare file size and modification time against the index. The jar is only hashed when those change.

**Parallelism:** convert independent mods on several threads; only new or changed mods are converted.

### 4.3 The NeoForge API, bundled and converted once

The real NeoForge jars (NeoForge universal, FML loader and the event bus) are converted with the same converter, once per NeoForge version, and shared by all mods. This replaces hand-written shims for the 44 missing FML/SPI classes and keeps the API complete.

Where the kernel has to take over (mod discovery, `ModList`, the lifecycle), the calls are **rewritten inside the converted jar to point at the kernel**, instead of shipping a second class with the same name and depending on load order.

Pruning the API to only what installed mods use is not worth it here: the JVM only loads classes that are actually used, so unused API classes cost disk space, not RAM or CPU.

### 4.4 Game hooks, only where used

NeoForge changes Minecraft itself in two ways:

- **Extension interfaces** with default methods (e.g. extra methods on `Item`, `Block`, `Entity`). The kernel adds these interfaces to the vanilla classes as they load. It's a small, one-time change per class.
- **Event hooks** inside vanilla code (block broken, entity joined, …). These are implemented as Vyne-owned patches (mixins).

During conversion, the index records which NeoForge events each installed mod subscribes to. **A hook is only applied if at least one installed mod listens to its event.** No loader does this today, and it keeps unused hooks completely out of the hot path.

Hooks are ported in order of how many real mods need them, starting with the events used by the compatibility test mods (section 7).

## 5. Runtime names: decision

The game can only run under one set of names.

| Option | Native | Converted |
|---|---|---|
| **Intermediary (recommended, current)** | Fabric mods, Fabric API | NeoForge mods, NeoForge API |
| Mojmap | NeoForge mods, NeoForge API, readable stack traces | Fabric mods and Fabric API |

Recommendation: **stay on Intermediary.** Fabric API is large and sensitive to remapping, Intermediary names are stable across Minecraft versions, and Fabric mods keep running exactly as their authors tested them.

## 6. Migration plan

Each step is small, keeps the game bootable, and has a test gate. Boot time and memory are measured before and after every step on the same mod set.

| Step | Work | Gate |
|---|---|---|
| 0 | Baseline: measure boot time, time to `Done`, peak memory for the smoke scenarios and the content mods | Numbers recorded in this file |
| 1 | Move the NeoForge-facing bridge into Knot (4.1); remove direct references from app-loaded Vyne code | No class-loader split errors; smoke suite green |
| 2 | Converter writes `.vyne/cache/converted/*.jar` + index; mount converted jars and skip the gate for them (4.2) | Second boot does no translation; boot time drops vs step 0 |
| 3 | Convert and mount the real FML + bus jars; delete shims they replace (4.3) | The 44 missing classes resolve; no new ERROR lines |
| 4 | Event index + hooks applied only when a mod listens (4.4), starting with events used by the test mods | Content mods register their blocks (block probes pass) |
| 5 | Remove fallback layers whose main path is now covered by a test | ERROR count on a clean boot is 0 |
| 6 | Optional: Java Class Data Sharing archive for faster startup | Measured improvement, or dropped |

### Progress

**Step 0, baseline (2026-11-14).** `neoforge-simple` scenario (MouseTweaks, dedicated server, `-Xmx2G`), wall time from process start to `Done`, warm caches, same machine:

| Build | Time to `Done` |
|---|---|
| Before | ~58 s |
| After the registry-lookup fix | ~26 s |
| After the fix, converter on vs. `-Dvyne.convert=false` | 26.0 s vs. 26.3 s (within noise on a mod this small) |

Peak memory is not recorded yet.

**Step 2, converter: first version landed (2026-11-14).** `dev.stolym.vyne.convert.ModConverter` translates each NeoForge/Forge mod jar and the stripped NeoForge API jar once, into `.vyne/cache/converted/<key>/<sha256>.jar`. `ConvertedModMount` (called from `Knot.init` right after the game provider is initialized) mounts the converted jar instead of the original, and `VyneTransformationGate` returns classes from converted jars untouched.

- **Cache key:** converter schema, mappings fingerprint, Vyne version and Vyne jar size/mtime, Minecraft version, NeoForge API jar name, plus the SHA-256 of the input jar. A jar is only hashed when its size or modification time differs from `index.properties`.
- **Deferred classes:** a class whose stack-map frames need a type that cannot be resolved ahead of time (for example a client-only Minecraft class on a server, or an optional dependency that is not installed), or whose translation throws, is copied unchanged and listed in `META-INF/vyne/deferred.list`. The gate translates those at runtime exactly as before. On the test mods this is 0 to 1 class per mod and 13 of 1468 NeoForge API classes.
- **Cleanup:** converted jars that no installed mod maps to, and cache directories for other keys, are deleted on boot.
- **Opt out:** `-Dvyne.convert=false`. Worker threads: `-Dvyne.convert.threads=N` (default: up to 4).
- **Measured:** first boot converts the NeoForge API in ~3 s and CraftPresence + UniLib (1450 classes) in ~1.8 s; later boots reuse everything in ~6 ms.

Not done yet for step 2: the per-boot hierarchy scan still runs (the index is not persisted), Vyne's own shim classes and mods of unknown loader type still go through the gate, and the smoke suite does not yet assert a second boot does no translation.

## 7. How progress is measured

- **Unit tests:** `./gradlew test`.
- **Smoke suite:** `./gradlew smokeTest` (dedicated servers with small mods). It should fail on ERROR-level registration failures, not only on crashes, and launch servers the same way the installer's `start.sh` does.
- **Content mods:** real NeoForge content mods (Comforts, Farmer's Delight, Waystones + Balm, Create) on a dedicated server, checked by placing their blocks with `/setblock` and verifying them with `/execute if block`. The script used so far should become a Gradle task, so the README's compatibility table can be generated from real runs.

## 8. Open questions

- Should conversion happen in the installer (ahead of time) as well as on first boot?
- How should converted jars be cleaned up when a mod is removed or updated?
- Which NeoForge hooks matter most beyond the test mods? Build a usage count from popular mods.
- Legacy Forge (pre-1.20.2): same converter with an SRG step, or out of scope?

## 9. Licensing

- Fabric Loader: Apache-2.0. Keep headers and notices in forked files.
- NeoForge, FML and the event bus: LGPL-2.1. Bundling converted jars is allowed if the license text ships with them, they are listed in `THIRD_PARTY_NOTICES.md`, and their source is available.
- Mojang mappings: downloaded at runtime, never bundled.
