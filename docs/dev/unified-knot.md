# Vyne "Unified Knot" Architectural Plan

## Decision

**The unified Knot is the architecture.** NeoForge/Forge mods are mounted onto the primary `KnotClassLoader` (via `KnotClassLoader.addCodeSource()`) and translated at its entry point; they do **not** run in an isolated `RemappingClassLoader` in the final design. This is where `unified-knot.md`, the runtime logs ("Initializing Unified Knot Environment"), and the recent migration commits already point; the isolated-classloader design in `legacy-vcl-architecture.md` is outdated.

**Transitional state (updated after the 2026-10 migration):** the unified Knot is now the **default and only** classloading path. `VyneModLifecycle` mounts emulated mod jars *and* the Vyne shim jar on Knot; `VyneTransformationGate` runs inside `KnotClassDelegate` (tasks 2.2/2.3 implemented) and remaps Mojmap→Intermediary with frame recomputation, per-class cached on disk (`.vyne/cache/remap`). The VCL stack (`RemappingClassLoader`, `BridgeClassLoader`, sandbox loaders, `ModRouter`) is **deprecated and off by default**: the sandbox loader constructors refuse to build unless `-Dvyne.vcl.legacy=true` is set (regression-bisect escape hatch). Live code may still use `RemappingClassLoader`'s *static* bytecode utilities. The classes are deleted in a future milestone once the legacy flag is proven unnecessary.

## Goal
Establish a stable, single-namespace modding environment where (Neo)Forge and Fabric mods coexist within the primary `KnotClassLoader`, eliminating ClassLoader isolation bugs (`NoClassDefFoundError`, `VerifyError`).

## 1. Unified Namespace Model
*   **Single Source of Truth:** Abandon the `BridgeClassLoader` for mod execution.
*   **Knot Integration:** Use `KnotClassLoader.addCodeSource()` to inject (Neo)Forge mods and Vyne Shims directly into the game's primary classpath.
*   **Visibility:** All mods share a single room. Fabric mods can see Forge mods, and vice-versa, with 100% visibility.

## 2. Surgical Interception Pipeline (Ring 0 Gate)
All non-Fabric bytecode is intercepted and translated at the entry point of the `KnotClassLoader` before the game engine sees it.

### The Pipeline Flow:
1.  **Intercept:** Hook into `KnotClassDelegate.getPreMixinClassByteArray`.
2.  **Remap:** Use a "dumb" ASM `ClassRemapper` to translate Mojmap/Srg names to Intermediary.
3.  **Validate:** Ensure all constant pool references are resolved to the unified namespace.
4.  **Recompute Frames:** Use `ClassWriter(COMPUTE_FRAMES)` with a **Metadata-Aware Provider** to generate JVM-safe StackMapTables.
5.  **Handoff:** Pass the "Fabric-ified" bytecode to the standard Knot/Mixin pipeline.

## 3. Metadata-Aware Hierarchy Provider
To solve `VerifyError` and `ClassCircularityError`, we use a non-loading metadata index.
*   **Hierarchy Index:** A read-only map of `(Class -> SuperClass/Interfaces)` built once on startup by scanning all JARs.
*   **Non-Loading:** Hierarchy checks walk the index strings instead of calling `Class.forName()`, preventing recursive loader deadlocks.

## 4. State Management (The "Eternal Registry")
*   **Registry Authority:** Instead of fighting Fabric's `freeze()`, Vyne intercepts the loading of `MappedRegistry` at the bytecode level.
*   **Constraint Removal:** The `freeze()` method is hollowed out during class definition, ensuring registries remain mutable for Vyne's injection phase.

## 5. Entrypoint Orchestration
*   **Synthesized Metadata:** Vyne generates virtual `fabric.mod.json` data for (Neo)Forge mods.
*   **Native Boot:** Fabric's own entrypoint engine triggers the mods, which then hand off control to Vyne's FML lifecycle shims.
