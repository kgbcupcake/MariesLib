# MariesLib API

MariesLib is a NeoForge 1.21.1 library for item classification, player value tracking, tooltip customization, and a dynamic drag/resize UI framework. Consuming mods register their own value keys and gameplay hooks through `MarieContext`. Nourished is the reference consumer — if you only care about nutrition, see [Nourished's API docs](https://github.com/kgbcupcake/nourished/blob/main/API.md) instead.

Every class, method, and tier below is verified directly against source (`dev.marie.framework.*`): not inferred from prose.

## Getting started

```gradle
repositories {
    maven { url = "https://maven.pkg.github.com/kgbcupcake/MariesLib" }
}

dependencies {
    compileOnly "dev.marie.MariesLib:marieslib:<version>"
}
```

At runtime, MariesLib must be installed as a separate mod, there is no JarJar bundling.

## Bootstrap

Two real entry points, both in `dev.marie.framework.core.MarieBootstrap`:

### `attach(String modId, IEventBus modEventBus)` `@Stable`

The one-call path for a mod that wants full player value tracking. Internally:

1. Calls `attachFrameworkServices(modEventBus)` for you (see below) — don't call both.
2. Builds and registers a default `MarieContext`.
3. Registers NeoForge data attachments (`MarieAttributes`, `TrackingAttachment`, `MilestoneProgressAttachment`).
4. On common setup: registers the value-tracking registries (`LockRegistry`, `ColorRegistry`, `ScannerSpecRegistry`, `ExcludedItemsRegistry`, `SourceClassificationRegistry`, `PresetRegistry`), registers core event handlers, and freezes the value-tracking-only API registries.
5. On load-complete: runs mod-compat discovery.

```java
MarieBootstrap.attach("examplemod", modEventBus);
```

### `attachFrameworkServices(IEventBus modEventBus)` `@Experimental`

The minimal, domain-agnostic call. Idempotent. Only freezes registries unrelated to value tracking (`BlockHoverProviderRegistry`, `GenericStateSyncHandlerRegistry`). Use this if your mod needs the UI framework, compat system, or hover data, but has no player value bars.

```java
MarieBootstrap.attachFrameworkServices(modEventBus);
// separately, wire whatever MarieContext fields you actually need:
MarieContext.register(MarieContext.builder("thermalsystems")
    .configScreenFactory(...)
    .build());
```

A deprecated `bootstrap(IEventBus)` also exists (old owned-config path): use `attach` instead.

## Registration window

All `MarieAPI.register*` calls must happen during mod initialization, your `@Mod` constructor or an `FMLCommonSetupEvent` handler. The window closes after init; calling register outside it throws `IllegalStateException`. Datapack reloads open a secondary internal window for datapack-driven content; you don't manage that yourself.

## Core concepts

**Classification**: `ItemClassifier` runs a cascading, confidence-scored pipeline (community tags, namespace, suffix, keyword, recipe inheritance) to guess what an item is. Powers `/marie scan`. Configured by `ScannerSpecRegistry`.

**Runtime resolution**: `RuntimeResolver` is the live, cached gameplay-time resolution path (LRU cache, hit/miss stats, recipe-timeout handling). `ComponentClassifier` handles ingredient/sub-component lookups separately, deliberately skipping recipe inheritance to avoid recursion.

**Manual overrides**: `SourceClassificationRegistry` (modpack-author per-item value overrides) and `ExcludedItemsRegistry` (opt-out list — never classified or tracked) sit on top of whatever the classifier produces.

**Value**: a tracked bar (`ValueDefinition`), driving decay, thresholds, and HUD rendering.

**Memory**: per-item/category/family diminishing-returns tracking on `TrackingData`. Server-side only.

**Tooltips**: `TooltipColorRegistry`/`TooltipMessageRegistry` provide per-key and per-item tooltip text/color, two-tier overridable (config → datapack). Unlike other registries, no built-in defaults — call `seedDefaultsIfAbsent(...)`.

**UI modules**: `ModuleRegistry` lets a consuming mod register pluggable panel modules under an opaque key (conventionally `modId` or `modId.region`), each maintaining an independent ordered list. `MarieComponent` is the base contract; `DraggableResizable` handles drag/resize gestures.

## Stability tiers

- **`@ApiStatus.Stable`**: safe for released addons; no breaking signature changes within a minor version
- **`@ApiStatus.Experimental`**: may change any release
- **`@ApiStatus.Internal`**: not a public contract; do not import

> **Known gap:** `ModuleRegistry`, `ComponentState`, and `MarieComponent` are documented here as `@Experimental` to match the rest of `marie-ui`'s real extension points, even though they carry no `@ApiStatus` annotation in source as of this writing. If you're building against them, treat them as `@Experimental` (subject to change) until the annotation lands.

---

## `MarieAPI` reference

All static, in `dev.marie.framework.api.marieapi.MarieAPI`.

### Player queries — `@Stable`

```java
float getAggregateLevel(Player player)
float getValueLevel(Player player, String valueKey)
ApplicationHistoryView getApplicationHistory(Player player)
float getTotalCount(Player player)
MariePlayerData getTrackingData(Player player)
void modifyValue(Player player, String valueKey, float delta)
String getVersion()
```

`modifyValue` posts a `ValueModifierEvent` first — listeners can cancel or change the amount before it lands.

### Registration — values & sources — `@Stable`

```java
void registerValue(ValueDefinition definition)                 // alias: addValue
void registerSourceClassification(ResourceLocation sourceId, String valueKey, float amount)
void registerSource(ResourceLocation sourceId, String valueKey, float amount)  // alias
```

### Registration — effects, compat, synergies — `@Stable`

```java
void registerCustomEffect(ThresholdEffect definition)           // alias: addEffect
void registerCompatEntry(CompatDefinition definition)           // alias: addCompat
void registerValueSynergy(SynergyDefinition definition)         // alias: addValueSynergy
void registerSourcePairSynergy(SourcePairSynergy definition)    // alias: addSourceSynergy
void registerTrackingProfile(ProfileDefinition definition)      // alias: addProfile
void registerMilestone(MilestoneDefinition definition)          // alias: addMilestone
```

### Registration — hooks — mostly `@Stable`, two `@Experimental`

```java
void registerSeasonHook(MarieSeasonHook hook)                   // alias: addSeasonHook — Stable
void registerAbsorptionModifier(AbsorptionModifier modifier)    // alias: addAbsorptionModifier — Stable
void registerReportProvider(ReportProvider provider)            // alias: addReportSection — Stable
void registerBlockHoverProvider(BlockHoverProvider provider)    // Experimental
void registerSourcePropertySignal(SourcePropertySignal signal)  // Stable
void registerSleepBonusEvaluator(SleepBonusEvaluator evaluator) // Stable
void registerTriggerHandler(SourceTriggerListener handler)      // Stable
void registerTriggerSource(SourceTriggerDefinition definition)  // alias: addTriggerSource — Stable
void registerConfigValidator(ConfigValidator validator)         // Stable
void registerTagRule(TagRule rule)                              // Stable
void registerTagAuditContext(String modId, TagAuditContext ctx) // Stable
void registerCommandCapability(ResourceLocation modId, ResourceLocation capability, CommandCapability handler)  // Experimental
void registerGenericStateSyncHandler(BiConsumer<ServerPlayer, GenericStateSyncPayload> handler)  // Experimental
```

### Export

```java
<T> void registerExportResolver(String key, ResourceKey<Registry<T>> registryKey, ExportResolver<T> resolver)  // Stable — real path
@Deprecated <T> void registerExportResolver(ExportResolver<T> resolver)  // Stable but non-functional — see below
```

The single-arg overload is deprecated and structurally cannot work (no way to know which registry to iterate). Use the two-arg overload.

### Custom triggers — `@Stable`

For non-item sources (crafting, EMC, block breaks). Server-side only. Runs the full pipeline: classification, modifiers, synergies, threshold checks.

```java
void fireSourceTrigger(ServerPlayer player, ValueSourceTrigger trigger)
void fireSourceTrigger(ServerPlayer player, ValueSourceTrigger trigger, @Nullable ItemStack stack)
```

---

## Definition builders

All follow the same `builder(...).field(...).build()` pattern. All `@Stable` with `@Stable` builders unless noted.

### `ValueDefinition`

```java
ValueDefinition.builder("emc")
    .displayName("EMC")
    .color(0xFF66CCFF)
    .defaultDecayRate(0.001f)
    .criticalThreshold(0.1f)
    .lowThreshold(0.3f)
    .excessThreshold(0.9f)
    .beneficial(true)
    .amountScale(1.0)
    .customRenderer(myRenderer)  // @Nullable, type ValueRenderer (Experimental)
    .build()
```

### `ThresholdEffect`

```java
ThresholdEffect.builder()
    .valueKey("emc")
    .threshold(0.1f)
    .thresholdType(ThresholdEffect.ThresholdType.CRITICAL)  // CRITICAL, LOW, EXCESS, BONUS
    .effectId(myEffectId)
    .amplifier(0)
    .duration(200)
    .build()
```

### `SynergyDefinition`

```java
SynergyDefinition.builder("my_synergy")
    .valueA("emc", SynergyDefinition.LevelCondition.HIGH)  // HIGH, LOW, OPTIMAL
    .valueB("stamina", SynergyDefinition.LevelCondition.HIGH)
    .bonusEffect(myEffectId)
    .effectAmplifier(0)
    .effectDuration(200)
    .penalty(false)
    .build()
```

### `SourcePairSynergy`

```java
SourcePairSynergy.builder("my_pair_synergy")
    .sourceA(itemA)
    .sourceB(itemB)
    .timeWindowTicks(200)
    .bonusValueKey("emc")
    .bonusAmount(0.1f)
    .valueModifier(1.2f)
    .modifierDurationTicks(600)
    .build()
```

### `ProfileDefinition`

```java
ProfileDefinition.builder("hardcore")
    .displayName("Hardcore")
    .customThreshold("emc", 0.2f)
    .customDecayRate("emc", 0.003f)
    .addBonusEffect(myEffectId)
    .description("Faster decay, higher thresholds")
    .build()
```

### `MilestoneDefinition`

```java
MilestoneDefinition.builder("emc_master")
    .valueKey("emc")
    .cumulativeGoal(1000f)
    .rewardEffect(myEffectId)
    .rewardAmplifier(0)
    .rewardDuration(200)
    .advancement(myAdvancementId)
    .build()
```

### `CompatDefinition`

```java
CompatDefinition.builder("farmersdelight")
    .category(CompatDefinition.CompatCategory.CONTENT_MOD)  // SOURCE_MOD, FARMING_MOD, SURVIVAL_OVERHAUL
    .addSourceMapping(someItemId, "emc")
    .build()
```

---

## NeoForge events

`dev.marie.framework.api.marie.MarieEvents`. Subscribe with `@SubscribeEvent`.

| Event                                      | When                                 | Cancellable |
| ------------------------------------------ | ------------------------------------ | ----------- |
| `ValueChangedEvent`                        | After a value level changes          | No          |
| `ValueCriticalEvent`                       | Value drops below critical threshold | No          |
| `ValueExcessEvent`                         | Value rises above excess threshold   | No          |
| `SourceTriggerEvent`                       | Before pipeline processes a trigger  | Yes         |
| `SourceAppliedEvent`                       | After a source applies value         | No          |
| `MilestoneTriggeredEvent`                  | Milestone goal reached               | No          |
| `ValueModifierEvent` (`api.value` package) | Before `modifyValue` applies a delta | Yes         |

Use `ValueModifierEvent` or `SourceTriggerEvent` to intercept before something happens. Use the `*Changed`/`*Critical`/`*Excess`/`*Triggered` events to react after the fact.

---

## KubeJS

All `@Experimental`, in `dev.marie.framework.kubejs.*` (marie-commands module).

**Startup bindings**: global `MarieAPI` (backed by `MarieKubeBindings`):

```js
MarieAPI.registerValue({ id: 'custom_value', displayName: 'Custom Value', decayRate: 0.02 })
MarieAPI.registerSourceClassification('minecraft:apple', 'custom_value', 0.35)
MarieAPI.registerSourceOverride('minecraft:apple', { custom_value: 0.5 })
MarieAPI.registerEffect({ /* spec */ })
MarieAPI.registerMilestone({ /* spec */ })
MarieAPI.registerValueSynergy({ /* spec */ })
MarieAPI.registerSourcePairSynergy('minecraft:apple', 'minecraft:bread', 200, 'custom_value', 0.2)
```

**Server bindings**: global `MarieServer` (server scripts only, backed by `MarieKubeServerBindings`):

```js
let level = MarieServer.getValueLevel(player, 'custom_value')
MarieServer.forceSetValue(player, 'custom_value', 0.5)
let keys = MarieServer.getValueKeys()
MarieServer.fireSourceTrigger(player, 'minecraft:apple')
```

**Events**: `MarieEvents` group, 8 handlers:

```js
MarieEvents.valueChanged(event => { /* event.playerId, valueKey, oldValue, newValue */ })
MarieEvents.valueCritical(event => { /* playerId, valueKey */ })
MarieEvents.valueExcess(event => { /* playerId, valueKey */ })
MarieEvents.sourceConsumed(event => { /* playerId, itemId, valueKey, amount */ })
MarieEvents.milestoneTriggered(event => { /* playerId, milestoneId, valueKey, cumulativeIntake, cumulativeGoal */ })
MarieEvents.valueDeltaModifier(event => { event.setAmount(...); event.cancel(); })
MarieEvents.decayTick(event => { /* playerId, valueKey, amount — settable/cancellable */ })
MarieEvents.playerSynced(event => { /* playerId */ })
```

---

## Datapack & config paths

Verified against source, not just changelog prose.

| Concept                | Bundled default                                      | Config override (server owner)                                                                    | Datapack override (modpack, wins)                    |
| ---------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| Scanner spec           | `data/<modid>/<modid>/scanner/scanner_spec.json`     | `config/<modid>/scanner_spec.json`                                                                | `data/<ns>/<modid>/scanner/scanner_spec.json`        |
| Excluded items         | `data/<modid>/<modid>/scanner/excluded_items.json`   | `config/<modid>/overrides/Overrides/excluded_items.json`                                          | `data/<ns>/<modid>/scanner/excluded_items.json`      |
| Source classifications | —                                                    | `config/<modid>/overrides/Overrides/source_classifications.json` (README at `overrides/Read_Me/`) | —                                                    |
| Colors                 | —                                                    | `config/<modid>/colors.json`                                                                      | `data/<modid>/config/colors.json`                    |
| Locks                  | —                                                    | `config/<modid>/locks.json`                                                                       | `data/<modid>/config/locks.json`                     |
| Mod compat             | `data/<modid>/config/mod_compat.json` (bundled only) | —                                                                                                 | —                                                    |
| Presets                | —                                                    | `config/<modid>/presets/*.json`                                                                   | —                                                    |
| Tooltip messages       | —                                                    | `config/<modId>/tooltips/tooltip_messages.json`                                                   | `data/<modId>/marie/tooltips/tooltip_messages.json`  |
| Tooltip colors         | —                                                    | `config/<modId>/tooltips/tooltip_colors.json`                                                     | `data/<modId>/marie/tooltips/tooltip_colors.json`    |
| Source families        | —                                                    | —                                                                                                 | `data/<namespace>/<modid>/source_families/<id>.json` |
| Module locks           | —                                                    | —                                                                                                 | `data/<namespace>/<modid>/module_locks/<id>.json`    |

Source families and module locks load through `MarieDataLoader` (a real, active `SimpleJsonResourceReloadListener`) into `FamilyResolver` and `LockRegistry` respectively via `MarieDatapackCallbacks`.

Legacy/older file layouts for excluded items and source classifications are auto-migrated on load, you don't need to manually move existing files.

---

## Commands

- `/marieslib`: library diagnostics, scanner tooling, schema templates
- `/marie`: generic consumer command tree (report, value, set, reset, profile, reload)

Consuming mods register their own namespace via `MarieConsumerCommandTree`: Nourished uses `/nourished`, for example. Custom command-tree capabilities can be contributed via `MarieAPI.registerCommandCapability(...)` (`CommandCapability`, `@Experimental`).

---

## `MarieContext`

`dev.marie.framework.core.MarieContext` (`@Experimental`) is the per-mod runtime configuration object, a builder with dozens of optional hooks (thresholds, resolvers, screen factories, preset hooks, respawn behavior, etc.), all defaulted except `modId`.

```java
MarieContext.register(
    MarieContext.builder("examplemod")
        .respawnValueBehavior(() -> RespawnValueBehavior.PRESERVE)
        .sourceFamilyResolver(itemId -> "custom_family")
        .build()
);
```

`MarieContext.get()` / `MarieContext.isRegistered()` read back the currently registered context. `MarieModRegistry` (`@Experimental`) tracks every registered context by `modId` for a future multi-mod config UI.

Most `MarieContext` fields are `@Internal` (implementation wiring for the framework itself). The consumer-relevant `@Stable`/`@Experimental` surface is: `respawnValueBehavior`/`respawnValueHandler` (`@Stable`; `deathNutritionBehavior`/`deathNutritionHandler` deprecated forwarders kept for compat), `valueKeys()`, `valueDefinitionFor(key)`, `dataProvider(...)`, and `builder(modId)` itself — all `@Stable`. Most `Builder` setter methods are unannotated (internal wiring); the ones a real integration is most likely to touch carry `@Experimental` (e.g. `sourceItemFilter`, `sourceValueResolver`, `sourceDeltaResolver`, `runtimeResolverStages`, `trackingDeltaSyncer`).

---

## Module windows (marie-ui) — `@Experimental`

Every module's options window is built through one facade, `dev.marie.framework.ui.api.MarieModuleSettings`, so all of them share the same tabs and groups. Do not hand-build a window's layout.

```java
MarieComponent content = MarieModuleSettings.standardPanel("My Module", persistence, "my_module")
        .opacity(cfg::bg, cfg::setBg, 0.8)            // Style > Background
        .backgroundShade(cfg::bgShade, cfg::setBgShade)
        .borderOpacity(cfg::border, cfg::setBorder)   // Style > Border
        .borderShade(cfg::borderShade, cfg::setBorderShade)
        .textBrightness(cfg::text, cfg::setText)      // Style > Brightness
        .iconBrightness(cfg::icon, cfg::setIcon)
        .layoutRows(p -> p.toggle("Vertical", cfg::vertical, cfg::setVertical, cfg::save))
        .behaviorRows(p -> p.section("Visibility").toggle(...))
        .extraTabs(p -> p.colorTab("Colors").color("Header", ...))
        .onCommit(cfg::save)
        .build();
```

- **Tabs:** Layout (Padding + your `layoutRows`), Behavior (your `behaviorRows`, a collapsible **Move** group, a collapsible **Hide** group with Hide Icons), Style (collapsible **Sizes**, **Brightness**, **Background**, **Border** groups; a group shows only if you bind something for it) and any `extraTabs` such as a Colors tab.
- **Groups:** `MarieToolbox.PanelBuilder.section(title)` / `endSection()` fold any options into a collapsible group.
- **Hide Icons:** enforced for any module drawn through `MarieModuleSettings.withDisplaySettings`; read it with `MarieModuleSettings.isIconsHidden(store, panelId)` if you draw icons yourself.
- **Standalone controls:** `dev.marie.framework.ui.api.MarieWidgets` is the one-file facade for the parts the windows are built from, usable in any screen: `tabBar(id, titles...)` (`.onChange`, `.selected()`), `button(id, caption, action)` (`.enabledWhen`), `slider`/`intSlider` (rounded bar with arrow buttons), `toggle`, `choice` and `section(title, rows...)` (collapsible). Each returns a `MarieComponent`; values stay behind your getters/setters and `onCommit` runs once per finished edit.
- **Colors:** `PanelBuilder.colorTab(...).color(...)` opens the shared round color picker.

## Versioning

```java
MarieAPIVersion.VERSION          // "1.0.0"
MarieAPIVersion.MAJOR / MINOR / PATCH
MarieAPIVersion.isCompatible(1)  // true if MAJOR >= required
```

Semantic versioning. Major bumps may break `@Stable` APIs with a migration guide. Minor bumps may evolve `@Experimental` APIs.

---

## Full class inventory

For every `@ApiStatus.Stable`/`Experimental` class across all four modules — full method signatures, not just the consumer-facing highlights above — see the [class inventory appendix](https://github.com/kgbcupcake/MariesLib/wiki/API-Class-Inventory) _(or: request the full table; it's long enough to warrant its own page rather than bloating this file)_.

Quick orientation by module:

- **marie-core** — `MarieAPI` facade, `MarieContext`, `MarieBootstrap`, all definition classes, events, scanner/classification classes, tag-audit classes, tracking classes.
- **marie-commands**: KubeJS integration only, all `@Experimental`.
- **marie-resources**: no public API surface; internal writer utilities only.
- **marie-ui** — `GuiValueRenderer`, `MarieValueColors`, `TooltipColorRegistry`/`TooltipMessageRegistry`, `ForeignScreenDetector`, `DraggableResizable`, plus `ModuleRegistry`/`ComponentState`/`MarieComponent` (see the known-gap note above).
