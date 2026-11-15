# API Reference — `com.stolym.vyne:vyne-api`

> **Alpha notice.** The `0.1.0-alpha` API surface is intentionally small. Methods marked
> "experimental" may change; the surfaces documented below are the ones we commit to keeping
> compatible across the `0.1.x` line.

The Vyne API is a pure-Java library. It has zero transitive dependencies beyond
`org.jetbrains:annotations`, so it is safe to include from any loader (Fabric, Forge,
NeoForge, multi-loader projects). Every runtime call into the modloader goes through reflection,
so the API also degrades gracefully when Vyne is not present — a mod that adds the jar
unconditionally will simply see `VyneApi.isPresent() == false` on a non-Vyne launch.

## Installing the API

> **Not published yet.** `vyne-api` and its Gradle plugin aren't available on a public Maven
> repository yet, so the snippets below won't resolve until the first API release. This page
> documents the planned surface.

### With the Gradle plugin (recommended)

```gradle
plugins {
    id 'com.stolym.vyne.api' version '0.1.0-alpha'
}

// Optional: pin a non-default API version or disable the auto-dep.
vyne {
    apiVersion = '0.1.0-alpha'
    // addApiDependency = false
}
```

Applying the plugin:

1. Adds `mavenCentral()` + the Fabric / NeoForge / Mojang maven repos.
2. Adds `implementation 'com.stolym.vyne:vyne-api:<apiVersion>'` automatically.
3. Exposes the `vyne { ... }` DSL block for overrides.

### Manually

```gradle
repositories {
    mavenCentral()
    maven { url 'https://maven.neoforged.net/releases/' }
    maven { url 'https://maven.fabricmc.net/' }
}

dependencies {
    implementation 'com.stolym.vyne:vyne-api:0.1.0-alpha'
}
```

## `VyneApi`

The entry-point class. Every method is `static`.

### `boolean isPresent()`

Returns `true` if the Vyne modloader is active in the current JVM. Detect via class probe; never
throws.

```java
if (VyneApi.isPresent()) {
    // Vyne-specific code path
}
```

### `Optional<String> version()`

Returns the running Vyne version, or `Optional.empty()` if Vyne isn't present (or is too old to
expose a version field).

```java
String v = VyneApi.version().orElse("vanilla");
```

### `String versionOrUnavailable()`

Convenience for log concatenation — returns `"unavailable"` when `version()` is empty.

```java
LOGGER.info("Vyne: {}", VyneApi.versionOrUnavailable());
```

### `VyneLoaderTier loaderTier(VyneLoader loader)`

Returns the current emulation tier for a given loader:

```java
import com.stolym.vyne.api.*;

VyneLoaderTier tier = VyneApi.loaderTier(VyneLoader.NEOFORGE);
if (tier.ordinal() <= VyneLoaderTier.EMULATED_FULL.ordinal()) {
    // NeoForge-level features are safe to use.
}
```

## `VyneLoader`

The enum of supported loader flavours: `FABRIC`, `NEOFORGE`, `FORGE`.

## `VyneLoaderTier`

The ordered enum `HOST → EMULATED_FULL → EMULATED_PARTIAL → UNKNOWN`. Ordering matters — a
check of `tier.ordinal() <= EMULATED_FULL.ordinal()` correctly passes both host and full-tier
emulation.

## `VyneLifecycleEvents`

Cross-loader subscription hub. Two phases today:

### `onModloaderReady(Runnable)`

Fired once Vyne finishes dispatching every `FMLLoadCompleteEvent` for every mod bus. Safe to
call at any time — listeners registered after the event has already fired are invoked immediately
on the calling thread.

```java
VyneLifecycleEvents.onModloaderReady(() -> {
    LOGGER.info("All mods ready. Vyne {}.", VyneApi.versionOrUnavailable());
});
```

### `onClientReady(Runnable)`

Fired just before the title screen is first shown on a client. Useful for one-time resource
reloads.

## `@VyneMod` annotation

Marker on a mod's main class. Vyne's discovery pass picks it up, which means you can run a
prototype mod without a `fabric.mod.json` / `mods.toml` — ideal for the scaffolding templates
in the [Guides](guides.md#writing-a-vyne-aware-mod).

```java
@VyneMod(id = "example", requiresTier = "EMULATED_FULL")
public class ExampleMod {
    public ExampleMod() {
        // one-shot init — Vyne invokes the no-arg constructor
    }
}
```

Fields:

- `id()` — required; must match `[a-z0-9_]+`.
- `requiresTier()` — optional; defaults to `"EMULATED_PARTIAL"`. If the active tier is below
  this, Vyne will refuse to load the mod and log a clear diagnostic.

## Stability promises

- The public classes in `com.stolym.vyne.api.*` and `com.stolym.vyne.api.annotations.*` are
  source-stable across `0.1.x`.
- Anything in a package suffixed `.internal` or prefixed with `_` is not — don't touch it.
- Method-level `@ApiStatus.Experimental` tags will be added as we introduce experimental
  surfaces from `0.2.0` onwards.
