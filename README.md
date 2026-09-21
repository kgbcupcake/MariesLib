# MariesLib

Shared development library for Marie's NeoForge mods.

MariesLib exists to keep reusable framework code out of individual mods and to provide a common infrastructure for mods.

| Module            | Purpose                                                                              |
| ----------------- | ------------------------------------------------------------------------------------ |
| `marie-core`      | Core APIs, registries, classification, tracking, compat, and shared services         |
| `marie-commands`  | Developer/admin commands and diagnostic tooling                                      |
| `marie-resources` | Resource-driven configuration, definitions, overrides, and data loading              |
| `marie-ui`        | Dynamic UI, draggable/resizable components, panels, colors, and screen configuration |

The goal is **reusable infrastructure, not domain logic**. A consuming mod owns its gameplay systems; MariesLib provides the machinery those systems can build on.

---

## Requirements

|           | Version  |
| --------- | -------- |
| Minecraft | `1.21.1` |
| NeoForge  | `21.1.x` |
| Java      | `21`     |

MariesLib is distributed as a normal NeoForge mod and is **not JarJar bundled** into consuming mods.

If a mod depends on MariesLib, install MariesLib separately.

---

## Development Dependency

Add the GitHub Maven repository and declare MariesLib as a compile-time dependency:

```gradle
repositories {
    maven {
        url = "https://maven.pkg.github.com/kgbcupcake/MariesLib"
    }
}

dependencies {
    compileOnly "dev.marie.MariesLib:marieslib:<version>"
}
```

At runtime, the corresponding MariesLib version must also be present in the instance.

---

# Architecture

MariesLib is organized around a few independent framework areas rather than one large API.

```text
marieslib
├── marie-core
│   ├── API / context
│   ├── classification
│   ├── value tracking
│   ├── compat
│   ├── effects
│   └── shared services
│
├── marie-commands
│   └── framework commands
│
├── marie-resources
│   ├── JSON definitions
│   ├── config overrides
│   └── datapack resources
│
└── marie-ui
    ├── components
    ├── dragging / resizing
    ├── module panels
    ├── colors
    └── configuration screens
```

A consuming mod should depend on the highest-level API necessary for its feature rather than reaching into implementation classes.

---

# Bootstrap

There are two bootstrap paths.

## Framework-only mods

Mods that only need shared framework services can use:

```java
MarieBootstrap.attachFrameworkServices(modBus);
```

This initializes the domain-agnostic services without enabling the player value-tracking system.

Use this when a mod needs things such as:

- `marie-ui`
- compatibility discovery
- shared resource/config infrastructure
- generic state synchronization
- other framework services

`attachFrameworkServices` is idempotent.

---

## Mods using value tracking

Mods that define player-facing values should use:

```java
MarieBootstrap.attach("examplemod", modBus);
```

This sets up the default `MarieContext`, data attachments, value registries, listeners, and related tracking infrastructure.

A value can then be registered during mod initialization:

```java
MarieAPI.registerValue(
    ValueDefinition.builder("emc")
        .displayName("EMC")
        .color(0xFF44AAFF)
        .defaultDecayRate(0.002f)
        .build()
);
```

The consuming mod remains responsible for defining what the value means and how it is used.

---

# Classification

MariesLib provides reusable item classification infrastructure for mods that need to determine what an item represents without maintaining a hand-written mapping for every item in a modpack.

## `ItemScanner`

`ItemScanner` performs an offline/batch classification pass across available items.

Classification uses a cascading signal pipeline including:

- explicit/community tags
- namespace matching
- keyword and suffix matching
- recipe ingredients
- inherited classifications
- weighted confidence scoring

The scanner can be invoked through the framework command:

```text
/marie scan
```

Scanner behavior is controlled by `ScannerSpecRegistry`.

Scanner specifications can be overridden through configuration or datapack resources, allowing modpacks to adjust classification behavior without modifying the consuming mod.

## `RuntimeResolver`

`RuntimeResolver` handles classification during actual gameplay.

Unlike `ItemScanner`, it resolves individual items on demand and caches the result.

`ComponentClassifier` handles component/ingredient classification separately so recursive resolution does not occur through the normal runtime resolver.

## Overrides

Two registries sit above the classifier:

### `SourceClassificationRegistry`

Provides explicit per-item classification/value overrides.

Use this when a modpack author needs to correct or customize the result produced by automatic classification.

### `ExcludedItemsRegistry`

Provides a complete opt-out mechanism.

Excluded items are not classified or tracked.

## Classification tracing

Classification decisions can be inspected through the tracing infrastructure.

A trace can show:

- which pipeline stages were evaluated
- which signals matched
- scores produced by those signals
- multipliers/weights applied
- the final selected classification

This is intended primarily for debugging classification behavior in large modpacks.

---

# Player Value Tracking

The value-tracking framework provides the common infrastructure for mods that maintain player-facing values.

A value can have:

- decay
- thresholds
- effects
- recent-source memory
- diminishing returns
- source synergies
- milestones
- player profiles

Values are registered through `MarieAPI` and accessed through the active `MarieContext`.

For example:

```java
float level = MarieAPI.getValueLevel(player, "emc");
```

## Memory

Recent applications can be tracked by:

- source
- category
- family

Each can participate in its own diminishing-return behavior.

This allows a consuming mod to implement mechanics where repeatedly using the same source becomes less effective without having to implement its own history system.

## Thresholds

Values can define threshold bands such as:

- critical
- low
- excess

Threshold transitions can invoke registered `ThresholdEffect` implementations.

## Synergies

Two forms of synergy are supported:

```text
SynergyDefinition
    value ↔ value

SourcePairSynergy
    source ↔ source
```

This allows consuming mods to define interactions between values or specific sources without putting those rules into the core tracking implementation.

## Milestones

Milestones represent cumulative progression goals.

A milestone can trigger rewards, effects, or advancements when its configured requirement is reached.

---

# Compatibility

MariesLib provides runtime compatibility discovery so integrations do not have to be hard-coded into the consuming mod.

Compatibility definitions can come from three layers:

| Tier | Source                           | Purpose                                       |
| ---- | -------------------------------- | --------------------------------------------- |
| 1    | `mod_compat.json`                | Base definitions shipped by the consuming mod |
| 2    | `CompatDefinition` registrations | Runtime integrations supplied by other mods   |
| 3    | `compat_overrides.json`          | Modpack-level overrides                       |

The runtime compatibility system is exposed through `ModCompat`.

This allows addon mods and modpacks to add or change compatibility behavior without recompiling the original mod.

---

# Tooltips

Tooltip customization is provided through:

```java
TooltipColorRegistry
TooltipMessageRegistry
```

Both support per-key and per-item customization.

Tooltip definitions can be overridden through:

```text
config/<modid>/tooltips/*.json
data/<modid>/marie/tooltips/*.json
```

Datapack definitions take precedence over configuration definitions where applicable.

MariesLib does **not** provide universal tooltip defaults.

A consuming mod owns its tooltip content and should seed its defaults explicitly:

```java
seedDefaultsIfAbsent(...);
```

This keeps MariesLib domain-agnostic.

---

# Dynamic UI

`marie-ui` provides the reusable UI framework used by Marie mods for configurable HUDs and screens.

The framework is designed around concrete components rather than domain-specific screens.

## `MarieComponent`

Base component contract for UI elements participating in the framework.

## `DraggableResizable`

Provides interactive positioning and sizing.

Supported behavior includes:

- dragging
- resizing
- per-edge resize handles
- per-corner resize handles
- parent-bound clamping
- snap-to-sibling behavior

## `ModuleRegistry`

Allows consuming mods to register pluggable UI modules under a shared key.

This makes it possible for multiple screens or regions to maintain independent ordered module lists without the library knowing what those modules represent.

The consuming mod owns the module implementation and presentation; MariesLib only provides the framework needed to arrange and configure them.

---

# API Stability

Public API elements are explicitly marked with `@ApiStatus` annotations.

## `@Stable`

Public API intended for released addons.

Within a minor version, breaking signature changes should not occur unless there is a compelling compatibility reason.

## `@Experimental`

API that is usable by addons but may change between releases.

Most of `marie-ui` and the KubeJS integration currently fall into this category.

## `@Internal`

Implementation detail.

Do not depend on `@Internal` classes or methods from external mods.

If a feature is missing from the stable API, open an issue or request the necessary API rather than depending on internal implementation classes.

See [`API.md`](API.md) for the complete API reference.

---

# Registration Rules

Framework registrations are initialization-time operations.

Calls such as:

```java
MarieAPI.registerValue(...);
MarieAPI.registerCompatEntry(...);
MarieAPI.registerCustomEffect(...);
```

must occur during mod initialization or an appropriate common setup event.

Once the registration window closes, attempting to register new definitions throws:

```text
IllegalStateException
```

This prevents runtime mutation of registries that are expected to be stable during gameplay.

---

# KubeJS

MariesLib provides experimental KubeJS bindings for selected APIs.

Currently supported areas include:

- value registration
- source classifications
- synergies
- milestones
- event hooks

Example:

```js
MarieAPI.registerValue({
    id: "custom_value",
    displayName: "Custom Value",
    decayRate: 0.02
});

MarieEvents.valueChanged(event => {
    if (event.valueKey === "custom_value" && event.newValue < 0.25) {
        event.player.tell("Your custom value is low!");
    }
});
```

The KubeJS API is **Experimental** and may change between releases.

See [`API.md`](API.md#kubejs) for the current bindings and event list.

---

# Compatibility Integrations

MariesLib currently provides framework support for:

| Integration  | Status       |
| ------------ | ------------ |
| KubeJS       | Experimental |
| Cloth Config | Supported    |
| JEI          | Supported    |
| REI          | Supported    |
| EMI          | Supported    |

These integrations are implemented as framework services where possible rather than being required by the core library.

---

# Building MariesLib

Clone the repository and build using the Gradle wrapper:

```bash
./gradlew build
```

The generated artifacts will be placed in:

```text
build/libs/
```

For development, consuming mods should generally use the Maven development dependency rather than copying the MariesLib jar into their source tree.

---

# Repository Structure

The source tree follows the module boundaries:

```text
src/
├── marie-core/
├── marie-commands/
├── marie-resources/
└── marie-ui/
```

Keep domain-specific behavior in the consuming mod.

For example:

```text
Good:
MariesLib → value tracking infrastructure
Nourished → nutrition rules

Good:
MariesLib → draggable component framework
Nourished → nutrition HUD modules

Avoid:
MariesLib → nutrition-specific calculations
MariesLib → Nourished-specific UI
```

The library should solve reusable problems without becoming coupled to the mod that originally motivated the feature.

---

# Documentation

- [`API.md`](API.md): public API reference and resource paths
- [`CHANGELOG.md`](CHANGELOG.md): release history

---

# Projects Using MariesLib

- [Nourished](https://modrinth.com/mod/nourished): nutrition framework for NeoForge 1.21.1
- **Thermal Systems** — planned/early development

---

# Community

[Discord](https://discord.gg/EZnFJsfQup): questions, suggestions, integration discussion, and development.

---

# License

LGPL-3.0-only

---

# Links

- [Modrinth](https://modrinth.com/mod/marieslib)
- [GitHub](https://github.com/kgbcupcake)
- [API Reference](API.md)
- [Changelog](CHANGELOG.md)
