# MariesLib Diagnostics — Addon Developer Guide

MariesLib can write crash and failure reports for your mod, so you don't have to build your own.
You get three things:

1. **Crash companion reports.** When Minecraft crashes, MariesLib saves its own report next to
   Minecraft's crash report. It says which mods' code was running and what state your mod was in.
2. **Failure reports.** When your mod catches an error, you can send it to MariesLib, which saves a
   report even though the game kept running.
3. **Providers.** Small callbacks that add your mod's own information (registry sizes, config
   state, and so on) to every report that involves your mod.

Everything lives in `dev.marie.framework.diagnostics.api`. The API is `@Experimental`: it may change
between MariesLib releases while the framework grows.

---

## 1. Register your mod

Register once, while your mod is loading: in your mod constructor or an `FMLCommonSetupEvent`
handler. Registration closes when mod loading completes. Registering after that throws
`IllegalStateException`, as does registering the same mod id twice.

```java
import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.diagnostics.api.DiagnosticsAPI;

public MyMod(IEventBus modEventBus) {
    DiagnosticsAPI.register(DiagnosticProfile.builder("mymod")
            .packages("com.example.mymod")
            .retention(20)
            .provider("registry", (ctx, section) -> section
                    .entry("foods", MyFoodRegistry.size())
                    .entry("lastReload", MyFoodRegistry.lastReloadTime()))
            .build());
}
```

| Builder method | What it does |
|---|---|
| `builder(modId)` | Your mod id. Must match `[a-z0-9_]{1,64}`; it is also your report folder's name. |
| `packages(...)` | Java packages that belong to your mod. Crash stack frames in these packages (or sub-packages) are counted as your mod's code. If packages of two mods overlap, the longest match wins. |
| `retention(n)` | Most reports kept in your folder (1–200, default **20**). Older ones are deleted. |
| `provider(id, provider)` | Adds a provider. `id` must be unique within your profile and shows up as `mymod:id` in reports. |

You don't need `MarieBootstrap.attach` or a `MarieContext` to use diagnostics; a mod id is enough.

**If you don't register:** `reportFailure` still works and your reports still go to your folder. But
crash reports can't recognise your code in a stack trace, and none of your providers run.

---

## 2. Report failures

Call `reportFailure` wherever you catch something that went wrong. It never throws, so it's safe in
any `catch` block.

```java
import dev.marie.framework.diagnostics.api.FailureCategory;
import dev.marie.framework.diagnostics.api.Severity;

try {
    nutrition = calculator.calculate(stack);
} catch (RuntimeException e) {
    DiagnosticsAPI.reportFailure("mymod", e, f -> f
            .component("NutritionCalculator")
            .subsystem("nutrition")
            .category(FailureCategory.DATA_PROCESSING)
            .severity(Severity.ERROR)
            .context("item", BuiltInRegistries.ITEM.getKey(stack.getItem())));
    nutrition = Nutrition.EMPTY;
}
```

For a problem with no exception (a validation failure, say), pass a description instead. The
report records where you called it from:

```java
DiagnosticsAPI.reportFailure("mymod", "oxygen value " + value + " is outside 0..1", f -> f
        .component("BreathTracker")
        .category(FailureCategory.GAMEPLAY)
        .severity(Severity.WARNING));
```

All the details are optional:

| Detail | Meaning |
|---|---|
| `component(...)` | The class or feature that failed, e.g. `"NutritionCalculator"`. |
| `subsystem(...)` | The larger area it belongs to, e.g. `"nutrition"`. |
| `category(...)` | `DATA_LOADING`, `DATA_PROCESSING`, `REGISTRY`, `NETWORK_SYNC`, `CONFIGURATION`, `COMPATIBILITY`, `RENDERING`, `GAMEPLAY` or `OTHER` (the default). `CRASH` is reserved for Minecraft crashes. |
| `severity(...)` | `WARNING`, `ERROR` (the default) or `FATAL`. |
| `context(key, value)` | Facts that help explain the failure: the item, recipe or file being processed. Up to 32 entries. |
| `runProviders(false)` | Skips providers for this report. Useful for frequent, cheap failures. |

`reportFailure` returns `Optional<String>`: the incident id, or empty if the report was not written.

### Limits

These limits stop a failure that repeats every tick from filling the disk:

- After **3** reports of the same failure (same mod, component, exception type and first stack
  frame) in one session, later copies are only counted and logged, not written.
- At most **50** reported failures are written per session across all mods.
- Minecraft crashes are never limited.

Reports are written on a background thread, so `reportFailure` returns immediately. Reports still
queued when the game closes get up to 2 seconds to finish writing.

---

## 3. Write providers

A provider adds a section of your mod's own information to reports:

```java
DiagnosticProvider registryState = (ctx, section) -> {
    section.entry("registeredFoods", FoodRegistry.size());
    section.entry("datapackFoods", FoodRegistry.datapackCount());
    section.entry("configMultiplier", MyConfig.multiplier());
};
```

`ctx` tells you what the report is about: `incidentId`, `kind` (`MINECRAFT_CRASH` or
`REPORTED_FAILURE`), `reportingMod`, `component` and `category`.

**When providers run:** for each report, MariesLib runs the providers of the mod that reported it,
then of every mod whose code is in the stack trace, then MariesLib's own.

**Rules.** Providers can run while the game is crashing, or while the server thread is frozen (a
watchdog crash). So a provider must:

- **Only read.** Never change game or mod state.
- **Be thread-safe.** It runs on a MariesLib thread, not the game thread. Read volatile or immutable
  data, and don't take locks the game thread might be holding.
- **Be fast.** Each provider gets **250 ms**. After that its section is marked `timed_out` and the
  report is written without it.
- **Stay small.** At most 64 entries, 1024 characters per value, and 8 KB in total. Anything beyond
  that is dropped and the section is marked `truncated`.
- **Leave out personal data.** No player names, UUIDs, IP addresses or chat.

A provider that throws or times out only loses its own section. The error is recorded in the
report's `diagnosticStatus.failed`, and every other provider still runs.

---

## 4. Minecraft crashes

You don't need to do anything for these. When Minecraft saves a crash report:

1. MariesLib adds one line to the **System Details** section of Minecraft's crash report:
   ```
   MariesLib Incident: 3f9a1c2b7d4e (config/mymod/crash-reports/incident-20261124-153012-3f9a1c2b7d4e.json)
   ```
2. After Minecraft's file is saved, MariesLib writes its report. That report names Minecraft's crash
   file in `correlation.minecraftCrashReport`, so you can go either way between the two.

**Which folder the crash goes to:** the first registered mod whose code appears in the stack trace,
starting from the deepest cause, since that's usually closest to what failed. If no registered mod's
code is in the stack, it goes to `config/marieslib/crash-reports/`.

Minecraft's crash report always comes first. If MariesLib fails to write its report, the error is
logged and Minecraft's report is saved exactly as normal.

**Not captured yet:**

- Chunk save/load errors and network protocol errors. Minecraft writes these with different report
  types.
- Failures that never reach a crash report and aren't sent through `reportFailure`.
- A game that is force-killed.
- During a crash while mods are still loading (`crash-…-fml.txt`), information is best-effort, and
  the report says so in `diagnosticStatus.limitations`.

---

## 5. Where reports go and what's in them

```
config/
├── mymod/
│   └── crash-reports/
│       ├── incident-20261124-153012-3f9a1c2b7d4e.json
│       └── incident-20261124-160455-a07be91c55d2.json
└── marieslib/
    └── crash-reports/
```

File names start with the local time, so they sort oldest to newest. Files are written to a
temporary file first and then moved into place, so you never see a half-written report.

Each report is JSON, `schemaVersion: 1`:

| Section | Contents |
|---|---|
| `incident` | `id`, `timestamp`, `kind`, `reportingMod`, `storageMod`, `subsystem`, `component`, `category`, `severity`, `occurrence` |
| `correlation` | `minecraftCrashReport` file name and `reportType` (crashes only) |
| `environment` | Minecraft, NeoForge, FML and Java versions, OS name/arch, side, MariesLib version, every loaded mod with its version |
| `session` | `sessionId` and `modlistHash`: the same launch id LogWatch and LeakWatch use |
| `failure` | `exceptionType`, `message`, `stackTrace`, `causes`, `relevantFrames`, `implicatedMods`, `context` (or `reportSite` for description-only failures) |
| `runtime` | `integratedMods` (every registered profile) and `providers` (each provider's `status`, `durationMs`, `entries`) |
| `analysis` | Reserved for later phases: classification, root cause, suggested fixes. Always empty for now, never guessed. |
| `diagnosticStatus` | `captured`, `failed`, `unavailable` and `limitations`: what worked and what didn't while building this report |

Each entry in `failure.relevantFrames` has the `frame`, the `exception` it came from, the `mod` it belongs to, and `isMariesLib`. That flag is true only for MariesLib's own code, never for a mod's code. A frame shared by an exception and its cause is listed once.

**Who reported it vs. whose code was in the stack.** `incident.reportingMod` is the mod that sent the
report (or `marieslib` for crashes). `failure.implicatedMods` lists mods whose code appeared in the
stack trace. That is evidence, not a verdict: a mod can be in the stack because something else
handed it bad data.

If any section fails to build, it is `null`, the reason is listed in `diagnosticStatus.failed`, and
the rest of the report is still written.

---

## 6. Testing in a dev environment

Dev (non-production) builds of MariesLib register a `marieslib_devtest` profile and two operator
commands:

| Command | Does |
|---|---|
| `/marieslib diagnostics devtest report` | Sends a test failure through `reportFailure`. Writes to `config/marieslib_devtest/crash-reports/`. |
| `/marieslib diagnostics devtest crash` | Crashes the server on its next tick, so you can check the crash companion report and the `MariesLib Incident` line. **This really crashes the game.** |

One of the test profile's two providers always throws, so you can see a failed provider recorded
without affecting the rest of the report.

To check your own integration, throw from your mod's package in a dev world. Then confirm the
companion report lands in your folder and your providers' sections are filled in.

---

## Quick reference

```java
DiagnosticsAPI.register(DiagnosticProfile)                              // during mod loading
DiagnosticsAPI.isRegistered(String modId)
DiagnosticsAPI.reportFailure(String modId, Throwable failure)
DiagnosticsAPI.reportFailure(String modId, Throwable failure, Consumer<FailureSubmission.Builder> details)
DiagnosticsAPI.reportFailure(String modId, String description, Consumer<FailureSubmission.Builder> details)
```
