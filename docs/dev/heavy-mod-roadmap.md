# Heavy-Mod Roadmap — from today to a native (Neo)Forge mod on Vyne

Target design: [kernel-architecture.md](kernel-architecture.md). Companion to [native-mod-format.md](../native-mod-format.md) (converter detail) and
[improvement-plan.md](improvement-plan.md) (repo/dev plan). This file is
the *capability* roadmap: what stands between the current state (small (Neo)Forge mods
boot via emulated shims) and the goal:

> A heavy (Neo)Forge) mod — Create as the canonical stress test — running as a
> **native Vyne mod**: converted once (bytecode in Intermediary, `vyne.mod.json`,
> no (Neo)Forge API references left), no runtime remap tax.

Two distinct finish lines:

| Bar | Meaning |
|-----|---------|
| **Emulated-heavy** | Create boots server-side through the runtime pipeline |
| **Native-heavy** (goal) | That mod is converted once at install/first boot and runs without emulation |

Each phase is gated by a test, not a feeling. Status markers: ✅ done, 🚧 in progress, ⬜ open.

---

## Phase 0 — Strategy gate: the real-jar swap ✅ (gate passed 2026-10-07)

**Result:** all three (Neo)Forge smoke scenarios (`neoforge-simple`,
`neoforge-with-lib` incl. CraftPresence+UniLib, and `mixed`) reach `Done` with the
real universal jar mounted (`-Psmoke.realNeo`). Evidence: real classes
(`DeferredRegister`, `RegisterEvent`, `RegistryBuilder`, `BaseMappedRegistry`, …)
loaded from the real jar, remapped Mojmap→Intermediary by the gate, cached under
`.vyne/cache/remap/<mappings>/<jar-sha>/`. Phase 0 is **complete**: 62 unreferenced duplicate shims deleted; the real-API
mount is **default-on** (opt out with `-Dvyne.realNeoforgeApi=false`); full
`check` + all 4 smoke scenarios green with the default path.

**Premise:** hand-writing every (Neo)Forge API class does not scale. Instead, load the
**real NeoForge universal jar** (already downloaded at runtime by
`NeoForgeReferenceDownloader`) through the same pipeline mods use: remap
Mojmap→Intermediary per class, apply targeted patches, put it on Knot. The full API
surface then comes for free, for every NeoForge version, because it *is* NeoForge.

The hand-written layer shrinks to bounded **seams**:

1. **FML bootstrap seams** — `ModList`, `FMLEnvironment`, `FMLLoader`,
   `ModFileInfo` (~10 classes reporting "what is loaded, what dist am I"). Keep.
2. **Registry bridging** — NeoForge's `VanillaRegistry` system vs Vyne's injection
   into vanilla `MappedRegistry`. The one genuinely custom layer (`VyneRegistryInjector`,
   freeze hollowing). Keep, grow.
3. **Event dispatch** — real NeoForge event classes load as-is; custom code is only
   firing them from Fabric-side lifecycle. Keep, grow.

- **Gate:** `smokeServer_neoforge-simple` green with the real jar, or a precise
  written list of what broke.
- **Deliverable (done):** 62 shims deleted (duplicated by the real jar, zero
  references from Vyne source). **Backlog for Phase 1:** 32 overlapping shims remain
  because Vyne's bridging code is compiled against their signatures
  (`DeferredRegister`, `RegisterEvent`, `NeoForgeRegistries`, `FluidStack`,
  capability/item handler types, client event types) — migrating that bridging code
  to the real API is the next cleanup wave.
- **Risk:** class-identity collisions between real classes and existing shims
  (e.g. `VyneEventBus` vs real `IEventBus` impls, shim `DeferredRegister` vs real one).
  Control knob: Knot-first/parent-first delegation rules in `KnotClassDelegate`.
- **Flag:** `-Dvyne.experimental.realNeoforgeApi=true` (smoke: `-Psmoke.realNeo`) so
  the old path stays runnable. Implementation: `dev.stolym.vyne.loaders.RealNeoForgeApiMount`
  — derives a classloading-only jar (`net/` + `META-INF/services/`) from the universal
  jar, caches it under `.vyne/cache/realneo/<sha256>/`, and registers it on Knot BEFORE
  the Vyne jar (first classpath match wins, so real classes shadow duplicated shims).
  The universal jar contains no `net/minecraft` classes and no FML/bus packages, so
  there is zero overlap with the kept seams.

## Phase 1 — Foundation hardening ⬜

Four capabilities every mid-size mod needs, each proven by a fixture in the smoke
scenarios:

1. **Mixin pipeline** — 🚧 **core path proven (2026-10-07)**: the `mixin-fixture`
   smoke scenario packages a real NeoForge-style fixture mod
   (`src/mixinFixture/`, built by the `mixinFixtureJar` task) whose mixin targets
   `MinecraftServer.runServer` in the Mojmap namespace; the scenario asserts the
   injection actually fires. Two pipeline bugs were found and fixed:
   (a) `RefmapGenerator` remapped injection selectors with the *handler's*
   descriptor (CallbackInfo), which never matched the target — now falls back to a
   name-only lookup; (b) `ModDetector` passed parsed mixin configs into the
   **authors** constructor parameter, so every (Neo)Forge mod's mixins were
   silently dropped since day one.
   Remaining: fixture expansion for cross-mod targets and client-only mixins
   (needs the dist machinery).
2. **Dist machinery** — real `Dist`/`DistExecutor`/`@OnlyIn` handling; client classes
   never load server-side and vice versa.
3. **Config system** — working `ModConfigSpec`/`IConfigSpec` backed by real TOML
   files (currently a stub; blocks a large fraction of mods at startup).
4. **Event coverage** — grow dispatchers from tick/gameplay to the top-20 events
   real mods subscribe to, driven by matrix failures.

**Gate:** fixture scenario green + `neoforge-with-lib` still green.

## Phase 2 — Library-mods tier ⬜

Grow the golden matrix with library-type NeoForge mods (UniLib-class: small surface,
high leverage, many dependents). Publish `docs/compatibility.md`
(Works/Partial/Broken × mod × Vyne version). Add **chaos scenarios** for the Guard:
deliberately broken mod → frozen, server still reaches `Done` (Tier 1/2/3 behavior
becomes a test, not an assumption).

**Gate:** 5–10 library mods green in smoke.

## Phase 3 — Heavy mod, server-side ⬜ (first big prize)

Put Create in its own smoke scenario (`smoke/scenarios/create-server/mods/`) and let
the log write the work list: every `NoClassDefFoundError` / `NoSuchMethodError` is one
task. Expected clusters: registry edge cases, networking volume, event gaps,
bytecode oddities (per-mod rewrite patches get absorbed or deleted here).

**Gate:** `smokeServer_create` reaches `Done (`.
**Effort:** the long tail — weeks, but measured weeks with a failing test per item.

## Phase 4 — Client-side heavy ⬜ (second big prize)

Client rendering is its own world: screens, `DistExecutor` paths, and above all
**Flywheel** (Create's injected render pipeline). Headless client boot-to-title smoke
tests need to exist before this phase can be measured.

**Gate:** client boots to title with the mod enabled; in-world rendering verified manually.
**Risk:** highest in the plan — the tier nobody has fully solved outside NeoForge itself.

## Phase 5 — Converter: emulation → native ⬜

Only after the runtime pipeline is trustworthy (a converter cannot be more correct
than the pipeline it reuses). Two steps (see native-mod-format.md N2→N4):

1. **`vyne convert` v0** — run the existing remap/rewrite pipeline **offline**, emit a
   real jar (`vyne.mod.json`, Intermediary bytecode), validate by class-loading every
   converted class (reuse `classLoadCheck`). "Convert at first boot, cache, skip the
   work afterwards" = the remap cache generalized into a product.
2. **Native surface** — converted mods stop consuming (Neo)Forge shims at runtime;
   `vyne-api` becomes the API they link against.

**Gate:** the converted Create jar passes the same smoke scenario as the emulated one.

---

## Continuous (spans all phases)

- CI on every push: `check` (unit tests + `classLoadCheck` + jar hygiene) + smoke
  scenarios; jar artifacts uploaded.
- `LoaderAudit` (`-Dvyne.debug.loaderAudit=true`) diffing on phase boundaries to prove
  nothing silently changed classloader.
- **No new shim without a failing golden mod that needs it.**

## What Create is NOT expected to need

A reminder of scope: Create stresses mixins, Registrate, capabilities, networking,
dist handling, and Flywheel (client). Server-side is the realistic first milestone;
client-side rendering is Phase 4 frontier work.
