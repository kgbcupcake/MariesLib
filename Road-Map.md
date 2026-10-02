

>  Marie's-Lib is the shared foundation behind my mods. It provides reusable systems for data resolution, tracking, synchronization, configuration, compatibility, validation, diagnostics, commands, and dynamic UI.

> This road-map describes the major capabilities Marie's-Lib is being built toward. Individual bugs, refactors, and implementation tasks are tracked separately in GitHub issues.

---

## Dynamic UI Framework

> **Goal:** Provide a reusable in-game UI framework for building configurable interfaces without rebuilding the same infrastructure for every mod.

- [ ] Dynamic panels and layouts
    
- [ ] Move, resize, and scale UI components
    
- [ ]  Registry-driven UI regions and slots
    
- [ ]  Shared HUD editing infrastructure
    
- [ ]  Dynamic configuration screens
    
- [ ]  Color and appearance customization
    
- [ ] Reusable editor components
    
- [ ] Finalize the remaining Classic → Dynamic UI transition
    

---

## Tracking & Synchronization

>  **Goal:** Provide reusable, multiplayer-safe systems for tracking player values without requiring each mod to implement its own persistence, synchronization, and decay logic.

### Tracking

- [ ]  Player value tracking
    
- [ ] Tracker history and querying
    
- [ ] Value milestones
    
- [ ] Configurable decay
    
- [ ] Versioned tracking data
    

### Server-authoritative synchronization

- [ ]  Registry synchronization across clients
    
- [ ] Tracking snapshot validation
    
- [ ] Multiplayer reliability for value deltas
    
- [ ] Robust client/server state reconciliation
    

---

## Data Resolution

>  **Goal:** Provide a reusable pipeline for discovering and resolving data without requiring mods to maintain hand-written heuristics.

- [ ]  Runtime data resolution
    
- [ ]  Source classification
    
- [ ]  Source families
    
- [ ] Compatibility discovery
    
- [ ] Runtime diagnostics
    
- [ ] Resolver migration and cleanup
    
- [ ] Ingredient/source attribution
    

---

## Validation Engine

>  **Goal:** Catch invalid or incomplete data before it becomes a runtime problem.

- [ ]  Datapack schema validation
    
- [ ] Value key validation
    
- [ ] Classification coverage analysis
    
- [ ] Registry validation
    
- [ ] Configuration validation
    
- [ ] Validation report generation
    

---

## Crash Diagnostics

>  **Goal:** Make difficult runtime failures understandable instead of requiring developers to reconstruct state manually.

- [ ]  Runtime state capture on failure
    
- [ ]  Classification trace reporting
    
- [ ] Synchronization failure diagnostics
    
- [ ] Registry state diagnostics
    
- [ ] Better contextual error reporting
    

---

## Marie Compiler

**Working name**

>  **Goal:** Build a compiler-style validation and diagnostic layer for MariesLib-powered data.

- [ ]  Architecture validation
    
- [ ] Registry validation
    
- [ ] Configuration validation
    
- [ ] Cross-system validation
    
- [ ] Compiler-style diagnostics
    
- [ ] Actionable fix suggestions
    

>  The compiler should eventually provide a unified way to inspect a modpack's MariesLib data before and during runtime.

---

## Data-pack Loaders

Schema's are established; additional loaders are still in progress.

### In Progress

- [ ]  `values/`
    
- [ ]  `effects/`
    
- [ ]  `synergies/`
    
- [ ]  `source_synergies/`
    
- [ ]  `milestones/`
    
- [ ]  `tracking_profiles/`
    

### Available

- `source_classifications/`
    
- `compat/`
    
- `source_families/`
    
- `module_locks/`
    

---

## Developer & Modpack Tooling

>  **Goal:** Make MariesLib systems easier to inspect, configure, and integrate.

- [ ] Expand KubeJS scripting support
    
- [ ] Broader JEI / REI / EMI tooltip coverage
    
- [ ] Third-party mod discovery improvements
    
- [ ] More diagnostic commands under `/marieslib`
    
- [ ] Improved scanner and inspection tools
    
- [ ]  Better Discover → Model → Verify workflows
    

---

## API & Architecture

>  **Goal:** Keep MariesLib modular and safe to build against as the library grows.

- [ ]  Separate stable and experimental APIs
    
- [ ] Split over-sized API and tracking classes
    
- [ ] Complete domain-agnostic audit of `marie-ui` & `marie-commands`
    
- [ ] Remove mod-specific assumptions from shared systems
    
- [ ] Establish clearer stable, experimental, and internal boundaries
    
- [ ] Keep architecture documentation aligned with the implementation

---

## Compatibility

>  **Goal:** Prove MariesLib's abstractions across multiple mods rather than designing systems around a single consumer.

- [ ]  Retrofit Thin Air: ReLived onto `AttachedValueSync`
    
- [ ] Test tracking and synchronization in additional Marie mods
    
- [ ] Improve third-party mod discovery
    
- [ ] Expand reusable compatibility hooks
    
- [ ] Remove hardcoded mod-specific assumptions where possible
    

---

## Documentation

- [ ] Maintain `README.md`
    
- [ ] Maintain `API.md`
    
- [ ] Maintain `ARCHITECTURE.md`
    
- [ ] Keep `CHANGELOG.md` current
    
- [ ] Document stable vs. experimental APIs
    
- [ ] Add developer examples for major systems
    
- [ ] Document datapack schemas and loaders
    

---

# Long-Term Direction

>  Marie's Lib should become the reusable infrastructure layer that mods and eventually third-party mods, can depend on for:

- **Source classification** without hand-written heuristics
    
- **Player tracking** without custom save, sync, or decay systems
    
- **Compatibility** without hardcoded mod IDs
    
- **Datapack tooling** with schemas and validation
    
- **Multiplayer-safe synchronization**
    
- **Dynamic in-game configuration and editing**
    
- **Runtime diagnostics and crash investigation**
    
- **Compiler-style validation and developer tooling**
    

The overall direction is:

**Discover → Resolve → Validate → Track → Synchronize → Configure → Diagnose**

MariesLib provides the infrastructure; individual mods provide the gameplay.