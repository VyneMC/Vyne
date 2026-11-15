# Vyne Project Restructure Proposal

The current project structure has several root-level folders that overlap or are disorganized (e.g., `com` and `Vyne Api` at the root, pre-compiled classes in subfolders, etc.).

Below is a proposed restructure into 6 major pillars to make the codebase professional and easy to navigate.

---

## 1. `core/` (The Engine)
Contains the heart of the modloader and compatibility logic.
- `launcher/`: Bootstrapping and early-init logic.
- `vcl/`: Vyne Compatibility Layer (The bridge between loaders).
- `registry/`: Injection, synchronization, and health management.
- `classloading/`: Custom classloaders and remapping logic.

## 2. `api/` (Developer Interface)
Public-facing code that modders use to interact with Vyne.
- `events/`: Cross-platform event bus and events.
- `registry/`: Public registry helpers.
- `plugin/`: Entry points for Vyne plugins.

## 3. `platforms/` (Loader Implementations)
Code specific to bridging other modloaders.
- `fabric/`: Lifecycle hooks and Fabric-specific bridges.
- `neoforge/`: NeoForge event mapping and compatibility.
- `forge/`: Legacy Forge support.

## 4. `compat/` (Third-Party Bridges)
Special handling for libraries and complex mods.
- `mixinextras/`: Integration with LlamaLad7's MixinExtras.
- `patches/`: Dynamic fixes for "problematic" mods (like the Create mod fix we just did).

## 5. `runtime/` (Execution Environment)
Everything related to running the game and modloader output.
- `run/`: Minecraft execution directory.
- `logs/`: Application and game logs.
- `config/`: Vyne-specific configurations.
- `cache/`: Remapping and discovery caches.

## 6. `tools/` (Maintenance & Metadata)
The "boring but necessary" stuff.
- `scripts/`: Python/Bash scripts for building/cleaning.
- `docs/`: Technical documentation and tutorials.
- `licenses/`: Legal information for bundled libraries.
- `gradle/`: Build system wrappers and configurations.

---

## Suggested Root Cleanup
| Current Path | Proposed Path |
| :--- | :--- |
| `Vyne Api/` | `api/` |
| `src/main/java/dev/stolym/vyne/vcl/` | `core/vcl/` |
| `fabric-loader/` | `platforms/fabric/loader/` |
| `com/llamalad7/mixinextras/` | `compat/mixinextras/` |
| `scripts/` | `tools/scripts/` |
| `run/` | `runtime/run/` |

> [!NOTE]
> This structure assumes a Gradle multi-project setup, where each top-level folder can be its own module if desired.
