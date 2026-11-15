# Guides

> **Alpha notice.** Commands, tasks, and file locations below follow the `0.1.0-alpha` layout.
> They'll stay source-stable across `0.1.x`, but we reserve the right to move things around as
> Vyne approaches its first stable release.

Task-oriented walkthroughs. Each section is self-contained — read the one that matches what
you're trying to accomplish.

## Writing a Vyne-aware mod

A plain Fabric mod doesn't need the API. Reach for `vyne-api` when you want to:

- Branch behaviour on whether Vyne is present.
- Hook into the modloader-ready lifecycle cross-loader.
- Ship optional Vyne-only features from the same source file as your vanilla Fabric codebase.

Minimum viable integration:

```java
package com.example.mymod;

import com.stolym.vyne.api.*;
import com.stolym.vyne.api.events.VyneLifecycleEvents;
import net.fabricmc.api.ModInitializer;

public class MyMod implements ModInitializer {
    @Override
    public void onInitialize() {
        if (VyneApi.isPresent()) {
            VyneLifecycleEvents.onModloaderReady(this::wireVyneFeatures);
        } else {
            wireVanillaFeatures();
        }
    }

    private void wireVyneFeatures() { /* ... */ }
    private void wireVanillaFeatures() { /* ... */ }
}
```

That's the whole shape of the integration. Everything else (registries, events, rendering)
stays on the loader you're already targeting.

## Writing a compat shim

When a NeoForge mod calls a method Vyne doesn't emulate yet, the user sees a `NoSuchMethodError`
and Vyne's containment layer absorbs it. You can fix it yourself by shipping a compat shim —
and optionally upstreaming it.

The pattern Vyne itself uses is:

1. **Identify the missing method** by grepping `latest.log` for `NoSuchMethodError`.
2. **Decide the shape**: is it a method on a class mods extend (add to the shim class), a
   method on a vanilla Minecraft class (add a static bridge + bytecode rewrite), or a method
   on an interface (add to the interface, all its default)?
3. **Add the shim** under `src/main/java/net/neoforged/...` or `src/main/java/net/minecraftforge/...`.
4. **If the method signature uses Mojang-mapped types** that need to survive into intermediary,
   add a compile-only stub under `src/main/java/net/minecraft/class_XXX.java` and exclude it
   from the shaded jar in `build.gradle` (the game jar supplies the real class at runtime).

See [`ComponentBridge`](../src/main/java/dev/stolym/vyne/compat/ComponentBridge.java) for a
worked example of the "static bridge + bytecode rewrite" variant, and
[`FluidStack`](../src/main/java/net/neoforged/neoforge/fluids/FluidStack.java) for the
"add a method to an existing shim" variant.

## Debugging a failing `FMLCommonSetupEvent`

Symptom: you see `[ClientDiag] No ... entries in any scanned Registries field` in the log, or
items from a NeoForge mod don't render, or `FMLCommonSetupEvent` aborts with an exception.

Walkthrough:

1. Grep `latest.log` for `Dispatching FML lifecycle events...`. That's the marker Vyne logs
   immediately before it starts the event post loop.
2. The next few lines are the mod's own log output from its `FMLCommonSetupEvent` handler. If
   they stop partway through — especially with a `NoSuchMethodError` or `NoClassDefFoundError` —
   you've found the method Vyne is missing.
3. Decide whether this is a shim miss (add a method to a Vyne shim) or a real Minecraft API
   gap (Vyne needs a bytecode-level bridge). The Vyne team can help in GitHub issues; include
   the exception's full type + descriptor.

## Shipping a NeoForge-in-Fabric mod pack

Steps to ship a pack that runs a NeoForge-style mod list on Vyne:

1. Install Vyne into the pack's Prism instance (see [Getting Started](getting-started.md)).
2. Drop NeoForge mod jars straight into `mods/`. No subfolder, no rename.
3. Also install **Fabric API** — several Vyne bridges rely on it (client rendering, networking).
4. Boot once in the pack launcher, confirm the expected `[ClientExtensions] wired: N installed`
   line is present in the log, and that `/vyne status` reports no failed mods.
5. Ship the instance export. Players get the same mod list on any launcher that can apply it.

## Running the dedicated server

```bash
./gradlew runServer
```

The `runServer` task copies the shaded loader jar into `run/server.jar` and boots it with the
standard server launch arguments. The server accepts the same mod folder layout as the client —
drop NeoForge or Fabric server-side mods into `run/mods/`.

For a production server, produce the shaded jar yourself:

```bash
./gradlew shadowJar
cp build/libs/vyne-loader-<version>.jar /opt/minecraft/server.jar
```

Then run it in your usual systemd / tmux / pterodactyl setup. Server-side Vyne has no GUI
dependency and needs no extra arguments beyond `nogui`.

## Creating a Vyne-aware Gradle plugin (project generator)

Vyne's `vyne-api-plugin` is published at `com.stolym.vyne:vyne-api-plugin:<version>` and
aliased under the plugin id `com.stolym.vyne.api`. You can publish a derivative plugin
(e.g. `com.example.mymod.vyne-bundle`) that applies `com.stolym.vyne.api` and then layers
your own defaults:

```groovy
// in your plugin's apply(Project):
project.plugins.apply('com.stolym.vyne.api')
project.getExtensions().getByName('vyne').apiVersion.set('0.1.0-alpha')
project.dependencies.add('implementation', 'com.example:my-library:1.0')
```

That's all — consumers of your plugin get Vyne ready, your library included, and your own
conventions applied in one shot.
