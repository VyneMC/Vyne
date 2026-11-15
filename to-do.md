# To-do

Working checklist for [plan.md](plan.md). Updated as work happens. `[x]` done, `[~]` in progress, `[ ]` not started.

## Done: Phase 1, one class loader for everything that touches the game

- [x] Map which Vyne classes reference game types and which the launcher side reaches (8 crossing points found, 3 of them misclassified helpers)
- [x] Launcher-side package set defined in `dev.stolym.vyne.core.LoaderSides`
- [x] Every other `dev.stolym.vyne` class loads through Knot (`KnotClassDelegate`)
- [x] Crossings cut: `GameLoader` (game class loader), `ModActivity` (inactive/failed mods), `ModIndex` (mod versions for `ModList`), `MappingRegistry.onMappingsChanged` listeners, registry-setup call removed from `Knot.init`
- [x] Only the game-side part of the Vyne jar goes on Knot (`VyneGameJar`), mounted in `Knot.init` after NeoForge's API jar; bundled libraries (Mixin, ASM, Gson, ...) stay with the launcher
- [x] `LoaderAudit` warns when a game-side class falls back to the launcher loader; static analysis: 0 crossings
- [x] All 5 server smoke scenarios reach `Done`, 0 fallbacks; client reaches the title screen with the test mods; `VyneModLifecycle` now runs in `KnotClassLoader`
- [ ] Move the crossing analysis from the scratch tool into the repo as a check (with the Phase 7 tooling)

## Now: Phase 2, run on NeoForge's patched game

Server: **done for the test mods** (2026-11-15). A dedicated server running NeoForge 21.1.265's own patched game reaches `Done` with Mouse Tweaks, CraftPresence and UniLib, 0 ERROR lines, NeoForge's own lifecycle constructing every mod through Vyne's core. Still behind dev properties.

- [x] Produce NeoForge 21.1.265's patched server with NeoForge's installer tools
- [x] `GameOverlay`: serve patched classes ahead of vanilla; Fabric's entrypoint patcher reads the patched `Main` from it
- [x] Patched mode set up before Fabric patches the entrypoint (`ConvertedModMount.preparePatchedGame`)
- [x] One exact rename run (tiny-remapper, full hierarchy, mixin extension) for the patched game, NeoForge's API jar and the NeoForge mod jars together (`PatchedGameBuilder` + `ExactRemap`), cached as one unit
- [x] Dedicated server keeps only the client classes server code uses (3 for NeoForge 21.1.265)
- [x] Real FML jar, real event bus jar and FML's libraries (SecureJarHandler, JLine, terminal appender) mounted ahead of Vyne's game-side jar
- [x] Patched mode switches off Vyne's compatibility lifecycle and its NeoForge-imitating mixins (only Vyne's own feature mixins apply)
- [x] Gradle source set `fml` compiled against real FML 4.0.45 and bus 8.0.5
- [x] Routing table `vyne/routing/neoforge-21.1.txt` (7 classes) applied to the patched game, NeoForge's API jar, the FML jar and the mods
- [x] Core FML layer in `dev.stolym.vyne.fml`: `VyneModList`, `VyneLoadingModList`, `VyneFMLLoader`, `VyneModLoader`, `VyneModInfo`, `VyneModFileInfo`, `VyneModFile`, `VyneSecureJar`, `VyneModContainer` (real event bus), `ModScan`, `VyneMods`
- [x] NeoForge itself (`neoforge`) and Minecraft are mods with containers; NeoForge's own data pack and assets kept
- [x] `@EventBusSubscriber` methods registered per method on the right bus, as real FML does
- [x] FML paths and `fml.toml` set up by the core
- [x] Fixes found on the way that also help the normal path: lambda method names now renamed with the interface they implement; night-config upgraded to 3.8.3; real Maven Artifact library bundled instead of two hand-written stand-ins; smoke servers use a free port
- [ ] Client build in patched mode (the `send(Packet)` naming conflict means the client needs either Mojang names at runtime or a targeted fix)
- [ ] Once patched mode runs the client and is the default: delete the legacy NeoForge path (imitation classes in `net/neoforged` and `net/minecraftforge`, `engine/dynamic` and `vyne-bridges.json`, registry and event bridging, `vyne.accesswidener`, the NeoForge mixins). Dead classes already removed on 2026-11-15 (35 files).
- [ ] Replace the dev properties (`vyne.neoforge.patchedGame`, `patchedClasspath`, `fmlJar`, `busJar`, `libraries`) with a real source: installer run on first start, later prebuilt packages
- [ ] Register NeoForge's own mixin config (`neoforge.mixins.json`)
- [ ] Patched-mode smoke scenario in Gradle (today: scratch script `run-patched.sh`)
- [ ] Content mods on patched mode (Comforts, Farmer's Delight, Waystones, Create)
- [ ] Move the analysis tools (crossing check, link check, FML surface) from scratch scripts into the repo

## Later

- [ ] Phase 3: Mojang-named compile jar; port Vyne's code to Mojang names; delete stand-ins and unneeded mixins; switch runtime names
- [ ] Phase 4: bundle Fabric API; block-break hook end to end; Fabric API events fired from NeoForge hooks
- [ ] Phase 5: mixin duplicate/collision detection; content-mod smoke scenarios
- [ ] Phase 6: Guard redesign (points 1 to 5 in the design doc)
- [ ] Phase 7: routing table, version adapters, signature-diff CI job, analysis tool into the repo
- [ ] Phase 8: prebuilt package pipeline and download/verify in Vyne
- [ ] Phase 9: benchmark table, CI, release workflow, license check, publish

## Chores (after Vyne works)

See [docs/dev/small-fixes.md](docs/dev/small-fixes.md) for the full list. Highlights:

- [ ] Log noise: duplicated Vyne lines, `[FieldRemap]` at INFO, `[VynePacks]` ERROR when Fabric API is absent
- [ ] Gate warning about `class_2385` / `class_2378` missing from the metadata index on every server boot
- [ ] Skip mounting/converting the NeoForge API when no NeoForge mod is installed
- [ ] Smoke suite: second boot per scenario to prove the cache is reused; fail on ERROR-level registration failures
- [ ] Prune old `.vyne/cache/remap` and `.vyne/cache/realneo` buckets

## Done on 2026-11-14

- [x] Converter (translate once, cache, deferred classes), boot stall fix
- [x] Client: CraftPresence + UniLib + Mouse Tweaks initialise; access transformers; key mappings; config screen extension point
- [x] Mods screen and Options button; Vyne branding in the title screen and client brand
- [x] Early loading window (pixel style, memory bar, two progress bars, per-mod tiles, recent activity, theme file) and loading overlay animation
- [x] Removed the obsolete loading-bar mixins
- [x] Docs: README, playtest guide, third-party notices restored, unified-platform design, overlap measurement
