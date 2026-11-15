# Loaders

> **Alpha notice.** Vyne is `0.1.0-alpha`. The tier assignments below reflect what's actually
> shipping today, not a long-term roadmap commitment. Things move up and down the table between
> releases.

Vyne runs one JVM, one Minecraft process, one resource pipeline — but three mod ecosystems. This
page explains how each ecosystem is supported, what "tier" means in practice, and what you can
reasonably expect today.

## The three tiers

Every loader Vyne supports falls into one of these, exposed via
`com.stolym.vyne.api.VyneLoaderTier`:

| Tier                 | What it means in practice                                                |
|----------------------|--------------------------------------------------------------------------|
| `HOST`               | Native — Vyne IS this loader. No emulation; everything works.            |
| `EMULATED_FULL`      | Everything a typical mod touches works. Niche extension points may miss. |
| `EMULATED_PARTIAL`   | Core features work; advanced client/render hooks fall back to vanilla.   |
| `UNKNOWN`            | Loader not recognised or Vyne not present.                               |

## Fabric — `HOST`

Vyne is built on Fabric Loader. Everything a plain Fabric mod compiles against resolves against
the real class files with zero Vyne involvement. `ModInitializer`, `ClientModInitializer`,
Fabric API, Indigo, ETF/EMF, and mixin entry points all behave exactly as they do on a vanilla
Fabric install.

**Implication for mod authors:** you never need to depend on `vyne-api` to run on Fabric. The
API only becomes useful when you want to interop with the emulation layer (e.g. read the
current loader tier, subscribe to `VyneLifecycleEvents`, or gate a feature on Vyne being
present).

## NeoForge 1.21.x — `EMULATED_FULL`

This is where Vyne's heavy lifting lives. The emulation covers:

### Lifecycle events

Dispatched via `dev.stolym.vyne.vcl.VCLManager.dispatchFmlLifecycle` in this order, per
detected mod event bus:

1. `FMLCommonSetupEvent`
2. `FMLClientSetupEvent` (client only)
3. `RegisterClientExtensionsEvent` (client only)  — see
   [`dev.stolym.vyne.client.ClientExtensionsBridge`](../src/main/java/dev/stolym/vyne/client/ClientExtensionsBridge.java)
4. `FMLLoadCompleteEvent`

All events use NeoForge's real class names (Vyne ships shims that the mod classloader binds
against) so `@SubscribeEvent` handlers fire the way the mod author expects.

### Registries

`dev.stolym.vyne.registry.VyneGenericRegistry` exposes every registry mods typically mutate:
blocks, items, entities, block entities, fluids, fluid types, menu types, recipe types,
creative tabs, data components. A mod's `DeferredRegister` and `RegisterEvent` paths both
bottom out in this generic registry, which in turn synchronises content into the vanilla
`BuiltInRegistries` at the right moment.

### Client extensions

`IClientItemExtensions` registrations are collected via the `RegisterClientExtensionsEvent`
shim and forwarded to Fabric's `BuiltinItemRendererRegistry` — this is what makes Traveler's
Backpack, Supplementaries, and other mods with 3D items render their real models.

### Mixins

`com.llamalad7.mixinextras.*` is shaded. The bridge class loader remaps NeoForge-side mixin
descriptors into intermediary before handing them to Sponge Mixin, so `@WrapOperation`,
`@Inject(at = @At("INVOKE"))`, etc. all work identically to a real NeoForge environment.

### What's still partial

- **Capabilities (legacy `ICapabilityProvider`).** Not exposed. Mods that still use the old API
  typically fall back to an in-memory side channel that happens to work.
- **NeoForge events on the game event bus** (not the mod bus). Coverage is
  ~90% — some niche events are still missing.

## Legacy Forge (1.20.4 and older) — `EMULATED_PARTIAL`

Vyne accepts Forge jars mostly because the community asked. The core works:

- `@Mod`-annotated entry classes get discovered and instantiated.
- `RegistryEvent.Register<T>` fires for items, blocks, entities.
- Mod-bus lifecycle events (`FMLCommonSetupEvent`, `FMLLoadCompleteEvent`) are dispatched.

The following intentionally do not work and likely never will:

- Forge's service layer (`IForgeRegistry`, `CapabilityManager`, `IExtensibleEnum`). Too invasive
  to emulate without rewriting the registry core.
- Forge-specific rendering (`RenderTypeLookup`, `ForgeHooksClient`). Use NeoForge equivalents.
- `CoreModInitializer` / ASM core mods. Not supported — use Mixin.

## Container lookup — `dev.stolym.vyne.engine.detector`

On boot Vyne scans the Fabric `mods/` folder (plus anything the launcher passes via
`-Dfabric.addMods`). Each jar goes through this pipeline:

1. `ModDetector` reads the jar manifest + `META-INF/mods.toml` + `fabric.mod.json`, and
   tags the jar with one or more loader flavours.
2. `ModDiscoveryCache` persists the detection result to speed up subsequent boots.
3. `ModRouter` (in the engine package) decides which sandbox the mod runs under:
   `FabricSandbox`, `ForgeSandbox`, or `NeoForgeSandbox`. Each sandbox sets up a dedicated
   `RemappingClassLoader` tailored to the remap direction (mojmap → intermediary for NeoForge,
   SRG → intermediary for legacy Forge, no-op for Fabric).

The detection + router pair is what lets mixed mod jars (mods that ship both a Fabric and a
NeoForge entrypoint) be picked up once and dispatched correctly. See
[`ModRouter`](../src/main/java/dev/stolym/vyne/engine/router/ModRouter.java) for the decision
table.
