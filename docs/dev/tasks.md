# Vyne Implementation Tasks

## Phase 1: The Foundation (Metadata & Indexing)
- [X] **Class 1.1:** Implement `VyneMetadataIndex` - A high-speed, read-only index for class hierarchies.
- [X] **Class 1.2:** Implement `HierarchyScanner` - One-pass JAR scanner to populate the index on startup.
- [X] **Class 1.3:** Create `MetadataClassWriter` - A custom ASM `ClassWriter` that uses the index for `getCommonSuperClass` without loading classes.

## Phase 2: Knot Orchestration (The Namespace)
- [X] **Integration 2.1:** Hook into `VyneLauncher` to feed all detected (Neo)Forge mods into `KnotClassLoader.addCodeSource`.
- [X] **Integration 2.2:** Implement the `VyneTransformationGate` within `KnotClassDelegate`. (wired at both transform call sites; scope rule + remap cache in `VyneTransformationGate`)
- [X] **Integration 2.3:** Map the "Translation Cycle": Intercept -> Remap -> Recompute Frames -> Return. (`VyneTransformationGate.transform`; results cached on disk)

## Phase 3: Bytecode Authority (God Mode)
- [ ] **Authority 3.1:** Implement the `MappedRegistry` interceptor to disable `freeze()` logic.
- [ ] **Authority 3.2:** Implement `VyneShimProvider` - A system to inject synthesized Forge/NeoForge API classes into Knot.

## Phase 4: Lifecycle & Registry
- [ ] **Lifecycle 4.1:** Synchronize the (Neo)Forge `@Mod` entrypoints with Fabric's initialization phases.
- [ ] **Registry 4.2:** Re-wire `DeferredRegister` to feed directly into the eternally-mutable Fabric registries.

## Phase 5: Validation & Triage
- [ ] **Triage 5.1:** Implement the Layer 1 "Hypervisor" catch blocks around mod entrypoints.
- [ ] **Verification 5.2:** Test with heavy mods (e.g., Create, JEI) to confirm no `VerifyError` or `NoClassDefFoundError`.
