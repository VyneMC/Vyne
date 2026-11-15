# Vyne Modloader Documentation

> **Vyne is currently in active development and in `0.1.0-alpha`.** The runtime bridges, the
> `vyne-api` surface, and even parts of the loader lifecycle may change between patch releases —
> and bugs, sharp edges, and partial emulation are expected until the first stable release. If
> you hit something confusing, weird, or plain broken, please open an issue with the Vyne log and
> a reproduction snippet. That feedback is genuinely how these docs get fleshed out.

Vyne is a **universal Minecraft modloader** that runs on top of Fabric and transparently loads
NeoForge and (legacy) Forge mods alongside vanilla Fabric mods. Instead of forcing users to pick
a single loader per instance, Vyne bridges the three ecosystems inside one JVM — registries,
event buses, client extensions, mixins, and the full FML lifecycle are all emulated from a
single host.

## Start here

The docs are organised into four tracks. Follow them in this order on a first read:

1. **[Getting Started](getting-started.md)** — install Vyne, launch a world, verify a mixed
   Fabric/NeoForge pack works. ~10 minutes.
2. **[Loaders](loaders.md)** — how each loader is supported, what's emulated at "full" tier,
   what still falls back. Read before you file bugs about a specific loader.
3. **[API Reference](api-reference.md)** — the public `com.stolym.vyne:vyne-api` surface. Mod
   authors and pack maintainers use this to integrate with Vyne from their own code.
4. **[Guides](guides.md)** — task-oriented walkthroughs: shipping a Vyne-aware mod, writing a
   compat shim, debugging a failing `FMLCommonSetupEvent`, producing a NeoForge-in-Fabric pack.

## At a glance

| Topic | Current status (0.1.0-alpha) |
|---|---|
| Fabric mods | **Host**: run natively, never transformed |
| NeoForge 1.21.1 mods | **Small mods boot** on server and client. Content mods need NeoForge's game hooks, which aren't implemented yet |
| Legacy Forge (pre-1.20.2) | **Limited**: events and registries only |
| Mixins shipped by NeoForge mods | Core path works (covered by the `mixin-fixture` smoke test) |
| Crash containment (the Guard) | Mod failures in ticks, events and rendering are contained and reported |
| `vyne-api` + Gradle plugin | **Not yet published**; the API described in the reference is planned for a later release |
| Dedicated server | Supported (same jar, `--server`) |

For the detailed, test-backed matrix see the [playtesting guide](playtest.md#4-what-currently-works--doesnt).

## How Vyne talks to you

Every Vyne-owned log line is prefixed with one of a small set of tags. When something goes
wrong, grep for the tag below that matches your issue:

- `FORGE-LOAD »` — mod-loader lifecycle steps (discovery → wiring → dispatch).
- `[RegDiag]` — registry-state snapshots after each event dispatch.
- `[Containment]` — a throw was caught inside Vyne's safety net; the game keeps running.
- `[VCLPopulator]` — static-field population for NeoForge-style constants.
- `[ClientDiag]` — post-lifecycle sanity check of the client-side registries.
- `[ClientExtensions]` — 3D-item (`BEWLR`) registration summary.

## Support surface

- Issue tracker: [GitHub Issues](https://github.com/VyneMC/Vyne/issues/new/choose). Use the *Mod incompatibility* form for a specific mod.
- Log format: always include `latest.log` starting from the first `FORGE-LOAD »` line — everything
  before that is the vanilla Fabric boot which Vyne can't influence.
- Reproduction bar: "new world + steps to reproduce" for gameplay issues, "mod list + Prism
  instance export" for load-time issues.
