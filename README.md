# MariesLib

Shared development library for Marie's NeoForge mods.

MariesLib contains reusable framework code shared between projects instead of being implemented separately in each mod.

The library is intentionally domain-agnostic where possible. A consuming mod defines what its values, trackers, UI modules, compatibility rules, or gameplay systems mean; MariesLib provides the infrastructure they run on.

---

## Modules

| Module            | Purpose                                                                                                            |
| ----------------- | ------------------------------------------------------------------------------------------------------------------ |
| `marie-core`      | Core APIs, contexts, registries, classification, tracking, compatibility, effects, networking, and shared services |
| `marie-commands`  | Framework commands and diagnostic tooling                                                                          |
| `marie-resources` | Resource-driven definitions, configuration, overrides, and datapack loading                                        |
| `marie-ui`        | Shared UI components, rendering, layouts, widgets, notifications, persistence, and command-center infrastructure   |
| `marie-editor`    | UI editing, module settings, toolbox controls, scale configuration, layout editing, and editor-facing APIs         |

`marie-editor` builds on `marie-ui`.

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

<details>
<summary><strong>Development Dependency</strong></summary>

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

The corresponding MariesLib version must also be present at runtime.

</details>

---

<details>
<summary><strong>Architecture</strong></summary>

MariesLib is divided into framework areas rather than one large API.

```text
marieslib
├── marie-core
│   ├── API / context
│   ├── classification
│   ├── value tracking
│   ├── generic tracking
│   ├── compat
│   ├── effects
│   ├── networking
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
├── marie-ui
│   ├── components
│   ├── rendering
│   ├── layouts
│   ├── widgets
│   ├── notifications
│   ├── persistence
│   └── command center
│
└── marie-editor
    ├── module settings
    ├── toolbox
    ├── scale configuration
    ├── layout editing
    ├── edit mode
    └── editor-facing API
```

A consuming mod should depend on the highest-level API necessary for the feature it uses rather than reaching into implementation classes.

</details>

---

<details>
<summary><strong>Bootstrap</strong></summary>

There are two primary bootstrap paths.

### Framework services

Mods that only need shared framework services can use:

```java
MarieBootstrap.attachFrameworkServices(modBus);
```

This initializes domain-agnostic framework services without enabling the player value-tracking system.

Use this for systems such as:

- `marie-ui`
- compatibility discovery
- shared resource/config infrastructure
- generic state synchronization
- other framework services

`attachFrameworkServices` is idempotent.

### Value tracking

Mods that define player-facing values can use:

```java
MarieBootstrap.attach("examplemod", modBus);
```

This initializes the default `MarieContext`, player data attachments, value registries, listeners, and related tracking infrastructure.

A value can then be registered during initialization:

```java
MarieAPI.registerValue(
    ValueDefinition.builder("example")
        .displayName("Example")
        .color(0xFF44AAFF)
        .defaultDecayRate(0.002f)
        .build()
);
```

The consuming mod remains responsible for defining what the value means and how it affects gameplay.

The older `bootstrap(IEventBus)` entry point is deprecated. New code should use `attach(...)` or `attachFrameworkServices(...)`.

</details>

---

<details>
<summary><strong>Registration</strong></summary>

Framework registrations are initialization-time operations.

Examples:

```java
MarieAPI.registerValue(...);
MarieAPI.registerCompatEntry(...);
MarieAPI.registerCustomEffect(...);
```

These registrations must occur during mod initialization or an appropriate common setup event.

Once the initialization registration window closes, attempting to register new definitions throws:

```text
IllegalStateException
```

This prevents runtime mutation of registries that are expected to remain stable during gameplay.

Datapack-driven systems have their own reload registration scope. The registration state for mod initialization and datapack reloads is tracked independently.

</details>

---

<details>
<summary><strong>Classification</strong></summary>

MariesLib provides reusable item classification infrastructure for mods that need to determine what an item represents without maintaining a manually written mapping for every item in a modpack.

### `ItemScanner`

`ItemScanner` performs an offline/batch classification pass across available items.

Classification can use:

- explicit/community tags
- namespace matching
- keyword matching
- suffix matching
- recipe ingredients
- inherited classifications
- weighted confidence scoring

The scanner can be invoked through:

```text
/marie scan
```

Scanner behavior is controlled by `ScannerSpecRegistry`.

Scanner specifications can be overridden through configuration or datapack resources.

The scanner is primarily developer and modpack tooling rather than a player-facing gameplay feature.

### `RuntimeResolver`

`RuntimeResolver` handles classification during actual gameplay.

Unlike `ItemScanner`, it resolves individual items on demand and caches the result.

`ComponentClassifier` handles ingredient/component classification separately so recursive resolution does not occur through the normal runtime resolver.

### `SourceClassificationRegistry`

Provides explicit per-item classification/value overrides.

This can be used when a modpack author needs to correct or customize the result produced by automatic classification.

### `ExcludedItemsRegistry`

Provides a complete opt-out mechanism.

Excluded items are not classified or tracked.

### Classification tracing

Classification decisions can be inspected through the tracing infrastructure.

A trace can show:

- pipeline stages that were evaluated
- matching signals
- scores produced by those signals
- weights and multipliers
- the final selected classification

This is primarily intended for debugging classification behavior in large modpacks.

</details>

---

<details>
<summary><strong>Player Value Tracking</strong></summary>

The value-tracking framework provides common infrastructure for mods that maintain persistent player-facing values.

A value can provide:

- decay
- thresholds
- effects
- recent-source memory
- diminishing returns
- source synergies
- milestones
- player profiles

Values are registered through `MarieAPI` and accessed through the active `MarieContext`.

```java
float level = MarieAPI.getValueLevel(player, "example");
```

### Memory

Recent applications can be tracked by:

- source
- category
- family

Each can participate in its own diminishing-return behavior.

This allows a consuming mod to implement repeated-source penalties without implementing its own history system.

### Thresholds

Values can define threshold bands such as:

- critical
- low
- excess

Threshold transitions can invoke registered `ThresholdEffect` implementations.

### Synergies

Two forms of synergy are supported:

```text
SynergyDefinition
    value ↔ value

SourcePairSynergy
    source ↔ source
```

The consuming mod defines the actual gameplay meaning of each synergy.

</details>

---

<details>
<summary><strong>Generic Tracking</strong></summary>

`MarieTracking` provides generic numeric accumulator tracking independently of the player value system.

Trackers can be used for arbitrary counters such as:

- blocks mined
- entities defeated
- distance traveled
- machine operations
- items produced
- spells cast
- resources consumed
- any other numeric value

Supported periods include:

```text
SESSION
DAILY
WEEKLY
MONTHLY
CUSTOM
REAL_TIME
```

Trackers can retain historical periods according to their configured retention policy.

Daily, weekly, and monthly periods use the world's day clock. This allows sleeping and `/time` changes to cross period boundaries correctly.

`SESSION` and `CUSTOM` retain game-time behavior, while `REAL_TIME` uses the wall clock.

Example:

```java
MarieTracking.registerTracker(
    TrackerDefinition.daily("example:blocks_mined", 30)
);

MarieTracking.incrementTracker(
    player,
    "example:blocks_mined",
    1
);
```

</details>

---

<details>
<summary><strong>Milestones</strong></summary>

MariesLib provides a generic one-time milestone system.

Milestones can operate against:

- registered player values
- generic `MarieTracking` accumulators

A tracker milestone can evaluate either:

- lifetime cumulative progress
- the current tracking period

Milestones can be registered through Java, datapack resources, or KubeJS where the corresponding integration is available.

A milestone can provide configured rewards such as:

- potion effects
- vanilla advancements
- event notifications

The consuming mod defines what the milestone represents.

</details>

---

<details>
<summary><strong>Compatibility</strong></summary>

MariesLib provides runtime compatibility discovery so integrations do not have to be hard-coded into the consuming mod.

Compatibility definitions can come from multiple layers:

| Tier | Source                           | Purpose                                       |
| ---- | -------------------------------- | --------------------------------------------- |
| 1    | Bundled compatibility resources  | Base definitions shipped by the consuming mod |
| 2    | `CompatDefinition` registrations | Runtime integrations supplied by other mods   |
| 3    | Configuration overrides          | Modpack-level changes                         |

The runtime compatibility system is exposed through `ModCompat`.

Later definitions can extend or override earlier definitions without requiring the original consuming mod to be recompiled.

</details>

---

<details>
<summary><strong>Tooltips</strong></summary>

Tooltip customization is provided through:

```java
TooltipColorRegistry
TooltipMessageRegistry
```

Both support per-key and per-item customization.

Definitions can be provided through configuration and datapack resources.

Typical resource paths are:

```text
config/<modid>/tooltips/*.json
data/<modid>/marie/tooltips/*.json
```

Datapack definitions take precedence over configuration definitions where applicable.

MariesLib does not provide universal tooltip defaults.

A consuming mod owns its tooltip content and should seed its own defaults explicitly:

```java
seedDefaultsIfAbsent(...);
```

This keeps the library domain-agnostic.

</details>

---

<details>
<summary><strong>Dynamic UI</strong></summary>

`marie-ui` provides reusable UI infrastructure for consuming mods.

The framework is built around concrete reusable components rather than domain-specific screens.

### `MarieComponent`

Base component contract for UI elements participating in the framework.

### `DraggableResizable`

Provides interactive positioning and sizing.

Supported behavior includes:

- dragging
- per-edge resize handles
- per-corner resize handles
- parent-bound clamping
- snap-to-sibling behavior

### `SnapRegistry`

Provides shared snap relationships between registered components.

Independent top-level components can participate in the same snap system without sharing a parent.

### Rendering

`RenderContext` provides common rendering operations for components.

The rendering infrastructure includes support for:

- text
- items
- rectangles
- bars
- lines
- clipping
- shared render-state handling

The rendering layer also restores shared GUI state after component rendering so a consumer cannot leave the remainder of the frame with an altered pose or clipping state.

### Widgets

The UI module includes reusable components such as:

#### `MarieTextList`

A scrollable, word-wrapped text/output list supporting:

- line insertion
- bulk replacement
- clearing
- automatic following of new output
- maximum line counts
- width-aware wrapping

#### `MarieListPicker`

A scrollable single-selection list.

#### `MarieGraph`

A rolling single-series graph supporting:

- sample insertion
- configurable line color
- optional units
- automatic vertical scaling
- minimum range handling for flat data
- axis labels

### Notifications

The notification system provides stacking and mergeable player-facing notifications anchored to the GUI.

Notifications are intended for discrete events rather than continuous HUD rendering.

### Command Center

`MarieCommandCenter` provides a shared, pluggable command-center screen.

Consuming mods can register categories and cards into a common registry.

Cards can:

- open configuration
- run a command
- invoke a caller-supplied action

A shared keybind can be registered with:

```java
MarieCommandCenter.registerOpenKey(modEventBus);
```

The keybind is registered once across consuming mods and only opens the command center when no other screen is active.

</details>

---

<details>
<summary><strong>UI Editor</strong></summary>

`marie-editor` contains the editing systems built on top of `marie-ui`.

It provides:

- module display settings
- layout editing
- move modes
- hide modes
- toolbox controls
- color editing
- scale configuration
- edit-mode coordination
- editor-facing API facades

### `MarieModuleSettings`

Provides reusable display configuration for HUD-style modules.

Settings can include:

- text scale
- icon scale
- text position
- icon position
- bar position
- bar scale
- text brightness
- icon brightness
- move modes
- hide modes
- reset positions
- per-tab reset values

Move modes include:

```text
TEXT
ICONS
BARS
HEADER
ALL
```

Modules can separately expose controls for moving or hiding different parts of their content.

The framework can track extents for text, icons, bars, and separately positioned headers so editor outlines correspond to the actual rendered areas.

### `StandardPanelBuilder`

Provides a reusable configuration-panel structure.

Standard sections can include:

- Layout
- Behavior
- Style

Consumers can add their own tabs for module-specific settings.

### `MarieToolbox`

Provides reusable editing controls including:

- sliders
- toggles
- buttons
- sections
- tabs
- color slots
- reset controls

Controls can use caller-provided getters, setters, and defaults without the toolbox owning the underlying storage.

### Color editing

Color editing supports:

- live RGB editing
- hex display
- reset
- commit callbacks
- optional cancel callbacks

The color picker is a separate draggable/resizable window rather than being constrained by the module body's clipping region.

### `ScaleConfigPanel`

Provides configuration for:

- content scale
- text scale
- padding
- related display settings

It can host caller-provided content while also providing standard controls.

### `EditModeCoordinator`

Coordinates edit mode across multiple independent UI panels.

This allows several registered components to enter or leave edit mode together without each component implementing its own global editing state.

</details>

---

<details>
<summary><strong>Networking and Shared Requests</strong></summary>

`MarieRequestChannel` provides a reusable client/server request-response channel for actions that need to execute on the server and return textual results to the client.

A consumer creates its own channel and registers it on the mod event bus.

Requests can provide:

- an action identifier
- an argument
- a server-side permission predicate

Responses provide:

- a label
- response lines

Request fields and response data are bounded during encoding and decoding.

`MarieRequestChannel` is currently `@Experimental`.

</details>

---

<details>
<summary><strong>Resources and Configuration</strong></summary>

`marie-resources` contains resource-driven infrastructure used by MariesLib and consuming mods.

Systems can load definitions and overrides from configuration and datapack resources.

Resource-driven systems include areas such as:

- classification
- compatibility
- tooltips
- scanner specifications
- other framework definitions

The consuming mod remains responsible for the meaning and defaults of its domain-specific data.

</details>

---

<details>
<summary><strong>Commands and Diagnostics</strong></summary>

`marie-commands` contains framework commands and diagnostic tooling.

The classification scanner is available through:

```text
/marie scan
```

Diagnostic systems are intended for:

- development
- debugging
- modpack configuration
- classification troubleshooting
- inspecting framework state

The commands module does not define consuming-mod gameplay commands.

</details>

---

<details>
<summary><strong>API Stability</strong></summary>

Public API elements use explicit `@ApiStatus` annotations.

### `@Stable`

Public API intended for released addons and consuming mods.

Breaking signature changes should not occur within a minor version unless there is a compelling compatibility reason.

### `@Experimental`

API that is usable by consuming mods but may change between releases.

Newer UI extension points, KubeJS bindings, request channels, and other developing systems may use this tier.

### `@Internal`

Implementation detail.

External mods should not depend on `@Internal` classes or methods.

If a feature is missing from the stable API, request the necessary API rather than depending on an internal implementation class.

See [`API.md`](API.md) for the current API reference.

</details>

---

<details>
<summary><strong>KubeJS</strong></summary>

MariesLib provides experimental KubeJS bindings for selected APIs.

Supported areas include:

- value registration
- source classifications
- synergies
- milestones
- event hooks

Example:

```javascript
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

The KubeJS API is `@Experimental` and may change between releases.

See [`API.md`](API.md) for the current bindings and event list.

</details>

---

<details>
<summary><strong>Integration Support</strong></summary>

MariesLib currently provides framework support for:

| Integration  | Status       |
| ------------ | ------------ |
| KubeJS       | Experimental |
| Cloth Config | Supported    |
| JEI          | Supported    |
| REI          | Supported    |
| EMI          | Supported    |

These integrations are kept separate from systems that do not require them where possible.

</details>

---

<details>
<summary><strong>Repository Structure</strong></summary>

The source tree follows the module boundaries:

```text
src/
├── marie-core/
├── marie-commands/
├── marie-resources/
├── marie-ui/
└── marie-editor/
```

The general rule is that domain-specific behavior belongs in the consuming mod.

Examples:

```text
Good:
MariesLib → value tracking infrastructure
Nourished → nutrition rules

Good:
MariesLib → dynamic UI/editor infrastructure
Nourished → nutrition HUD modules

Good:
MariesLib → generic tracking
Another mod → what its tracker counts

Avoid:
MariesLib → nutrition-specific calculations
MariesLib → Nourished-specific UI
MariesLib → consuming-mod gameplay rules
```

A system belongs in MariesLib when it represents reusable infrastructure rather than behavior belonging to one particular consuming mod.

</details>

---

<details>
<summary><strong>Projects Using MariesLib</strong></summary>

- [Nourished](https://modrinth.com/mod/nourished) — nutrition mod for NeoForge 1.21.1
- Thermal Systems — development
- ProjectE Extended Life — planned

</details>

---

<details>
<summary><strong>Building</strong></summary>

Clone the repository and build using the Gradle wrapper:

```bash
./gradlew build
```

Generated artifacts are placed in:

```text
build/libs/
```

For consuming-mod development, use the Maven development dependency rather than copying the MariesLib jar into the source tree.

</details>

---

<details>
<summary><strong>Documentation</strong></summary>

- [`API.md`](API.md) — public API, stability tiers, resource paths, and usage details
- [`CHANGELOG.md`](CHANGELOG.md) — release history and development changes

</details>

---

## Community

[Discord](https://discord.gg/EZnFJsfQup)

Questions, bug reports, suggestions, and integration discussion are welcome.

---

## License

LGPL-3.0-only

---

## Links

- [Modrinth](https://modrinth.com/mod/marieslib)
- [GitHub](https://github.com/kgbcupcake/MariesLib)
- [API Reference](API.md)
- [Changelog](CHANGELOG.md)
- [Discord](https://discord.gg/EZnFJsfQup)
