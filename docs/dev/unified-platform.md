# Unified platform: Fabric and NeoForge on Vyne

Status: **brainstorm** (2026-11-14). This describes where Vyne is heading, not what exists today. It extends [kernel-architecture.md](kernel-architecture.md) (translate once, run native) and changes the goal of [heavy-mod-roadmap.md](heavy-mod-roadmap.md) from "run NeoForge mods on Fabric" to "run Fabric and NeoForge mods on Vyne".

---

## 1. Goal

Vyne is its own platform. Fabric mods and NeoForge mods both run **on Vyne**, as first-class citizens, in the same game.

- **Fabric is a starting point, not a dependency.** Vyne began as a fork of Fabric Loader because it is a small, working loader to build on. From here on, the loader core is Vyne's own code. Vyne does not track or merge upstream Fabric Loader.
- **Both APIs ship with Vyne.** The full Fabric API and the full NeoForge API are part of Vyne. Mods never need to install either one, and they talk to them directly: no emulation layer, no shims pretending to be the real thing.
- **One owner of the game.** Only Vyne changes vanilla Minecraft. Fabric API and NeoForge express what they need through Vyne, so they can never fight over the same code.
- **Easy for both sides.** Players drop any supported mod into `mods/` and it works. Mod developers build against the API they already know (Fabric API or NeoForge) with no Vyne-specific changes.

## 2. Architecture

```
          Fabric mods                 NeoForge mods
               │                            │
        Fabric API (bundled)         NeoForge API (bundled)
               └──────────────┬─────────────┘
                     Vyne hook layer
           (the only thing that patches vanilla Minecraft)
                              │
                     Vyne core (loader)
          discovery, mod metadata (fabric.mod.json and mods.toml),
          lifecycles, class loading, the converter, routing
                              │
                      Minecraft (vanilla)
```

### 2.1 Vyne core

The loader: finds mods, reads `fabric.mod.json` and `neoforge.mods.toml` / `mods.toml` natively, resolves dependencies across both ecosystems, runs both lifecycles (Fabric entrypoints and the FML lifecycle), owns class loading and the converter.

The FML loader layer (mod discovery, `ModList`, `ModContainer`, lifecycle events, mod event buses) lives **in the core**, not in a separate emulation layer. NeoForge's own code calls into it as if it were FML.

### 2.2 Hook layer

Fabric API and NeoForge both change vanilla Minecraft at mostly the same places (block broken, entity joined, item tooltips, registries, networking, rendering), each in its own way: Fabric API with mixins, NeoForge with source patches. On Vyne, **neither applies its own changes**. Vyne patches each place in vanilla once and fires both APIs from there.

Example: one Vyne hook where a block is broken fires Fabric's `PlayerBlockBreakEvents` and NeoForge's `BlockEvent.BreakEvent`. A cancel from either side cancels the break for both.

The same goes for extension methods: NeoForge's extra methods on `Item`, `Block`, `Entity` (extension interfaces) are added by the hook layer, once.

The hook layer is **derived from upstream, not hand-written from scratch**: its inputs are NeoForge's own patches for each version and Fabric API's mixins. The tooling (section 6) produces it; humans only resolve the places where the two disagree.

Hooks are applied only where an installed mod listens (as in kernel-architecture section 4.4), so unused hooks cost nothing.

### 2.3 Bundled APIs

- **Fabric API**: the whole API, every module, ships inside Vyne. Its mixins into vanilla are replaced by hook-layer calls. A mod that declares `depends: fabric-api` (or any `fabric-*` module id) is satisfied by the built-in copy. A Fabric API jar the player drops into `mods/` is ignored, with a short notice saying why.
- **NeoForge**: the real NeoForge API, FML and event bus, converted once per version (kernel-architecture section 4.3). A mod's `neoforge` version range is satisfied by the bundled version.

Both APIs are real upstream code, so behaviour matches what mod authors tested against.

## 3. Keeping up with NeoForge (and Fabric API) updates

Only one NeoForge version runs in a game at a time. The question is how Vyne follows NeoForge's frequent releases (bug fixes, optimisations, new features) without rewriting Vyne each time.

### 3.1 What a NeoForge release can change, and who pays for it

| Change | Example | Cost to Vyne |
|---|---|---|
| Inside NeoForge's own API | new event, renamed method, moved class | None: Vyne runs NeoForge's real code for that version, and mods built for it already expect the change |
| A NeoForge patch to vanilla | a new hook point, a changed hook | Picked up by the hook-layer tooling; human review only where it overlaps Fabric API |
| Where NeoForge meets the Vyne core | FML internals Vyne takes over (`ModLoader`, `ModContainer`, event dispatch) | The real breaking changes. Handled by routing data and core adapters (3.2) |
| Minecraft version jump | 1.21.1 → 1.21.x | A real port, as for every loader: mappings, patches, both APIs move at once |

### 3.2 Routing data instead of code

Where NeoForge's code must reach the Vyne core, the redirect is **data**: a routing table per NeoForge version line, applied by the converter when it builds that version.

```
net/neoforged/fml/ModLoader.postEvent(...)  ->  dev/stolym/vyne/core/fml/Lifecycle.post(...)
net/neoforged/fml/ModList.get()             ->  dev/stolym/vyne/core/fml/Mods.list()
```

A renamed or moved method in a new NeoForge release is a one-line change to the table, not a code change.

When NeoForge restructures something for real (the 1.20.2 move from Forge's loader to NeoForge's own is the reference case), the core gets a second implementation of that part for the new version range, picked at runtime. Old versions keep working on the old one.

### 3.3 Detecting breaks automatically

For every new NeoForge release, CI compares the signatures of everything in the routing table and everything the core calls in NeoForge against the previous supported version. The output is an exact list ("`ModContainer.getCustomExtension` changed its parameters"), so a break is known before any player sees a crash. Most releases are expected to show nothing.

The same check runs for Fabric API releases against the hook layer.

### 3.4 Which NeoForge version a pack gets

Vyne supports several NeoForge versions per Minecraft version, each prebuilt (section 6). For a given pack, Vyne picks **the newest supported version that satisfies every installed mod's range**, so players get the newest fixes and optimisations without doing anything. NeoForge is mostly backward compatible within a Minecraft version, so this is normally the latest one. If a mod needs something a newer version removed, a small adapter in the routing data covers it.

## 4. Mixins from mods

With Fabric API and NeoForge no longer mixing into vanilla themselves, what is left are mods that mix into vanilla directly. Vyne inspects them before the game starts:

| Case | What Vyne does |
|---|---|
| Two mods inject the exact same change at the same place | Detected by comparing the injected code after name normalisation; applied once, both mods benefit |
| Two mods change the same method at different points | Nothing needed: Mixin already composes these |
| Two mods replace the same method body or redirect the same call | Cannot be merged automatically in general. Vyne detects it before launch, applies a clear rule (config can override it), and tells the player which mods collide and what was chosen |

The promise is: everything that can run together does, and real conflicts are caught and explained instead of crashing or silently misbehaving.

## 5. The Guard (crash containment)

The Guard keeps the game running when a mod fails, freezes that mod, and tells the player. It is part of what makes Vyne user friendly, so it stays. Today it catches exceptions inside vanilla methods (the whole render frame, a full entity tick), which can leave the game half-way through an operation: items taken out of one slot but never put in the other, a block entity with half its fields updated. The target design removes that risk:

1. **Contain at the mod's edge, not inside vanilla.** The Guard wraps only the call into a mod's code: an event listener, a mod block entity's tick, a mod screen. Vanilla never stops half-way, because the failure happens before control returns to it. With the hook layer, every listener is called by Vyne's dispatcher, so one containment point covers Fabric and NeoForge mods alike.
2. **Only contain mod errors.** The stack trace says whose code threw. If it is a mod's, contain it. If vanilla itself fails, crash normally: hiding a real Minecraft bug helps nobody.
3. **Remove the broken object, not just the error.** A block entity or entity whose tick keeps failing is removed (the block itself stays), like Forge's old `removeErroringBlockEntities` option. The world stays consistent and the log is not flooded twenty times a second.
4. **Freeze the whole mod.** Once a mod is frozen, all of its listeners, ticks and screens stop together, so it never runs half-working. The Mods screen shows it in red with the reason.
5. **Protect the save.** After the first contained error, the startup world snapshot is kept no matter what and the player is told a restore point exists. If errors keep coming, Vyne saves and stops cleanly instead of carrying on.

A mod can still damage its own data before it throws, but that is limited to the failing mod, which is frozen right after.

## 6. Prebuilt packages

Players should not wait for patching and conversion on first launch. Vyne publishes prebuilt packages:

- **Per NeoForge version:** NeoForge's patches turned into hook-layer input, NeoForge API/FML/bus converted, routing table applied and verified.
- **Per Fabric API version:** Fabric API with its vanilla mixins turned into hook-layer calls.
- **Per Minecraft version:** the merged hook layer.

A build pipeline produces them: apply patches, convert, check routes and signatures (3.3), run the smoke suite, sign, publish. The runtime and the pipeline use the same converter, so they cannot drift apart. Vyne downloads the packages it needs and verifies their signatures; the on-first-launch conversion that exists today stays as a fallback for versions without a prebuilt package.

Licensing: NeoForge, FML and the event bus are LGPL-2.1, so hosting converted builds is allowed with the license text, an entry in `THIRD_PARTY_NOTICES.md` and a pointer to the source. Fabric API is Apache-2.0.

## 7. Performance

Being based on Fabric does not make Vyne light by itself: with NeoForge's patched game and both APIs, the starting weight is about NeoForge's. Vyne aims to beat NeoForge where the design allows it, and to prove it with numbers rather than claims.

Where Vyne can be lighter than NeoForge:

- **Work done once, not every launch.** NeoForge rescans every mod jar for annotations, applies access transformers and builds its module layers on every start. Vyne converts mods once and caches the result; the cache grows to cover scan results and the patched game, so a second launch skips almost all of it.
- **Hooks only where a mod listens** (section 2.2). NeoForge fires its events everywhere, some per entity per tick with an allocation each time. Vyne leaves out hooks no installed mod listens to.
- **Prebuilt packages** (section 6): no patching or conversion on the player's machine.
- **Class Data Sharing** archive for faster JVM startup (kernel-architecture step 6), if measurements show a gain.

API size matters less than it seems: the JVM only loads classes that are used, so an unused API costs disk, not memory or CPU. In-game tick time in a real pack is dominated by the mods themselves; Vyne's goal there is to add nothing on top.

### 7.1 Benchmark (release requirement)

Before a public release, the same mod sets run on Fabric, NeoForge and Vyne (using each mod's Fabric or NeoForge build where available, and both together on Vyne), on the same machine and Java version, and the results are published in the README, including the cases where Vyne is slower.

| Measurement | Fabric | NeoForge | Vyne |
|---|---|---|---|
| Client: start to title screen, first launch | not measured | not measured | not measured |
| Client: start to title screen, later launches | not measured | not measured | not measured |
| Server: start to `Done`, later launches | not measured | not measured | not measured |
| Peak memory to title screen | not measured | not measured | not measured |
| Server tick time (ms per tick) in a loaded world | not measured | not measured | not measured |
| Disk used by the loader and its caches | not measured | not measured | not measured |

Mod sets: no mods; a small utility pack; a content-heavy pack (Create class). The benchmark becomes a Gradle task so it can be rerun for every release.

## 8. Relation to the code today

Already in place and reused by this design:

- **The converter** (`dev.stolym.vyne.convert.ModConverter`): translates a jar once, caches it, falls back to the runtime gate for the few classes it cannot resolve ahead of time. This becomes the core of the build pipeline.
- **The real NeoForge API mount** (`RealNeoForgeApiMount`): the first step toward a bundled NeoForge.
- **NeoForge access transformers** applied by the core (`AccessTransformerBridge`).
- **FML pieces** already implemented in the core: `ModList`, `ModContainer` extension points, `ModLoadingContext`, the `RegisterKeyMappingsEvent` timing.

To be replaced over time: the hand-written NeoForge classes (`src/main/java/net/neoforged/**`, `net/minecraftforge/**`) that exist only to imitate NeoForge, and Vyne's own ad-hoc mixins that patch the same places NeoForge does.

## 9. First steps

1. **Measure the overlap.** Done 2026-11-14, see 9.1.
2. **One hook end to end.** Pick a hook both APIs use (block broken is a good candidate). Implement it in the hook layer, fire both events from it, remove both upstream patches for it, and prove with a test mod from each side that events and cancellation work.
3. **Routing table, first version.** Move the FML redirects that exist today into a data file applied by the converter, and add the signature check from 3.3 to CI.
4. **Bundle Fabric API.** Ship it inside Vyne and resolve `fabric-api` dependencies to the built-in copy.

### 9.1 Overlap measurement (2026-11-14)

Inputs: NeoForge 21.1.265's patched client classes (`neoforge-21.1.265-client.jar`, produced by NeoForge's installer, Mojang names) against vanilla 1.21.1 with Mojang names, and Fabric API 0.116.17+1.21.1 (every module's mixins, with method names resolved through each module's refmap and translated Intermediary → Mojang). Method bodies were compared instruction by instruction, ignoring line numbers and stack frames.

| | Count |
|---|---|
| Vanilla classes | 8269 |
| Classes NeoForge ships patched | 1613 |
| Classes NeoForge really changes (the rest differ only in line numbers) | 1023 |
| Methods NeoForge changes / adds | 3370 / 1573 |
| Classes NeoForge gives extra interfaces (extension methods) | 92 |
| Fabric API mixin classes / vanilla classes they target | 334 / 256 |
| Vanilla classes touched by both | 184 (71% of Fabric API's targets) |
| Methods changed by both | 209 of 359 Fabric API method targets (58%) |

Classes with the most NeoForge hook calls added: `LivingEntity` (69), `Minecraft` (54), `Gui` (44), `Player` (42), `ServerLevel` (33), `MinecraftServer` (32).

What this means:

- NeoForge's patches are a superset in practice: they cover far more of the game than Fabric API's mixins do, and Fabric API's hooks mostly sit in places NeoForge already hooks.
- Running Fabric API's own mixins on top of NeoForge-patched code would hit the same method in more than half of Fabric API's targets. Many injections at different points would still compose, but a large share would not.
- So the hook layer starts from **NeoForge's patches as the base**. Fabric API's events are fired from the matching NeoForge hooks where both exist (the 209 shared methods), and Fabric API's own mixins are kept only where NeoForge has no hook (the remaining 150 method targets in 72 classes). This is the same direction Sinytra's Forgified Fabric API took for Fabric-on-NeoForge, which may be a useful reference (check its license and 1.21.1 support first).

### 9.2 Patched game prototype (2026-11-14)

Running Minecraft with NeoForge's own patches, behind dev properties (`-Dvyne.neoforge.patchedGame`, `-Dvyne.neoforge.patchedClasspath`, `-Dvyne.neoforge.fmlJar`) until prebuilt packages exist.

- **Producing the patched game:** NeoForge's installer (`--install-server <dir>`) runs NeoForge's own tools and outputs the patched classes in 25 s. NeoForge's binary patches are diffs against the exact bytes its own renaming tool produces, so the pipeline runs NeoForge's tools rather than reimplementing them.
- **Loading it:** `PatchedGameBuilder` renames the patched classes to the runtime names with tiny-remapper (4.5 s, cached), and `GameOverlay` serves them to Knot ahead of vanilla. 1201 patched classes on a dedicated server.
- **Naming conflict, client only:** NeoForge gives the client and server packet listeners a shared `ICommonPacketListener.send(Packet)`. In Intermediary those are two different methods (`method_52787`, `method_14364`), so the client build cannot be renamed consistently. The dedicated server leaves out client classes and is not affected. This is a concrete case for the Mojang-names runtime (section 10): with Mojang names the patched game needs no renaming at all.
- **FML:** the patched game needs FML. NeoForge's real FML jar (4.0.45 for NeoForge 21.1.265) is mounted, minus the classes Vyne's core provides. `ModLoader` is provided by the core and posts to Vyne's mod buses through `ModBusRegistry`. ModLauncher's stack-trace helper is provided by the core.
- **Where it stops:** the server gets through bootstrap into `Block` initialisation, where NeoForge's real `GameData` posts `RegisterEvent`. Vyne's own `RegisterEvent` (an old imitation used by Vyne's registry bridge) shadows the real one and does not extend `Event`.
- **Next:** in patched mode NeoForge's real classes must win over every Vyne imitation of the same name (the full NeoForge API jar and the real event bus jar), and Vyne's registry and event bridging must be switched off, because NeoForge's own code now does that work. That requires removing the class-loader split first (kernel-architecture step 1): Vyne's lifecycle code still runs in the launcher's class loader and would hand objects to NeoForge's classes from a different loader.

### 9.3 Patched game boots (2026-11-15)

A dedicated server running NeoForge 21.1.265's own patched game reaches `Done` with Mouse Tweaks, CraftPresence and UniLib, with 0 ERROR lines. NeoForge's own `ServerModLoader` drives the lifecycle; Vyne's core answers FML's calls. What it took:

- **Entrypoint.** Fabric's entrypoint patcher read `Main` straight from the vanilla jar, so NeoForge's call into its mod loader never ran. It now reads from the overlay, and patched mode is prepared before Fabric patches entrypoints.
- **One rename run.** Renaming the patched game and NeoForge's API in separate runs gave different names to the same method (`ChunkAccess.getLevel`, added by NeoForge, became `method_12200` on one side only). The patched game, NeoForge's API and the NeoForge mods are now renamed together by tiny-remapper with the full hierarchy, then routed.
- **Guessing removed.** Vyne's own remapper guessed names when it could not see a class's hierarchy and renamed unrelated DataFixerUpper methods (`elementCodec` became `comp_986`). Patched mode no longer uses it.
- **Lambdas.** Lambdas implementing Minecraft interfaces kept Mojang method names; now renamed with the interface method (fixed in Vyne's remapper too, so the normal path benefits).
- **Core FML layer** (`src/fml/java`, compiled against real FML): 7 routed classes plus the container, scanner and discovery. Real FML provides everything else (configs, `ModLoadingContext`, `ModContainer`, version checker, work queues).
- **Libraries.** FML's own libraries are mounted (SecureJarHandler, JLine, terminal appender); night-config upgraded to 3.8.3; Vyne's hand-written Maven version classes replaced by the real library.
- **Client classes on the server.** NeoForge's server output carries 384 client classes; 3 are used by server code (LAN pinger) and kept, the rest are left out.

Still open: the client in patched mode, the source of the patched game and FML jars (dev properties today), NeoForge's own mixin config, content mods.

## 10. Decisions

- **Runtime names: Mojang names** (2026-11-14). The game runs with Mojang's names. NeoForge mods and the NeoForge API load as built; Fabric mods and Fabric API are converted once from Intermediary, by the same converter that converts NeoForge mods today. Reasons: readable stack traces and logs (`Minecraft.runTick`, not `class_310.method_1523`), and Vyne's own code becomes readable too. This replaces the Intermediary choice in kernel-architecture section 5.
- **Vyne compiles against the real game, not stubs.** The hand-written Minecraft stand-ins in `src/main/java/net/minecraft/` and `src/mock/java/net/minecraft/` (80 files) are removed. A build step produces a Mojang-named Minecraft jar used only for compiling. Order: build step, port Vyne's code from Intermediary names to Mojang names, delete the stubs, then switch the runtime.

- **The Guard stays** (2026-11-14). Crash containment (catching a mod's exception in rendering, ticking, screens or input, freezing that mod and keeping the game running) is part of what makes Vyne user friendly. Its mixins are kept and ported to Mojang names; in the hook-layer design it wraps hook dispatch, so one containment point covers both Fabric and NeoForge listeners.

## 11. Open questions

- **Conflict rule.** For real mixin collisions (section 4), what is the default winner: load order, mod priority declared in metadata, or the player's choice in a config file?
- **Legacy Forge.** Same platform with an extra SRG naming step, or out of scope?
- **Vyne's own API.** Once both APIs live on one hook layer, is there value in a small Vyne API for things neither offers (cross-ecosystem queries, conflict reporting), or should Vyne stay invisible to mod developers?
- **Package hosting.** Where prebuilt packages live (GitHub Releases, a Maven repository, our own CDN), and how a player's Vyne finds and updates them.
