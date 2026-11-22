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

## Done: Phase 2, temporary patched mode (2026-11-15)

NeoForge-only packs run on NeoForge 21.1.265's own patched game behind Vyne's loader, server and client, by default, with no developer flags. Temporary: it is replaced by Vyne's own hook layer (plan, Phase 5) and then deleted.

- [x] `NeoForgeInstall`: Vyne downloads NeoForge's installer, runs it on first start (server ~65 s, client ~34 s), keeps the 8 jars it needs under `.vyne/neoforge/<version>/<side>/` (20 MB) and deletes the rest
- [x] One exact rename pass for the patched game, NeoForge's API jar and the NeoForge mods (`PatchedGameBuilder`, `ExactRemap`), cached; `SharedMethodSplit` handles NeoForge methods that Minecraft names two ways (`ICommonPacketListener.send`)
- [x] FML routing points answered by `dev.stolym.vyne.fml` (8 classes, incl. `VyneImmediateWindowHandler` for the client window hand-off)
- [x] NeoForge's lifecycle constructs the mods; NeoForge's data pack and assets kept; `@EventBusSubscriber` per method
- [x] Default for packs with NeoForge mods (since 2026-11-21 also with Fabric mods, see Phase 6)
- [x] All five smoke scenarios pass (`neoforge-simple`, `neoforge-with-lib` on patched mode; `mixed`, `fabric-only`, `mixin-fixture` as before); client reaches NeoForge's title screen with the three test mods
- [x] Title screen corner shows a Vyne line (version and Fabric mod count) under Minecraft's, above NeoForge's; early-screen mod tiles advance as NeoForge constructs each mod

## Done: Phase 3, hook inventory (2026-11-15)

- [x] `./gradlew hookInventory` (source in `src/tools/java`, not shipped), docs in `docs/dev/hook-inventory.md`
- [x] Per vanilla class and method: what NeoForge changes (`method~`, `method+`, `method-`, `field+`, `implements`, `class+`) with the NeoForge references added and the vanilla references removed
- [x] Recompilation noise filtered (compare behaviour, not bytecode layout: 2466 methods only recompiled); renumbered lambdas paired
- [x] Every Fabric API injection into Minecraft with injector kind, `@At` point, module and mixin
- [x] Output in `data/hooks/` for NeoForge 21.1.265 and Fabric API 0.116.17: NeoForge changes 938 classes (1914 methods changed, 1240 added); 211 methods changed by both (earlier name-only estimate: 209)
- [ ] Pair renumbered anonymous classes (`Util$7` / `Util$8`); today they show up as removed in one class and added in another

## Next

- [x] Phase 4: `./gradlew minecraftMojangJar` (vanilla client with Mojang names) and the `minecraftMojang` compile classpath with Minecraft's libraries; a probe using entities, blocks, codecs, Brigadier and the dedicated server compiles (2026-11-15)
- [~] Phase 5: Vyne's patch layer and NeoForge API (design: `docs/dev/hook-layer.md`)
  - [x] Hook surface (structure NeoForge adds to vanilla) as Vyne data: `surface.tsv` + `accesstransformer.cfg`, applied by `HookSurface`; `./gradlew minecraftHookedJar`
  - [x] Vyne's copy of NeoForge 21.1.265's API source in `src/neoforge/` (LGPL headers kept, credited); compiles with Gradle against vanilla + the hook surface (1495 classes)
  - [x] Hooks mode (`-Dvyne.hooks=true`, NeoForge-only packs): vanilla with Mojang names (`VanillaGame`, client or server) + the hook surface (`HookedGame`, 591 server classes) renamed in the same exact pass as Vyne's NeoForge copy and the mods, served like patched mode
  - [x] Vyne's NeoForge copy shipped inside the Vyne jar (`META-INF/vyne/neoforge.jar`) and used instead of NeoForge's jar
  - [x] `hooks` source set: Vyne's behaviour hooks as mixins with Mojang names, renamed at build time (`remapHooksJar`), applied only in hooks mode (`vyne.hooks.mixins.json`)
  - [x] First hook: server `Main` starts NeoForge's lifecycle (`MainHooks`); NeoForge's mod loading runs on vanilla + Vyne hooks
  - [x] Generated hooks: 318 mixins, 682 method bodies, 133 field initialisers from NeoForge's patched classes (`generateHookBodies`, Vineflower); all compile
  - [x] In hooks mode NeoForge's lifecycle constructs every test mod (minecraft, neoforge, CraftPresence, UniLib, Mouse Tweaks) on vanilla + Vyne's hooks
  - [x] Hooks mode server milestone (2026-11-15): NeoForge-only test server reaches `Done` with 0 ERROR lines on vanilla Minecraft + Vyne's hooks + Vyne's NeoForge copy; hand-written hooks so far: `MainHooks`, `DyeColorTagHooks`, `CreativeModeTabHooks` (+ `BuildCreativeModeTabContentsEvent`), `CreativeModeTabBuilderHooks`, `RegistryDataLoaderHooks`
  - [x] Binary patch layer (2026-11-15): `./gradlew hookPatches` diffs vanilla against NeoForge's patched classes (client + server), shipped as `META-INF/vyne/patches/`; `HookPatches` applies them on first start (1613 server classes); `NeoForgeLibraries` downloads the 6 libraries NeoForge needs; replaces the generated and hand-written hook mixins
  - [x] Hooks mode with the binary patches: `neoforge-simple` and `neoforge-with-lib` reach `Done` with 0 ERROR lines, no NeoForge installer (`./gradlew smokeServer_<scenario> -Psmoke.hooks`)
  - [x] Measured: a mixed pack on the binary patches fails at the first Fabric API mixin on a NeoForge-changed method (`fabric-entity-events-v1` `LivingEntityMixin.onIsSleepingInBed`, target `LivingEntity.method_18405`)
  - [x] Hooks mode on the client (2026-11-15): Prism instance with Mouse Tweaks, CraftPresence and UniLib (NeoForge) reaches the title screen, patches applied to 1613 client classes, no NeoForge installer
  - [x] Hooks mode is the default for NeoForge-only packs (2026-11-16); `-Dvyne.hooks=false`, or missing patches, fall back to NeoForge's installer
  - [ ] Delete the installer fallback (runtime `NeoForgeInstall`) once hooks mode has run in players' packs
  - [x] NeoForge reports 21.1.265 (version in Vyne's NeoForge jar manifest and registered by Vyne's FML)
  - [x] `docs/dev/hook-layer.md` describes the binary patch layer
  - [x] `FMLModContainer` routed to `VyneModContainer`: mods that check their container type (Comforts) load in NeoForge-only packs
- [~] Phase 6: Vyne's copy of Fabric API on the patch layer; at the 211 shared methods, Fabric API's event calls added into the patched methods when Vyne builds its patches (instead of its mixins); block-break hook end to end; mixed scenario passes; delete patched mode and the legacy NeoForge path (imitation classes in `net/neoforged` and `net/minecraftforge`, `engine/dynamic` and `vyne-bridges.json`, registry and event bridging, `vyne.accesswidener`, ~26 imitation mixins, `BridgeClassLoader`)
  - [x] `./gradlew fabricApiOnPatches`: every Fabric API injection checked against NeoForge's patched classes (`data/hooks/fabric-api-on-neoforge-21.1.265.tsv`): 404 apply, 57 break (7 target method gone, 46 injection point gone, 4 point moved), 15 not verifiable; the measured gap is 57 injections in 19 modules, not 211 methods
  - [x] Patch-layer handler table (`vyne/patch-layer/fabric-api-handlers.txt`, regenerated by the tool): broken Fabric API handlers, or whole mixins (`*`), are switched off on the patch layer and logged as replaced or not available yet; every other Fabric API injection becomes optional there, so a missed case cannot stop the game
  - [x] Replaced: block breaking (`PlayerBlockBreakEvents` BEFORE/AFTER/CANCELED fired from `destroyBlock` and NeoForge's `removeBlock`); tag removal (NeoForge's own, Fabric's `TagFileMixin` and `TagGroupLoaderMixin` off); datapack registries (NeoForge's list plus Fabric's dynamic registries merged)
  - [x] Fixed: Mixin put Fabric's `Pack.parentsPredicate` default after a nested `new Pack` in NeoForge's constructor; Vyne sets the default at constructor return
  - [x] Mixed server on the patch layer (`-Dvyne.hooks.mixed=true`, Fabric API + Mouse Tweaks + CraftPresence + UniLib) reaches `Done` with 0 ERROR lines (2026-11-16)
  - [x] End-to-end test (`patch-layer` smoke scenario): a fake player breaks three blocks; Fabric and NeoForge listeners both run, either can cancel, Fabric's AFTER and CANCELED fire exactly when they would on Fabric
  - [x] Replaced: Fabric furnace fuels (`FuelRegistry`) answered through NeoForge's `FurnaceFuelBurnTimeEvent` for items NeoForge does not already treat as fuel; tested in `patch-layer` (dirt 1234 from Fabric, coal 1600 from NeoForge)
  - [x] Sleep and conversion events through NeoForge events (tested in `patch-layer`): `ALLOW_SLEEP_TIME`, `ALLOW_NEARBY_MONSTERS`, `ALLOW_BED` (`CanPlayerSleepEvent`, `CanContinueSleepingEvent`), `ALLOW_SETTING_SPAWN` (`PlayerSetSpawnEvent`, bed spawns only), mooshroom `MOB_CONVERSION` (`LivingConversionEvent.Post`)
  - Patch-layer events, in batches (status of every disabled Fabric API handler: `vyne/patch-layer/fabric-api-handlers.txt`):
    - [x] Batch 1: block breaking, furnace fuel, tag removal, datapack registries
    - [x] Batch 2: sleep checks, bed spawn, mooshroom conversion
    - [x] Batch 3, sleep without a NeoForge event (mixins at NeoForge's call sites, tested with a fake player and a sleeping villager): `MODIFY_SLEEPING_DIRECTION`, `SET_BED_OCCUPATION_STATE`, `MODIFY_WAKE_UP_POSITION`, `ALLOW_BED` direction check (5 handlers)
    - [x] Batch 4, items (`fabric-item-api-v1`, tested in `patch-layer`): durability (`CustomDamageHandler` through NeoForge's `damageItem`), recipe remainders (crafting, brewing, furnace, through `getCraftingRemainingItem`), enchanting checks (table, anvil, command, loot, through `supportsEnchantment` / `isPrimaryItemFor`), `EnchantmentEvents.MODIFY` (NeoForge moved the registry call into a lambda)
    - [x] Batch 5, recipes and content (tested in `patch-layer`): Fabric custom ingredients (`fabric:type`) in NeoForge's ingredient codecs, including the list codecs built from `MAP_CODEC_NONEMPTY`; network falls back to item lists as Fabric does for clients without the type; villager trades and chunk section air checks already done by NeoForge
    - [x] Batch 6, menus and networking (server side tested in `patch-layer`, client connection still to test): Fabric payload types in NeoForge's four custom payload codecs (round trip tested), extended screen handlers through NeoForge's `openMenu`, Fabric's close-current-screen check, known packs filter, vanilla language file, datagen tag `replace`
    - [x] Client-only handlers (targets checked against the patched client jar, not run yet): screen tick and render events, Indigo chunk building (non-vanilla Fabric models), block entity render data, fluid sprites and overlays, sound streams, model modifiers, block-breaking continuation; hanging signs and namespaced shaders already done by NeoForge
    - [x] Every Fabric mod's injectors are optional on the patched game (also `CAPTURE_FAILHARD` → `CAPTURE_FAILSOFT`); each hook that attaches nowhere is logged with its mod (`PatchLayerHandlers.reportUnapplied`); hooks covered elsewhere are listed in `vyne/patch-layer/covered-hooks.txt` (2026-11-21)
    - [x] Client fixes found by booting the client: colour providers read NeoForge's `blockColors`/`itemColors` (Fabric mixins rewritten at load), particle factories through a raw-id view of NeoForge's `providers`, registry-sync client mixins off, shader stage and import ids no longer prefixed twice, `UseEntityCallback` on the client via `EntityInteractSpecific`, `BLOCK_ENTITY_UNLOAD` in `getBlockEntity`, block dust tint via `areBreakingParticlesTinted`
    - [x] `smokeClient_<scenario>`: boots the client under Xvfb without the asset store, passes at the title screen (`mixed` passes); run one at a time, the client is heavy
    - [x] Architectury bridged, temporary until its spots are covered by the spot table (tested in `mixed-content`): `BlockEvent.BREAK`, `EntityEvent.ADD` (server), `LIVING_CHECK_SPAWN` via `MobSpawnEvent.PositionCheck`, screen render and char-typed events, client right-click-air
    - [ ] Remaining third-party hooks seen: REI `modifyBl` (left-side potion effects, could use `ScreenEvent.RenderInventoryMobEffects`), Continuity item frame model hooks
    - [x] `mixed-content` scenario with the test pack's server-safe mods plus the fixture (REI, Architectury, AppleSkin, Traveler's Backpack, Comforts)
    - [ ] Test on a real client: a mixed pack joining a server (instance `VyneMixedTest` in Prism)
  - [ ] Analysis: slices and same-name members added by both loaders (missed `WorldChunkMixin.onRemoveBlockEntity` and `TagFileMixin`), and injections that apply to code NeoForge no longer uses (missed `IngredientMixin.injectCodec`: NeoForge builds `Ingredient.CODEC` from `CraftingHelper`, not `codec(boolean)`)
  - [x] Replace every disabled Fabric API handler: 60 replaced, 0 disabled in the table
  - [x] Patch layer is the default for mixed packs, also on the NeoForge installer fallback (`-Dvyne.hooks=false`); `mixed` smoke scenario passes both ways (2026-11-21)
  - [x] Compatibility path deleted: hand-written NeoForge/Forge classes, VCL lifecycle, dynamic bridge engine, registry/event/fluid/capability/config/network bridges, runtime bytecode translation (`VyneRemapper`), 28 emulation mixins; NeoForge mods are remapped exactly once or the launch stops with an error
  - [x] Legacy Forge mods (`net.minecraftforge`) are skipped with a warning; they only ever ran on the hand-written classes
  - [x] Gaps the hand-written classes had been covering: NeoForge's `mergetool` API library (`Dist`, `OnlyIn`) is now downloaded; modlauncher's `TransformingThrowablePatternConverter` is provided by Vyne's FML (`src/fml`)
- [ ] Phase 6 proper (design: `docs/dev/unified-platform.md` 4.1 and 4.2)
  - [x] Retarget moved hooks at launch (`HookRetarget`): a Fabric mod's call-site hook whose call moved into a lambda or helper of the same class is pointed at its new place, only when the handler provably fits; it replaced the hand-written known-packs and vanilla-language bridges
  - [ ] Fabric API's calls inside Vyne's patches at the shared spots (one hook fires both APIs); its mixins and the hand-written bridges at those spots removed
    - [x] Typed hooks: `src/hooks` compiles in Mojang names against Fabric API remapped to Mojang names (`./gradlew fabricApiMojangJar`); a hook applies on the patched game when its Fabric module is loaded (`HooksMixinPlugin`)
    - [x] Block breaking ported (`FabricBlockBreakHook`); reflection bridge deleted; same results in `patch-layer`
    - [x] Port the other bridges (2026-11-22): fuel, sleep, items, enchantments, ingredients, payloads, menus, block entity unload (now tested), interaction, pack defaults, dynamic registries, datagen tags, screens, block-breaking continuation, sounds, fluids, Indigo, render data, models, particles, block dust; every server scenario and the `mixed-content` client pass
    - [ ] Colour rewrites still live in `PatchLayerHandlers` (they rewrite Fabric API's mixins rather than bridge a spot); move them once the hooks are baked
    - [ ] Client checks a person has to do: Indigo terrain, Fabric fluid sprites, Fabric sounds, block dust tint, model modifiers (the Xvfb boot has no assets and renders no world)
    - [ ] Bake the hooks into the binary patches at build time instead of applying them as mixins at launch
  - [ ] Spot table for spots NeoForge replaced with events, written against Vyne's hooks; Architectury bridge removed
- [ ] Phase 6b: Mojang names at runtime; port the remaining Vyne code; delete the Minecraft stand-ins
  - [x] `./gradlew minecraftRuntimeJar`: vanilla client jar with runtime (intermediary) names, built from Mojang's jar and Fabric's intermediary mappings (`src/minecraftJar`)
  - [x] `main` compiles against it; all `class_xxxx` and Mojang-named stand-ins deleted (2026-11-21); was blocked by the compatibility path
- [ ] Phase 7: mixin duplicate/collision detection; content-mod smoke scenarios
- [ ] Phase 8: Guard redesign (points 1 to 5 in the design doc)
- [ ] Phase 9: inventory diff per NeoForge / Fabric API release
- [ ] Phase 10: benchmark table, CI, release workflow
  - [x] Third-party notices: NeoForge source copy, runtime-downloaded NeoForge libraries and installer, modlauncher stand-in; deleted hand-written NeoForge/Forge classes removed from the list
  - [ ] Notices for Vyne's own copy of Fabric API, once it ships
- [ ] Move the crossing check and link check from scratch scripts into the repo

## Chores (after Vyne works)

See [docs/dev/small-fixes.md](docs/dev/small-fixes.md) for the full list. Highlights:

- [x] Log noise: Vyne lines mirrored to stdout only while the logger drops them, `[FieldRemap]` at DEBUG, `[VynePacks]` gone with the compatibility path, no more `BOOT → BOOT` warning
- [x] Gate warning about `class_2385` / `class_2378` missing from the metadata index (gone with the compatibility path)
- [x] Skip mounting/converting the NeoForge API when no NeoForge mod is installed (and Vyne's NeoForge event hooks stay off)
- [x] Smoke suite: `-Psmoke.secondBoot` boots each scenario twice and fails if the second boot redoes first-launch work (remap, patched game, API jar, downloads)
- [x] Smoke suite: server scenarios fail on any ERROR line (log4j or `[Vyne] ERROR`); the client test allows only the missing asset index, the window icon and CraftPresence's pack lookups
- [x] Prune old caches: `.vyne/cache/remap` removed at start, `realneo` keeps only the current API jar

## Done on 2026-11-15 (fixes from player logs)

- [x] Fabric-only packs no longer get NeoForge's access rules, and no pack gets every Minecraft member made public (`MinecraftFieldAccessWidener` deleted): Lithium's private `@Overwrite`s crashed on it; Fabric API + Lithium reach `Done` alone and in the mixed scenario
- [x] NeoForge mods' mixin configs registered with the Fabric mods' configs in `FabricMixinBootstrap`, not from `VCLMixinConfigPlugin.onLoad` during Mixin's config selection (`ConcurrentModificationException` with Comforts + Lithium)
- [x] `[VyneGate] Refusing to transform` lowered to debug (2434 lines in a 140-mod Fabric pack)
- [x] `classLoadCheck` skips the optional `commons-compress` codecs (xz, brotli, zstd, 7z, pack200) bundled for `jbsdiff`
- [x] `./gradlew build` on a fresh clone generates what it needs (no generated sources in git)
- [x] Comforts 9.0.5 in a mixed pack (with Fabric API and Lithium): blocks place, items exist, 0 ERROR lines. Fixes: payload and late registry events posted once per bus; `RegistryUnlock` around `RegisterEvent`; `NewRegistryEvent` and the server lifecycle events fired on the compatibility path (SpectreLib loads its configs on them); full `FMLConfig` stand-in; `ICancellableEvent`; `VyneRegistryInjector` uses 1.21.1's `register(key, value, RegistrationInfo)` and checks registration by value; removed a redirect that sent every `getConnectedDirection` call to `FaceAttachedHorizontalDirectionalBlock`; converter and remap caches bumped
- [x] Title screen: Mods button replaces the Realms button (and left the Options screen); skipped when Mod Menu is installed
- [x] Mods screen: Fabric mods' config screens through their `modmenu` entrypoint; Vyne's copy of Mod Menu's API interfaces is mounted only when Mod Menu is not installed (Capes)
- [x] Fabric Loader base updated from 0.19.1 to 0.20.1 (upstream changes ported, Mixin 0.17.5, MixinExtras 0.5.5, tiny-remapper 0.15.0, mapping-io 0.9.1, class-tweaker 0.3.0, ASM 9.10.1); mods requiring loader 0.19.5+ (Fabric Language Kotlin 1.14) load
- [x] Default NeoForge version 21.1.265 (was 21.1.233), matching the patch layer; an older version stored in `.vyne/vyne.properties` no longer pins it (JEI 19.70 needs 21.1.238+)
- [x] Jar-in-jar: bundled jars listed in `META-INF/jarjar/metadata.json` are found wherever they are stored (Skin Layers 3D keeps them in `META-INF/jars/`)
- [x] A mod that fails to construct ends the phase with a `ModLoadingException`, so NeoForge writes the crash report its error screen opens
- [x] Loading overlay mixin uses `@WrapOperation` so splash-screen mods (Puzzle) can change the same calls
- [x] Every push to `dev-1.21.1` builds, tests and publishes a GitHub pre-release (`.github/workflows/dev-build.yml`)
- [x] Early window: logs GLFW's error when `glfwInit` or `glfwCreateWindow` fails
- [x] Installer: Prism mods folder is `minecraft/mods` unless `.minecraft` exists; the command-line Prism install is a component install that disables Fabric Loader; notes no longer promise Forge mods

## Done on 2026-11-14

- [x] Converter (translate once, cache, deferred classes), boot stall fix
- [x] Client: CraftPresence + UniLib + Mouse Tweaks initialise; access transformers; key mappings; config screen extension point
- [x] Mods screen and Options button; Vyne branding in the title screen and client brand
- [x] Early loading window (pixel style, memory bar, two progress bars, per-mod tiles, recent activity, theme file) and loading overlay animation
- [x] Removed the obsolete loading-bar mixins
- [x] Docs: README, playtest guide, third-party notices restored, unified-platform design, overlap measurement
