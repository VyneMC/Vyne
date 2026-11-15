# Vyne Native Mod Format — Design Note (Roadmap)

Status: **design only, not implemented**. This note records the decision and the
phased path so the current emulation work is structured toward it.

---

## Why

Vyne's universal loading today works by **runtime emulation**: (Neo)Forge mod
bytecode is remapped (Mojmap → Intermediary), shimmed (`net.neoforged.*` /
`net.minecraftforge.*` API stand-ins), and patched while the game is starting.
That approach has a structural cost:

- **Boot tax.** Remapping, frame recomputation and shim synthesis run at every
  launch. The remap cache (`.vyne/cache/remap`) reduces it to once per
  (mod version, mappings version), but the *first* boot of a big pack stays slow.
- **Fragility.** Emulation must track every (Neo)Forge API a mod touches. Any
  uncovered surface becomes a `NoClassDefFoundError` hunt.
- **No ownership of the platform.** Vyne currently ships a fork of Fabric
  Loader's Knot. Every Fabric Loader update is a rebase, and Vyne has no API of
  its own that mods can target directly.

A **native Vyne mod format** pays the conversion cost **once at install time**
instead of at every boot, and gives Vyne a first-class platform identity:
Vyne the modloader, not Fabric-with-extras and not (Neo)Forge-with-extras.

## What it is

1. **`vyne.mod.json` is the first-class metadata.** It already exists and is the
   highest-priority fingerprint in `ModDetector`. Everything else
   (`fabric.mod.json`, `META-INF/neoforge.mods.toml`, `META-INF/mods.toml`)
   remains supported as *emulation input*, not as the platform format.
2. **A native Vyne jar** contains:
   - `vyne.mod.json` (id, version, dependencies with version ranges, entrypoints,
     mixin configs, access wideners, environment).
   - Bytecode already in **Intermediary** (same namespace as the running game) —
     zero runtime remapping.
   - No (Neo)Forge API references; Vyne's own API (`vyne-api`) where needed.
3. **The converter** (future tool, `vyne convert <mod.jar>`): a build-time /
   install-time pipeline that takes a (Neo)Forge (or Fabric) jar and produces a
   native Vyne jar:
   - strip (Neo)Forge API references, rewrite them against `vyne-api` /
     the shim surface (the same rewrites the runtime `DynamicBridgeEngine`
     applies today, moved offline);
   - remap Mojmap → Intermediary **once** (reusing `MappingComposer` + `VyneRemapper`);
   - emit `vyne.mod.json` from the source metadata;
   - validate by loading/linking every class (`classLoadCheck`-style) before
     writing the output jar.
   Because the runtime already knows how to do all of these steps (see
   `DynamicEngineHost`'s whole-jar rewrite cache and `RemapCache`), the converter
   is largely the same code with a file output instead of a cache entry.
4. **Vyne as the platform, Fabric as a foot.** Vyne keeps booting on top of
   Fabric Loader's Knot for the foreseeable future (game discovery, mixin,
   deobfuscation pipeline all work), but mod-facing contracts become Vyne's:
   `vyne.mod.json`, `vyne-api`, Vyne's classloading semantics. Decoupling from
   Fabric core (own launcher, own game provider) is a later, separate effort and
   is explicitly *not* a prerequisite for the native format.

## Phased roadmap

| Phase | Deliverable | Depends on |
|-------|-------------|------------|
| N0 (this milestone) | Unified Knot classloading, remap cache, ModList exposure, `vyne.vcl.legacy` flag | done |
| N1 | `vyne.mod.json` accepted for *all* mod kinds (VYNE `LoaderType` treated as first-class everywhere; `VyneVirtualMetadata` grown to full mod metadata) | N0 |
| N2 | Converter v0: Fabric jar → native Vyne jar (metadata translation only, no code rewrite) | N1 |
| N3 | Converter v1: (Neo)Forge jar → native Vyne jar (offline remap + shim rewrite; runtime `RemapCache` bypassed entirely for converted jars) | N2 |
| N4 | `vyne-api` stable surface (events, registry, config) so native mods don't need loader shims at all | N3 |
| N5 | (Optional, far out) Vyne-owned launcher/game-provider; drop the Fabric fork | N4 |

## Non-goals / guardrails

- Emulation is **not** removed until the converter covers real mods. The two
  coexist indefinitely: unconverted (Neo)Forge jars keep using the runtime path.
- No bundling of Mojang mappings (licensing); the converter downloads them the
  same way the runtime does.
- The golden-mod smoke suite (`gradle/smoke.gradle`) is the acceptance gate for
  every phase.
