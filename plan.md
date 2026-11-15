# Vyne plan

The single place that says what Vyne is becoming, in what order, and where each part stands. The working checklist is [to-do.md](to-do.md). Design detail lives in [docs/dev/unified-platform.md](docs/dev/unified-platform.md) (the target) and [docs/dev/kernel-architecture.md](docs/dev/kernel-architecture.md) (translate once, run native).

Last updated: 2026-11-15.

---

## 1. Goal

Vyne is its own Minecraft platform. Fabric mods and NeoForge (and later Forge) mods run **on Vyne**, side by side, as first-class citizens.

- **Fabric is a starting template, not a dependency.** Vyne began as a fork of Fabric Loader. The loader core is now Vyne's own code and does not follow upstream Fabric Loader.
- **Both APIs ship inside Vyne.** The full Fabric API and the full NeoForge API (with FML and the event bus) are part of Vyne. Players never install either, and mods talk to them directly: no emulation layer, no imitation classes.
- **Only Vyne changes vanilla Minecraft.** Each place in the game is patched once, and that one hook serves both APIs, so Fabric API and NeoForge can never fight over the same code.
- **Easy for players and for developers.** Players drop any supported mod into `mods/`. Developers build against Fabric API or NeoForge as they already do.
- **Lighter where the design allows it.** Work is done once (at build time or first start), never on every launch; hooks nobody listens to are left out.

**Main objective right now: make Vyne work.** Everything else (cleanups, polish, small fixes) waits unless it blocks that.

## 2. Target architecture

```
          Fabric mods                 NeoForge mods
               |                            |
        Fabric API (bundled)         NeoForge API + FML + bus (bundled, real code)
               +--------------+-------------+
                     Vyne hook layer
           (the only thing that patches vanilla Minecraft;
            built from NeoForge's own patches, Fabric API
            events fired from the same hooks)
                              |
                     Vyne core (loader)
      mod discovery and metadata for both ecosystems, both lifecycles,
      class loading, the converter, routing, the Guard
                              |
                      Minecraft (vanilla, Mojang names)
```

## 3. Decisions made

| Decision | Date | Why |
|---|---|---|
| Unified Knot: every mod and both APIs load in one class loader | 2026-10 | Mods see each other; no isolation bugs |
| Translate each mod jar once, cache it, never at runtime | 2026-11-14 | No runtime translation tax |
| NeoForge's own patches are the base of the hook layer | 2026-11-14 | Measured: NeoForge changes 1023 classes / 3370 methods; 58% of Fabric API's method targets are methods NeoForge already changes |
| Run NeoForge's own installer tools to produce the patched game | 2026-11-14 | Its binary patches only apply to the exact bytes its own tools produce |
| Use NeoForge's real FML jar; Vyne's core provides only the parts that route into Vyne | 2026-11-14 | Writing imitations class by class does not scale |
| Fabric API and NeoForge bundled; mods' `fabric-api` / `neoforge` dependencies resolve to the built-in copies | 2026-11-14 | Mods talk to real code directly |
| Runtime names: Mojang names; Fabric mods get converted once | 2026-11-14 | Readable logs and code; the patched game needs no renaming (the Intermediary build already hit a naming conflict on the client) |
| Compile against real Minecraft, delete the 80 hand-written Minecraft stand-ins | 2026-11-14 | Stand-ins drift from the real game |
| Several NeoForge versions supported per Minecraft version; a pack gets the newest one every installed mod accepts | 2026-11-14 | Players get NeoForge's fixes and optimisations; one version runs per game |
| Prebuilt packages per NeoForge / Fabric API version, built and checked on our side | 2026-11-14 | No first-launch wait for players |
| Breaking NeoForge changes: routing data per version line, core adapters for real restructures, automatic signature diff per release | 2026-11-14 | Updating NeoForge must not mean rewriting Vyne |
| The Guard stays, redesigned to contain at the mod's edge | 2026-11-14 | User friendly without leaving the world half-updated |
| Loading screens and other Vyne UI follow the legacy Minecraft / Forge pixel style | 2026-11-14 | Fits the game |

## 4. Phases

Each phase has a gate: a check that proves it is done. Phases overlap where they can, but a phase is not "done" until its gate passes.

### Phase 0: Foundations (done)

- Unified Knot class loading; legacy isolated loaders retired behind a flag.
- Converter: NeoForge mod jars and the NeoForge API translated once and cached (`dev.stolym.vyne.convert`).
- Real NeoForge API jar mounted instead of most hand-written copies.
- Mixin translation for NeoForge mods (`MixinAnnotationRemapper`), jar-in-jar libraries, stack-map fixes.
- Boot stall fixed (registry lookup reflection): server boot ~58 s to ~26 s.
- Client: NeoForge mods initialise (CraftPresence, UniLib, Mouse Tweaks), access transformers, key mappings, config screens.
- Client UX: early loading window, loading overlay animation, Mods screen, Vyne branding, theme file.
- **Gate (passed):** `./gradlew check` green; all five server smoke scenarios reach `Done`; client reaches the title screen with the test mods.

### Phase 1: One class loader for everything that touches the game (done 2026-11-14)

Today Vyne's own lifecycle code runs in the launcher's class loader while NeoForge's real classes run in the game's (Knot), so the same NeoForge class exists twice and objects cannot be passed between the two sides. This blocks every later phase.

- Split Vyne into a **launcher side** (starts the game: mod discovery, mappings, converter, caches, early window, logging, shared state) and a **game side** (everything that touches Minecraft, NeoForge or Fabric API types).
- The launcher side never references game types; it hands plain values (paths, strings, lists) to the game side.
- The game side loads in Knot together with the mods.
- **Gate:** no Vyne class that touches NeoForge, Minecraft or Fabric API types is loaded by the launcher's class loader (checked by `LoaderAudit`); smoke suite and client test still pass.

### Phase 2: Run on NeoForge's patched game (server works for the test mods, 2026-11-15; still behind dev flags)

- Produce the patched game with NeoForge's own installer tools (prototype done for 21.1.265, server).
- Serve the patched classes to the game ahead of vanilla (`GameOverlay`, done).
- Mount NeoForge's real FML jar; the core provides `ModLoader` and the other routing points (done for what the server needs so far).
- In patched mode, NeoForge's real classes win over every Vyne imitation of the same name (full NeoForge API jar, real event bus jar, real FML jar); Vyne's own registry and event bridging is switched off, because NeoForge's own code does that work.
- **Routing table, first version.** The FML classes only Vyne can answer for (`ModList`, `ModLoader`, `FMLLoader`, `LoadingModList`; later the client's early-display classes) are renamed to Vyne's core implementations in `dev.stolym.vyne.fml` while NeoForge's jars, the patched game, FML itself and the mods are converted. Everything else is NeoForge's real code. The core FML layer is compiled in its own source set against real FML, so it cannot drift from the real API.
- Vyne's lifecycle drives NeoForge's real lifecycle (mod construction, mod buses, setup events) through the core's FML layer.
- **Gate:** dedicated server with NeoForge's patched game reaches `Done` with the NeoForge smoke mods, and their content registers through NeoForge's own registry code.

### Phase 3: Mojang names at runtime

- Build step: a Mojang-named Minecraft jar for compiling Vyne.
- Port Vyne's own code from Intermediary names to Mojang names; delete the 80 Minecraft stand-ins and the mixins the new design no longer needs.
- Switch the runtime to Mojang names: the patched game runs as NeoForge produces it; Fabric mods and Fabric API are converted once from Intermediary.
- **Gate:** client and server run the patched game with no renaming step; Fabric-only and mixed smoke scenarios pass with converted Fabric mods.

### Phase 4: Fabric API on the hook layer

- Bundle Fabric API inside Vyne; resolve `fabric-api` and `fabric-*` dependencies to it; ignore a Fabric API jar in `mods/` with a notice.
- For the 209 methods both patch: fire Fabric API's events from NeoForge's hooks instead of Fabric API's mixins.
- Keep Fabric API's own mixins only where NeoForge has no hook (about 150 method targets in 72 classes).
- First hook end to end: block breaking fires both `PlayerBlockBreakEvents` and `BlockEvent.BreakEvent`; cancelling either cancels the break.
- **Gate:** a Fabric test mod and a NeoForge test mod both receive and can cancel the shared events; the mixed smoke scenario passes with no Fabric API mixin failures.

### Phase 5: Mod compatibility

- Mixins from mods: detect exact duplicates and apply once; detect real collisions before launch and report them clearly (section 4 of the design).
- Content mods: Comforts, Farmer's Delight, Waystones, then Create, each in its own smoke scenario, working through failures one by one.
- Library mods tier (UniLib-class), compatibility table generated from real runs.
- **Gate:** a content-mod smoke scenario reaches `Done` and its blocks can be placed and checked in game.

### Phase 6: The Guard, redesigned

- Contain at the mod's edge (listeners, mod block entities, mod screens), not inside vanilla methods.
- Contain only errors thrown by mod code; let vanilla failures crash normally.
- Remove a failing block entity or entity instead of retrying it every tick.
- Freeze the whole mod at once; show it in red in the Mods screen with the reason.
- Keep the startup snapshot after the first contained error; save and stop cleanly if errors keep coming.
- **Gate:** chaos scenarios (a deliberately broken mod in each spot) keep the server running with a consistent world and a clear report.

### Phase 7: Updates without rewrites

- Routing table per NeoForge version line, applied by the converter.
- Core adapters selected by NeoForge version range for real restructures.
- CI job: signature diff of everything the routing table and core touch, for every new NeoForge and Fabric API release.
- Overlap / break analysis tool moved from a scratch script into the repo.
- **Gate:** moving to a newer NeoForge 21.1.x is a version bump plus whatever the diff report lists.

### Phase 8: Prebuilt packages

- Pipeline: run NeoForge's tools, build the patched game, convert, verify routes, run the smoke suite, sign, publish.
- Vyne downloads and verifies the packages it needs; first-launch conversion stays as the fallback.
- Pick the newest supported NeoForge version every installed mod accepts.
- **Gate:** a fresh install starts without any patching or conversion on the player's machine.

### Phase 9: Release

- Benchmark Fabric vs NeoForge vs Vyne and publish the table (design section 7.1).
- CI green on every push; release workflow; docs and README match reality; third-party notices complete (verify Launchwrapper's license).
- Commit history reviewed and published to `VyneMC/Vyne`.
- **Gate:** `0.1.0-alpha` tagged with jar, installer, notes and benchmark.

## 5. Where things live

| What | Where |
|---|---|
| Target design and measurements | `docs/dev/unified-platform.md` |
| Translate-once design, earlier problems list | `docs/dev/kernel-architecture.md` |
| Capability roadmap (heavy mods) | `docs/dev/heavy-mod-roadmap.md` |
| Small fixes noticed along the way | `docs/dev/small-fixes.md` |
| Working checklist | `to-do.md` |
| Player-facing state | `README.md`, `docs/playtest.md` |
