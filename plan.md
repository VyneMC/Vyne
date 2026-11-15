# Vyne plan

The single place that says what Vyne is becoming, in what order, and where each part stands. The working checklist is [to-do.md](to-do.md). Design detail lives in [docs/dev/unified-platform.md](docs/dev/unified-platform.md) (the target) and [docs/dev/kernel-architecture.md](docs/dev/kernel-architecture.md) (translate once, run native).

Last updated: 2026-11-15.

---

## 1. Goal

Vyne is its own Minecraft mod loader. It loads Fabric mods and NeoForge mods **natively**, side by side, as first-class citizens.

- **Vyne patches vanilla itself.** Vyne starts from vanilla Minecraft and changes it with its own hook layer. Everything NeoForge changes in the game and everything Fabric API changes in the game is done by Vyne, once per spot, and each hook serves both ecosystems.
- **Vyne contains both APIs.** The `net.neoforged.*` API that NeoForge mods call and the `net.fabricmc.fabric.api.*` API that Fabric mods call are part of Vyne and run on Vyne's hooks. Mods are not rerouted to a separate NeoForge or Fabric API install; there is none.
- **Vyne's own loader.** Vyne reads `neoforge.mods.toml` and `fabric.mod.json` itself and runs both lifecycles. Fabric Loader was the starting template; the loader core is now Vyne's code.
- **Vyne's own hooks, later.** On top of the two ecosystems, Vyne will add hooks of its own so mods can do more than either API allows. Not started yet.
- **Easy for players and developers.** Players drop any supported mod into `mods/`. Developers build against Fabric API or NeoForge as they already do.
- **Lighter where the design allows it.** Work is done once (at build time or first start), never on every launch; hooks nobody listens to are left out.

**Main objective right now: make Vyne work.** Everything else (cleanups, polish, small fixes) waits unless it blocks that.

## 2. Target architecture

```
          Fabric mods                 NeoForge mods
               |                            |
     Fabric API (Vyne's copy)      NeoForge API (Vyne's copy)
               +--------------+-------------+
                     Vyne hook layer
           (Vyne's own transformers on vanilla Minecraft;
            one hook per spot, firing both APIs' events;
            later also Vyne's own hooks)
                              |
                     Vyne core (loader)
      mod discovery and metadata for both ecosystems, both lifecycles,
      class loading, mod mixins, the Guard
                              |
                      Minecraft (vanilla, Mojang names)
```

## 3. Decisions made

| Decision | Date | Why |
|---|---|---|
| Unified Knot: every mod and both APIs load in one class loader | 2026-10 | Mods see each other; no isolation bugs |
| Translate each mod jar once, cache it, never at runtime | 2026-11-14 | No runtime translation tax |
| **Vyne patches vanilla itself; NeoForge's patched game is not the base** | 2026-11-15 | Vyne is its own loader, not NeoForge with Fabric on top (that would be a loader-level Sinytra Connector) |
| **The NeoForge API inside Vyne is Vyne's copy of NeoForge's source** (LGPL-2.1, credited), rewired onto Vyne's hooks and owned from then on | 2026-11-15 | Exact signatures and behaviour mods expect, without NeoForge's patches or installer |
| **Vyne's hook layer is generated, then owned**: a Vyne tool lists every spot NeoForge's patches and Fabric API's mixins change in vanilla; Vyne's hooks are written as Vyne transformers from that list and maintained as Vyne code | 2026-11-15 | Complete coverage of what mods rely on, without hand-searching 1023 classes |
| Patched mode (NeoForge's own patched game behind Vyne's loader) stays as a **temporary** path and test reference until Vyne's hook layer covers NeoForge; then it is deleted | 2026-11-15 | NeoForge packs keep running meanwhile; Vyne's hooks can be checked against NeoForge's real behaviour |
| Fabric API is part of Vyne the same way (Vyne's copy, its mixins replaced by Vyne hooks where a shared hook exists); a Fabric API jar in `mods/` is ignored with a notice | 2026-11-14 | One owner of every spot in the game |
| Runtime names: Mojang names; Fabric mods get converted once | 2026-11-14 | Readable logs and code; NeoForge's API source and NeoForge mods need no renaming |
| Compile against real Minecraft, delete the hand-written Minecraft stand-ins | 2026-11-14 | Stand-ins drift from the real game |
| Several NeoForge API versions supported over time; updating means regenerating the hook list and merging NeoForge's API changes into Vyne's copy | 2026-11-15 | Players get NeoForge's fixes without Vyne becoming NeoForge |
| The Guard stays, redesigned to contain at the mod's edge | 2026-11-14 | User friendly without leaving the world half-updated |
| Loading screens and other Vyne UI follow the legacy Minecraft / Forge pixel style | 2026-11-14 | Fits the game |
| Superseded: "NeoForge's own patches are the base of the hook layer", "run NeoForge's installer tools", "use NeoForge's real FML jar", "prebuilt packages of NeoForge's patched game" | 2026-11-14, superseded 2026-11-15 | Only used by the temporary patched mode now |

## 4. Phases

Each phase has a gate: a check that proves it is done. Phases overlap where they can, but a phase is not "done" until its gate passes.

### Phase 0: Foundations (done)

- Unified Knot class loading; legacy isolated loaders retired behind a flag.
- Converter: mod jars translated once and cached (`dev.stolym.vyne.convert`).
- Client UX: early loading window, loading overlay animation, Mods screen, Vyne branding, theme file.
- **Gate (passed):** `./gradlew check` green; all five server smoke scenarios reach `Done`; client reaches the title screen with the test mods.

### Phase 1: One class loader for everything that touches the game (done 2026-11-14)

- Vyne split into a launcher side and a game side; the game side loads in Knot with the mods.
- **Gate (passed):** no game-side Vyne class loaded by the launcher's class loader; smoke suite and client pass.

### Phase 2: Temporary patched mode (done 2026-11-15)

NeoForge's own patched game behind Vyne's loader. Not the long-term design (see decisions); kept so NeoForge-only packs run while Vyne's hook layer is built, and as the reference Vyne's hooks are tested against.

- NeoForge's installer run by Vyne on first start (`NeoForgeInstall`), patched game renamed in one exact pass (`PatchedGameBuilder`, `ExactRemap`, `SharedMethodSplit`), FML routing points answered by `dev.stolym.vyne.fml`.
- **Gate (passed):** NeoForge-only server and client reach `Done` / the title screen with the test mods, no developer flags. Mixed packs (Fabric API + NeoForge) do not work in patched mode: Fabric API's mixins target code NeoForge's patches changed. That is solved by Phases 5 and 6, not by patched mode.

### Phase 3: Hook inventory (done 2026-11-15)

- Tool in the repo (from the scratch overlap analysis): diff vanilla against NeoForge's patched classes, method by method, and list what each patch adds (calls into NeoForge, interfaces added to vanilla classes, fields and methods added, changed control flow).
- Same tool lists every Fabric API mixin target in vanilla.
- Output: a data file per NeoForge / Fabric API version, grouped by spot in the game, showing which spots both change.
- **Gate (passed):** `./gradlew hookInventory` writes `data/hooks/` for NeoForge 21.1.265 and Fabric API 0.116.17: 938 vanilla classes changed by NeoForge, 211 methods changed by both (the earlier name-only measurement said 209). See `docs/dev/hook-inventory.md`.

### Phase 4: Compile against real Minecraft with Mojang names (done 2026-11-15)

- `./gradlew minecraftMojangJar` builds the vanilla client with Mojang names (`build/minecraft/`); `gradle/minecraft.gradle` declares Minecraft's own libraries from Mojang's version manifest and exposes the whole compile classpath as `minecraftMojang`.
- **Gate (passed):** code using entities, blocks, codecs, Brigadier and the dedicated server compiles against it.
- Switching the *runtime* to Mojang names (and porting Vyne's own code, deleting the Minecraft stand-ins) moved to Phase 6b: most of the code that would need porting is the legacy NeoForge path Phase 6 deletes.

### Phase 5: Vyne's hook layer and Vyne's NeoForge API

- Vyne's copy of NeoForge's API source in the repo (own source set), compiled against `minecraftMojang` and Vyne's hook layer; the parts of FML that mods call are provided by Vyne's loader. Until Phase 6b it is converted once to the current runtime names, like the NeoForge API jar today.
- Vyne transformers for each spot in the inventory: add NeoForge's extension interfaces to vanilla classes and insert the event calls, one transformer per spot, ready to fire Fabric API's events from the same hook.
- Order: what the test mods need first (lifecycle, registries, extension interfaces, client setup, key mappings), then by how many mods use a spot.
- **Gate:** the NeoForge-only smoke scenarios and the client reach `Done` / the title screen on vanilla plus Vyne's hooks, with patched mode off.

### Phase 6: Fabric API on Vyne's hook layer

- Vyne's copy of Fabric API; for spots NeoForge also changes, its mixins replaced by Vyne hooks; its other mixins kept as they are.
- First shared hook end to end: block breaking fires both `PlayerBlockBreakEvents` and `BlockEvent.BreakEvent`; cancelling either cancels the break.
- **Gate:** the mixed smoke scenario passes; a Fabric test mod and a NeoForge test mod both receive and can cancel the shared events. Patched mode and the legacy NeoForge path are deleted.

### Phase 6b: Mojang names at runtime

- Port what is left of Vyne's code to Mojang names; delete the Minecraft stand-ins and the mixins the new design no longer needs.
- Fabric mods and Fabric API converted once from Intermediary; Vyne's NeoForge API copy and NeoForge mods need no renaming.
- **Gate:** client and server run vanilla with Mojang names; all smoke scenarios pass.

### Phase 7: Mod compatibility

- Mixins from mods: detect exact duplicates and apply once; report real collisions before launch.
- Content mods: Comforts, Farmer's Delight, Waystones, then Create, each in its own smoke scenario.
- **Gate:** a content-mod smoke scenario reaches `Done` and its blocks can be placed and checked in game.

### Phase 8: The Guard, redesigned

- Contain at the mod's edge (listeners, mod block entities, mod screens), not inside vanilla methods; only errors thrown by mod code; remove a failing block entity or entity instead of retrying it; freeze the whole mod and show it in red in the Mods screen; keep the startup snapshot; save and stop cleanly if errors keep coming.
- **Gate:** chaos scenarios keep the server running with a consistent world and a clear report.

### Phase 9: Updates without rewrites

- For each new NeoForge / Fabric API release: regenerate the inventory, diff it against the previous one, merge the API changes into Vyne's copies, adjust the hooks the diff lists.
- A Gradle task (and CI job) that produces that report automatically.
- **Gate:** moving to a newer NeoForge 21.1.x is the report plus the changes it lists.

### Phase 10: Release

- Benchmark Fabric vs NeoForge vs Vyne and publish the table (design section 7.1).
- CI green on every push; release workflow; docs and README match reality; third-party notices complete (NeoForge and Fabric API source copies credited).
- **Gate:** `0.1.0-alpha` tagged with jar, installer, notes and benchmark.

### Later: Vyne's own hooks

Hooks that neither NeoForge nor Fabric API offers, for mods that need more. Not planned in detail yet.

## 5. Where things live

| What | Where |
|---|---|
| Target design and measurements | `docs/dev/unified-platform.md` |
| Translate-once design, earlier problems list | `docs/dev/kernel-architecture.md` |
| Capability roadmap (heavy mods) | `docs/dev/heavy-mod-roadmap.md` |
| Small fixes noticed along the way | `docs/dev/small-fixes.md` |
| Working checklist | `to-do.md` |
| Player-facing state | `README.md`, `docs/playtest.md` |
