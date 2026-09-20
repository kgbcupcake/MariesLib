# Changelog

<!-- markdownlint-disable MD013 -->

## [Unreleased]

### Fixed

- `GuiGraphicsRenderContext.pushClip`/`popClip` (marie-ui) left the GL scissor rect clamped for the rest of a frame if a consumer's render code threw between a `pushClip` and its matching `popClip` — e.g. a HUD overlay whose backing data mutates mid-render (Nourished's Nutrient HUD hit exactly this on the food-eaten trigger path, seen as a brief screen-corruption flash). New `resetClip()` unconditionally clears the clip stack and disables scissoring regardless of how many `pushClip` calls are outstanding, for a consumer to call in a `finally` block wrapping its per-frame render pass.
- The actual cause of the full-screen black flashing reported on Nourished's Nutrient HUD while eating (the scissor-clip issue above was a real but separate/insufficient fix): `GuiGraphicsRenderContext.drawText`/`drawItem` (marie-ui) called `PoseStack.pushPose()` followed by `translate`/`scale`/the actual draw call and a bare `popPose()`, with no `try`/`finally`. `GuiGraphics`' `PoseStack` is the same shared instance every `RenderGuiEvent.Post` subscriber draws through in a frame, and `GuiGraphics.fill()` applies whatever pose transform is currently active to its quad. If `graphics.renderItem(...)`/`graphics.drawString(...)` threw partway through — plausible for a nutrient icon resolved mid-sync while values update rapidly, i.e. exactly while eating — the matching `popPose()` never ran, leaving that `translate`+`scale` baked into the shared `PoseStack` for every remaining fill/text/item draw call for the rest of the frame; a small bar/icon fill elsewhere could then render stretched into a full-screen quad, self-healing on the next successful draw and re-corrupting on the next throw, producing the reported rapid strobing. Both methods now wrap their transform + draw call in `try { ... } finally { pose.popPose(); }`. Nourished's classic (pre-MarieUI) HUD renderer had the identical unguarded pattern in `HudDrawHelpers.renderIcon`/`drawScaledLabel`, fixed the same way.

### Changed

- The shared "Appearance" tab label (`config.marieslib.moduleoptions.tab.appearance`) is now "Style": at four tabs in the 212 px strip "Appearance" (48 px at the tab text scale) exceeded the 46 px a label gets and rendered as "Appearan..". New `config.marieslib.colorpicker.reset` lang key.
- `ScaleConfigPanel` no longer has its own hand-drawn slider/toggle rows: every entry's window now hosts content, and an entry without caller-supplied content gets a default Text Scale / Padding / Move Text and Icons panel built from the same toolbox widgets (`SliderOption`/`ToggleOption`) a custom panel uses, so there is one copy of that logic. Visible differences for such entries: Text Scale and Padding preview live while dragged instead of only applying on release, value text is right-aligned by measured width, the rows use the toolbox's cyan rather than the entry's accent color (the header and tab keep it), and the window can be shrunk with the rows scrolling, like any hosted window. Stored keys and formats are unchanged. The class also shed its stacking and window-state logic into package-private `AnchorStack` and `WindowStates` (no behavior change), taking `ScaleConfigPanel` from 636 to 315 lines.

### Added

- `AutoGrowPanelContainer.leftMarginAfterResize(persistedMargin, widthBefore, widthAfter, leftEdgeGesture)` and `DraggableResizable.isLeftEdgeGestureActive()` (marie-ui) — one shared rule for the dead space a panel keeps left of its content after a left-edge/bottom-left-corner resize, so content stays put on screen like a right-edge resize instead of sliding with the moving edge. Any other gesture leaves the margin unchanged; it may go negative, so shrinking from the left past it keeps clipping content instead of dragging it along. New `AutoGrowPanelContainer.MIN_COLLAPSED_SIZE` (16 px) is the floor for collapsing such a box. Consumers persist it in `ComponentState.leftMargin` and add it to their content origin; use `isLeftEdgeGestureActive()` for the live preview and `lastCommitWasLeftEdge()` at commit.

- Color editing for the toolbox. `MarieToolbox.PanelBuilder.colorTab("Label").color("Slot label", intGetter, intSetter, defaultValue, onCommit)` (`@ApiStatus.Experimental`, safe to call in a loop for slots only known at runtime) adds a tab of swatch rows (`toolbox.colorpicker.ColorSlotRow`: live swatch, label, `#RRGGBB`). Clicking a row opens one separate, draggable, resizable **picker window** (`scaleconfig.colorpicker.PickerWindow`) — a sibling of the module windows, drawn at panel level so the module body's clip does not apply — holding `toolbox.colorpicker.ColorPicker`: a hue ring plus saturation/brightness square built from `fillRect` cells (about 1,200-1,400 fills per frame while open; cell size grows with the wheel), a swatch with a read-only `#RRGGBB` readout (`MarieComponent` has no `charTyped`), and a Reset button. Everything is laid out from the window's bounds, so it scales proportionally when resized and never clips. Only one picker exists per panel; clicking another slot retargets it (keeping its size and position, retitling it "slot - module"). It closes when its owner module is collapsed or another module opens, and when the host stops rendering the panel for 500 ms; reopening the owner does not restore it. `ScaleConfigPanel` gained forwarding hooks only (a listener wiring loop, `beginFrame`/`render`, and the four mouse handlers). The picker is RGB only: the setter is called live on every drag tick where the value changes and receives `0xRRGGBB`; **when the stored int is ARGB the setter must preserve its existing alpha bits** (alpha stays on opacity sliders); `onCommit` runs once on release and after Reset (which first calls the setter with the default). The toolbox stores nothing and knows no color storage. Internal pieces (all color code lives in the `colorpicker` subpackages of `toolbox` and `scaleconfig`): `toolbox.colorpicker.ColorSlot`, `ColorSlotRow`, `ColorPicker`, `HexColors`; `OptionLayout.addColorSlot`/`setColorSlotListener` (the host's "slot clicked" callback; without one a click does nothing).
- `CommandCenterCard` now holds its accent as an `IntSupplier` (`accent`), read each time the card is drawn (`accentColor()` still returns the current value), so a consumer can pass a supplier that resolves a user-editable color instead of a color fixed at registration. The constructor taking an `int` accent is unchanged and wraps it in a constant supplier.
- `ColorSlot` (and `MarieToolbox.PanelBuilder.color(..., onCancel)`) gained an optional `onCancel` `Runnable`: the picker runs it when it is closed (close button, owner collapsed or replaced, or no render for 500 ms) or retargeted to another slot, so a caller whose setter only previews a value can drop an uncommitted preview. It may run with nothing pending, so it must be safe then. The five-argument `color(...)` is unchanged.
- `MarieModuleSettings.StandardPanelBuilder.extraTabs(Consumer<MarieToolbox.PanelBuilder>)` appends caller-defined tabs (e.g. a `colorTab`) after the standard Layout / Behavior / Style tabs, and a shared `config.marieslib.moduleoptions.tab.colors` ("Colors") lang key.
- `scaleconfig.colorpicker.PickerLayer` (`@ApiStatus.Internal`) — an opt-in top layer for a host that draws several `ScaleConfigPanel`s in one frame: `begin()` before its targets render and `flush()` after draws every picker last (so a later panel's content or tabs cannot paint over it), and its mouse methods let the host offer input to pickers first. `EditOverlayScreen` now does this around its targets. Hosts that never call `begin()` are unaffected.
- `toolbox.colorpicker.HexColors` (`@ApiStatus.Internal`) — `parseStrictRgbHex`, `formatRgbHex`, `hexInputFilter`, relocated from `ColorHexRowWidget`, which now delegates to them (behavior unchanged).

- `MarieModuleSettings` gains an optional **Move Header** mode for a module whose title is separate from its body text: `MoveDrag.Mode.HEADER`, `headerOffsetX/Y`, `setHeaderOffset`/`commitHeaderOffset`, `isMoveHeaderEnabled`, and `StandardPanelBuilder.withHeader()` (Move Text then moves only the body); `StandardPanelBuilder.withoutIcons()` leaves out Move Icons for a module that draws none. Reset Positions clears the header offset too.

- `MarieModuleSettings` (`dev.marie.framework.ui.api`, marie-ui, `@ApiStatus.Experimental`) — a facade for the display settings a HUD-style module box exposes, extracted from Nourished so any consumer can reuse it (implementation in the new `dev.marie.framework.ui.modulesettings` package plus `toolbox.ModuleOptionRows`, all `@ApiStatus.Internal`). Read side, all over a caller-supplied `PersistenceProvider` and `panelId`: `textScale`/`iconScale` (independent text and icon size; icon size follows text size until first set, and the first text-size edit pins it, so modules that predate the split look unchanged), `barOffsetX/Y` + `setBarOffset`/`commitBarOffset` (a "move bars" offset kept in memory for per-frame reads and written once per drag), `isMoveBarsEnabled`/`isMoveIconsEnabled`/`isMoveTextEnabled`/`activeMoveMode`, `barScale` (a Bar size multiplier for the bar and the value text at its end), `iconOffsetX/Y` + `setIconOffset`/`commitIconOffset` (a "move icons" offset alongside the bar one), `MarieToolbox.PanelBuilder.resetTab()` + `defaultValue(double|boolean|int)` (a "Reset This Tab" button that puts every option on the current tab that has a default back to it — the shared module rows come with their defaults, and a caller sets its own with `defaultValue`, e.g. `StandardPanelBuilder.opacity(getter, setter, default)`; icon size resets by forgetting its separate value so it follows the text size again; `standardPanel` now puts one on its Layout and Appearance tabs, next to Behavior's Reset Positions), `ModuleRenderContext` now also applies the bar offset and Bar size to any `drawBar`/`drawVerticalBar` the module makes, `MarieToolbox.PanelBuilder.resetPositions(store, panelId, hostReset)` and `StandardPanelBuilder.onReset(...)` (a "Reset Positions" button — a new one-shot `ButtonOption` row — that zeroes the icon and bar offsets, switches every move mode off, and runs a host callback for anything the host stores itself, such as its text offset), `withDisplaySettings(context, store, panelId)` (a `RenderContext` wrapper, `modulesettings.ModuleRenderContext`, that applies a module's text/icon offsets, its icon size relative to its text size, and its stored text/icon brightness to everything the module draws — for a renderer that already scales text and icons together and draws through a `RenderContext`, no per-draw edits needed), `textBrightness`/`iconBrightness` and `textOffsetX/Y` + `setTextOffset`/`commitTextOffset` (values a module keeps in its own store instead of a config file or its own key), `StandardPanelBuilder.withoutBars()`/`storedBrightness()` and `PanelBuilder.moveToggles(store, id, bars)`/`storedBrightness(...)` (the same panel for a module with no bars and store-backed brightness), `isMoveAllEnabled` (a "Move All" toggle that drags text, icons and bars together), `MoveDrag` (the grab-offset/mode state for a host's move-drag modes — `MoveDrag.Mode` TEXT, ICONS, BARS and ALL, with `startAll`/`baseX`/`baseY` for the combined drag — so mouse handlers don't each re-implement it), and `withBrightness(context, text, icon)`/`scaleBrightness(argb, b)` (a `RenderContext` decorator that scales `drawText` colors and tints `drawItem` icons independently, and returns the context untouched at 1.0/1.0; `MIN_BRIGHTNESS` 0.2 to `MAX_BRIGHTNESS` 2.0). Edit side: `standardPanel(title, store, panelId)` builds a ready-made Layout (Padding) / Behavior (Move Text, Move Icons, Move Bars, Move All — mutually exclusive) / Appearance (Text size, Icon size, Bar size, plus whichever of text brightness, icon brightness and background opacity you bind through getters/setters, committed through `onCommit`) options panel. `MarieToolbox.PanelBuilder` gained `padding`, `textAndIconSizes`, `barSize` and `moveToggles` so a consumer can compose those same rows into its own tabs. New `config.marieslib.moduleoptions.*` lang keys (marie-core resources) carry the labels.
- `RenderContext.drawRoundedRect(x, y, width, height, thickness, radius, fillColor, borderColor)` (marie-ui) — a rounded rectangle with a real corner radius: each corner is a stepped quarter circle drawn one pixel row at a time with `fillRect` (radius 4 cuts 2, 1, 1, 0 pixels from the first four rows), replacing the fixed one-pixel notch of the existing radius-less overload for callers that opt in (that overload is unchanged). The border is the `thickness`-pixel band between the outer shape and the same shape inset by `thickness`, and border and fill never overlap, so translucent colors don't double-blend along the edge. Radius and thickness clamp to half the shorter side. Implemented in the new `@ApiStatus.Internal` `dev.marie.framework.ui.render.RoundedRects`.
- `MarieToolbox` (`dev.marie.framework.ui.api`, marie-ui, `@ApiStatus.Experimental`) — a facade for tabbed option panels: `MarieToolbox.panel(title).tab(..).slider(..)/.toggle(..)/.cycle(..)[.enabledWhen(..)].build()` returns a plain `MarieComponent` for a module box to host. Each option edits a caller-owned value through a getter/setter (primitive `DoubleSupplier`/`DoubleConsumer`, `BooleanSupplier` + new `BooleanSetter`, `IntSupplier`/`IntConsumer`) and reports each finished edit through a caller-supplied `onCommit` — the toolbox stores and persists nothing. Sliders take the caller's `min`/`max`/`step` (values snap to `min + n*step`, never leave the range), display as a percentage, and call the setter live on every drag tick that changes the snapped value (so the edited thing previews while dragging) with `onCommit` once on release (a scroll notch is setter + `onCommit`); toggles and cycle buttons commit on click. `enabledWhen(BooleanSupplier)` greys out and disables the option just added. The implementation lives in the new `dev.marie.framework.ui.toolbox` package (`OptionLayout`, `TabRow`, `SliderOption`, `ToggleOption`, `CycleOption`, `OptionRow`, `OptionStyle`, all `@ApiStatus.Internal`; consumers use only the facade). Tab labels truncate with an ellipsis to fit their tab (measured with `RenderContext.textWidth`), the panel's preferred height is the tallest tab's so a host never resizes on tab change, and a tab taller than its box clips and scrolls with the mouse wheel (wheel anywhere over the rows while they overflow, with a thin scroll thumb on the right edge; while everything fits, the wheel nudges the slider under the cursor).
- `MarieScaleConfig.isMoveContentEnabled(PersistenceProvider, String)`/`setMoveContentEnabled(PersistenceProvider, String, boolean)` expose the "Move Text and Icons" flag for code that has the store but no `ScaleConfigPanel` instance — backed by the same `modulesettings.MoveFlags` the panel and the toolbox's move toggles use, so the stored key and format are unchanged. `ScaleConfigEntry` gained an optional `content` (`MarieComponent`) via `withContent(...)`; existing constructors are unchanged. An entry with content shows it in its open window, below the header, in place of the default Text Scale/Padding/Move Text and Icons rows an entry without content gets, and the window first opens at the content's preferred height (capped at the window maximum) but is not held to it: it can be resized down to the normal window minimum and the content scrolls. Hosting/forwarding lives in the new package-private `HostedWindow`; `ScaleConfigPanel` only gained a handful of hook calls into it.
- `ValueDefinition.icon`/`Builder.icon(String)` (marie-core) — optional nullable icon identifier for a value, mirroring `tooltipColor`'s field/getter/builder shape exactly. Defaults to `null`; consumers already have their own fallback-icon logic downstream, so no default icon string is invented here.
- `ValueDefinition.tags`/`Builder.tags(List<String>)` (marie-core) — optional food-tag list for a value, same shape as `icon`. Defaults to an empty list; `Builder.tags(...)` defensively copies the supplied list via `List.copyOf` rather than storing the caller's reference, matching `NutrientDef.tags()`'s immutability contract elsewhere in the codebase.
- `TooltipMessageRegistry.registerExternal(String, String, String)`/`removeExternal(String, String)` (marie-ui) — a runtime override tier, keyed the same way (`modId`, then key) as the class's existing config/datapack tiers, for scripts (e.g. KubeJS) to set a tooltip message at runtime. Checked first in both `get()` and `getForItem()`, ahead of the datapack tier. Unlike the config/datapack tiers, runtime entries are not cleared by `reload()`/`loadFromDatapack()`, so they survive config/datapack reloads; repeat `registerExternal` calls for the same key overwrite rather than throw, since a script resetting its own tooltip on a later run isn't an error case.
- `RenderContext.drawGlow(int, int, int, int, int)` (marie-ui) — a soft highlight ring around a rectangle, distinct from `drawBorder`'s flat single-pixel border, for edit-mode-only highlighting of nested sub-boxes (marking a component as draggable/movable without implying a resize handle). New `ThemeKey.SUBBOX_GLOW` (`DarkTheme` `0xFFE8B84B`, `LightTheme` `0xFFB8860B`) supplies its color. The sole `RenderContext` implementation, `GuiGraphicsRenderContext`, implements it as 4 concentric `drawBorder`-style frames, each one pixel further outside the given bounds than the last, with the color's alpha scaled to roughly 100%/65%/35%/15% of the original across the 4 rings.
- `ContentModule` (`dev.marie.framework.ui.edit`, marie-ui) — the one nested, independently draggable/resizable content region a `MarieComponent` may expose inside its own main box (one module per component, not a general child-component tree). Composed the same way `DraggableResizable` itself is composed into a component — internally holds a `DraggableResizable` clamped to the owner's current bounds via `setParentBounds` — and persists its position as an offset relative to the owner's own bounds (not an absolute screen position) under the owner's `#module`-suffixed `ComponentState`, mirroring `ScaleConfigPanel`'s existing `#window`-suffixed sub-state convention; relative storage means the module rides along automatically when the owner itself is dragged/resized. Purely a layout/editing primitive: it never renders anything itself, leaving content drawing and any edit-mode affordance (e.g. `drawGlow`/`drawResizeHandle`) to the owning component's own `render()`. Also gained `setSize(int, int)`, for a module with no user-facing resize handles whose size the owner computes fresh every frame instead of relying on a committed resize gesture. Nourished's `CalorieHudScreen` tried this for its row content, then dropped it in favor of a toggle + plain offset (that panel's content fills essentially its whole interior, so a separately hit-tested sub-region for it always competed with the panel's own drag) — **this primitive currently has no consumer in either codebase**, kept as a documented public primitive (same precedent as `BoxFitScale` above) for a future component whose content genuinely occupies only part of its owner's box, where the competing-hitbox problem doesn't apply.
- `ScaleConfigPanel` gained a third row, "Move Text and Icons", under each entry's existing Text Scale/Padding sliders — a plain on/off toggle (no track/scrub, a single click flips it) rather than a slider, for a host panel to let its content be dragged as a translated whole separately from the panel itself. State is owned by `ScaleConfigPanel` itself (not the host panel) under a new `componentId + "#moveContent"` persistence key, reusing `ComponentState.collapsed()` as a generic boolean flag for that dedicated key (mirroring `#window`'s reuse of the whole record for unrelated per-key state) — exposed via a new public `isMoveContentEnabled(String componentId)` getter a host panel polls each frame. `CARD_HEIGHT` grew by one row block to fit it; already-open/resized windows self-heal to the new minimum on next render via the existing `clampWindowBounds` clamp, no migration needed. New `config.marieslib.scaleconfig.moveContent` lang key ("Move Text and Icons").

- Toolbox additions for whole-number options and one-shot actions. `MarieToolbox.PanelBuilder.intSlider(label, IntSupplier, IntConsumer, int min, int max, int step, String unit, Runnable onCommit)` is a slider whose value reads e.g. "60 px" (`unit` is the suffix) and whose setter always receives an int; it is `SliderOption.ofInt`, which shares the existing slider's live-setter, commit-on-release and scroll behavior. `MarieToolbox.PanelBuilder.button(label, caption, Runnable action, Runnable onCommit)` exposes the existing one-shot `ButtonOption` row (a click runs `action`, then `onCommit`; "Reset This Tab" leaves it alone). `MarieModuleSettings.StandardPanelBuilder.styleRows(Consumer<MarieToolbox.PanelBuilder>)` lets a caller add rows to the end of the Style tab, before its "Reset This Tab" button, so a row given a `defaultValue` is reset with the tab (`extraTabs` only runs after that button). Nothing stored or changed for existing callers.

## [MariesLib 0.1.2-beta] — 2026-09-17

### Added

- `BoxFitScale` (`dev.marie.framework.ui.api`, marie-ui) — a small public utility for the "shrink content together to fit a box smaller than its natural size" pattern, deliberately the opposite of `ContentScaleController`'s documented contract (content is driven by the user's own persisted adjustment alone, never by box size — overflow gets clipped, not shrunk). `BoxFitScale.of(boxWidth, boxHeight, naturalWidth, naturalHeight, minScale)` (plus a `Bounds`/`Size`-typed overload) returns `1.0` once the box is at or above natural size on both axes, scaling down toward `minScale` as it shrinks below that — a caller multiplies the result into its own content-scale factor alongside `ContentScaleController.resolveContentScale`, rather than in place of it. Small enough to live directly in `dev.marie.framework.ui.api` as its own utility, same as `SnapRegistry`, rather than needing a separate subsystem-package implementation behind a facade. Originally extracted from duplicated logic in two of Nourished's HUD panels (Activity Log, Calorie History); those panels were reverted back to fixed-natural-size content (matching the Nutrient HUD's own pinned-content behavior) before shipping, so **this utility currently has no consumer in either codebase** — kept as a deliberate, documented public primitive for whichever future component actually wants box-driven shrinking instead of pin-and-clip/scroll.

### Changed

- `ScaleConfigPanel`'s always-open fixed-size cards are now collapsed-by-default tabs that expand into a single draggable/resizable window, one entry open at a time
    - Each `ScaleConfigEntry`'s open/closed state and (when open) its screen position/size are persisted through the same `PersistenceProvider` field the panel already held, reusing the existing (previously-unused) `collapsed`/`x`/`y`/`width`/`height` fields on `ComponentState` under a new `<componentId>#window` key — no new record, and `MarieScaleConfig.create(...)`'s signature is unchanged. An entry with no `#window` state renders collapsed, matching "nothing open by default"; the first time an entry opens with no prior window state it gets today's card size and a position anchored near the panel instead of `(0, 0)`.
    - Collapsed entries render as a small `TAB_HEIGHT`-tall tab, stacked at the panel's anchor the same way the old fixed cards were. Clicking a tab opens that entry's window (using its persisted bounds, or the default on first open) and force-collapses any other entry that was open; the open window renders the same header/two-slider-row content as before but at its own persisted bounds, with a small header control to re-collapse it. If a hand-edited save somehow has more than one entry marked open, only the first one found is honored — the rest render as tabs instead of a second window.
    - The open window's header is drag-repositionable and its bottom-right corner is resizable, hand-rolled the same way this class already hand-rolls its slider interactions (no `mouseDragged`/`mouseReleased` existed on this class before), sized via `DraggableResizable`'s existing `RESIZE_HANDLE_SIZE` handle geometry. Both gestures clamp to the `Bounds` passed into `render()` and to new `MIN_WINDOW_WIDTH`/`MIN_WINDOW_HEIGHT`/`MAX_WINDOW_WIDTH`/`MAX_WINDOW_HEIGHT` constants. Existing slider click/scroll behavior for Text Scale/Padding is unchanged, just relative to the window's now-variable bounds.
    - Host screens forwarding this panel's input must now also forward `mouseDragged`/`mouseReleased` (documented on `MarieScaleConfig`'s usage example) for the drag/resize to work; `mouseClicked`/`mouseScrolled` keep their existing return-`false`-when-outside contract.
- The open window's header no longer shows the combined-percentage status pill (average of the Text Scale/Padding percentages) — it was redundant once the window is open, since both sliders' own live percentages are already visible right below it; the `combinedPercent`/`drawPill` helpers and `PILL_WIDTH`/`PILL_HEIGHT` constants are removed as unused. Collapsed tabs never showed the pill to begin with.

### Fixed

- `ScaleConfigPanel`'s "one window open at a time" only held within a single panel instance's own entries, not across sibling instances — a consumer mod that constructs one panel per HUD widget (Nourished's Nutrient HUD/Calorie History/Activity Log panels each have their own `ScaleConfigPanel`, same as its existing cross-instance tab-stacking at a shared anchor) could still end up with multiple windows open simultaneously, one per instance, since `openEntry`'s force-collapse loop only walked that instance's own `entries` list and had no way to see or touch another instance's state. New static `KNOWN_WINDOW_COMPONENT_IDS` set (same accumulate-and-never-prune shape as the existing `VISIBLE_CLAIMS` anchor-stacking registry) records every componentId any panel instance has rendered a tab/window for; `openEntry` now force-collapses over that shared set instead of `entries`, so opening any entry's window — in any panel instance — collapses whichever other entry was open, wherever it lives.
- Collapsed tabs and the open window are now larger and easier to read/hit: `TAB_HEIGHT` 14 → 18, `CARD_WIDTH` 200 → 224, and the window's header/row/slider dimensions bumped proportionally (`CARD_HEIGHT` grows automatically from those).
- `ScaleConfigPanel`'s Text Scale/Padding sliders lost drag-to-scrub when the collapsed-tabs/window rework landed — `mouseClicked` jumped the value to the clicked position but nothing continued adjusting it while the button stayed held and the cursor moved, so holding and dragging the slider (as opposed to repeated clicks) silently did nothing. `mouseClicked` now also arms a slider-scrub gesture (tracked via new `draggingSliderComponentId`/`draggingSliderIsPadding`/`draggingSliderTrack` fields), `mouseDragged` continues recomputing the value from the live mouse x each frame, and `mouseReleased` ends it.
- The open window's default position (first time an entry is opened with no prior persisted window state) is no longer placed directly on top of the still-stacked collapsed tabs of the panel's other entries. It previously anchored at the same corner point the tab stack itself starts from; since the window (card-sized) is far taller than a single tab, it rendered over whichever other entries' tabs were stacked there. `defaultWindowBounds()` now offsets from the currently-rendered tab stack's actual bounds (`lastPanelBounds`) — below it for top/center-anchored stacks, above it for bottom-anchored ones, which grow upward from the screen edge.
- `ScaleConfigPanel.mouseReleased` now returns `boolean` instead of `void`, matching `mouseClicked`/`mouseDragged`/`mouseScrolled` on the same class and every host screen's `if (scaleConfigPanel.mouseReleased(...))`-style forwarding introduced alongside the collapsed-tabs/draggable-window feature above. It now also finalizes the in-progress header-drag or corner-resize gesture (if any) by persisting its final bounds the same way `mouseDragged`'s own preview does, then clears the gesture state and returns `true`; returns `false` (uncontested) when no gesture was active.
- `ScaleConfigPanel`'s Text Scale/Padding sliders looked dead unless the initial press landed exactly inside the drawn 7px-tall track strip — `handleWindowClick` only started a scrub gesture on a hit against `textTrack()`/`paddingTrack()` directly, not the row (label included) the track sits in, so a press anywhere else in that row (very easy to land on) registered nothing and `mouseDragged` found no active gesture. `layoutWindow` now also computes `textRowHit`/`paddingRowHit` (new `WindowLayout` fields spanning from the row's label down through the bottom of its track, same x/width as the track), and both `handleWindowClick` and `mouseScrolled` hit-test against those instead of the bare track; `valueAt`'s horizontal mapping still uses the actual track bounds, unaffected.
- The corner resize-handle hit-test ran before the slider hit-tests in `handleWindowClick`, and its hitbox overlaps the right end of the Padding row on a short/small window, so a press in that overlap started a window resize instead of moving the slider. Slider row hits are now checked first — the resize handle only sees whatever the sliders didn't claim.
- `mouseDragged` persisted the scrubbed value to the `PersistenceProvider` on every single drag frame while a slider was held, doing a full synchronous save/file-rewrite continuously and causing visible stutter while scrubbing. Dragging now only updates a new in-memory `draggingSliderLiveValue` field (read by `drawWindow` so the slider still visually tracks the cursor live); `mouseReleased` is the sole point that actually persists it, exactly once per gesture — a plain click-and-release with no movement still persists correctly, since `mouseReleased` fires either way.

## [MariesLib 0.1.1-beta.5] — 2026-07-26

Two new generic, consumer-agnostic primitives: client-side foreign-screen detection by menu-type registry name, and a generic block-scoped state sync packet from client to server.

### Added

- `ForeignScreenDetector` (`dev.marie.framework.ui`, marie-ui)
    - Lets a consuming mod register a `(ResourceLocation menuTypeId, Consumer<Screen> callback)` pair and get called back whenever a `ScreenEvent.Opening` screen's menu type matches, by registry name only
    - Never references any foreign mod's screen/menu class — matches purely via `BuiltInRegistries.MENU.getKey(...)` read off the opened menu
    - Lazily subscribes to `NeoForge.EVENT_BUS` on first `registerInterest` call
- `GenericStateSyncPayload` (`dev.marie.framework.network`, marie-core)
    - `CustomPacketPayload` carrying a `BlockPos` plus an opaque `CompoundTag`, for a consuming mod to sync small block-scoped state to the server without defining its own payload type or channel
    - `sendToServer(BlockPos, CompoundTag)` for client-side callers
- `MarieAPI.registerGenericStateSyncHandler(BiConsumer<ServerPlayer, GenericStateSyncPayload>)`
    - Registers a server-side handler for inbound `GenericStateSyncPayload`s, gated by `MarieAPIState.assertRegistrationAllowed` like the rest of `MarieAPI`'s registration surface
    - `MarieNetworking` registers the payload type via `RegisterPayloadHandlersEvent` and dispatches received payloads to all registered handlers
- Optional `calories` field on `source_classifications.json` entries (marie-core), following the same convention as `food_overrides.json`'s `calories` (`int`, default `0` / absent = "no explicit calorie override"). `SourceClassificationRegistry.parseEntry` reads it (taking precedence over the legacy `total` field when both are present) and folds it into `SourceClassification.total()`; the raw value is also retained on the new `SourceClassification.calories()` record component so `writeRegistry` round-trips it under its own key (`calories` emitted when non-zero, else the legacy `total`). `setOverride` gained a `(String, Map, int calories, boolean)` overload — the old 3-arg form delegates with `calories = 0`. KubeJS `MarieKubeBindings.registerSourceOverride` accepts a `calories` key in its map. Precedence in `SourceApplicationPipeline`: an explicit per-entry `calories` still loses to a non-zero `resolverDelta.total()` (the item's existing value) but beats an implicit/default `0` — see Fixed.
- `RecipeInheritanceResolver.collectContributions(ResourceLocation rootItemId, Function<ResourceLocation, T> nodeClassifier)` (marie-core) — a new public, generic graph-walk that visits **every** recipe-ingredient node below `rootItemId` up to `MAX_DEPTH` and invokes the caller's classifier per node, collecting all non-null results into a `List<NodeContribution<T>>` (new `public record NodeContribution<T>(ResourceLocation nodeId, int depth, T value)`, `depth` 0 = direct ingredient of the root).
    - **Why:** the existing `resolve(...)` / `resolveRecursive` path stops descending a branch the moment a node classifies confidently (non-null, non-uncertain). That short-circuit is the confirmed root cause of the "calzone" bug class — a weak keyword-fallback match on an intermediate node (a generic dough / bread ingredient) ended the branch, so a stronger tag-based match exactly one hop further down (the real filling) was never visited and never contributed. `collectContributions` never short-circuits; the caller sees the full picture and chooses the strongest signal itself.
    - Reuses the already-built recipe index via `getIngredients(itemId)` — `buildIndex()` is neither called nor duplicated. Same `depth >= MAX_DEPTH` gate as `resolveRecursive`.
    - Single global visited guard (`Map<ResourceLocation, Boolean>`, the same pattern as `resolveRecursive`'s cycle guard), checked before the classifier runs and marked immediately after: one mechanism covers duplicate ingredients within a list and the same node reachable through multiple branches — each distinct node is classified exactly once, at the shallowest depth it is reached. The root is seeded as visited so it is never classified (caller's responsibility, matching `resolveRecursive`).
    - A `null` classifier result means the node is still walked through (its own ingredients are visited, subject to depth/visited) but not recorded.
- `AbstractRegistry.unregister(K key)` (marie-core) — removes a single entry by key without touching the rest of the registry, throwing `IllegalStateException` if the registry is frozen (same guard shape as `register`/`registerUnlocked`) and returning whether an entry was actually present and removed. Previously the only bulk-removal path was `reset()` (clears everything); this is a new, separate, targeted path alongside it and is inherited by every `AbstractRegistry` subclass (`MilestoneRegistry`, `TrackerMilestoneRegistry`, `TrackerRegistry`, etc.) with no per-registry override needed.
- Tracker milestone system (marie-core) — structurally parallel to the existing nutrient/value `MilestoneDefinition`/`MilestoneRegistry`/`MilestoneTracker` trio, but tracks generic MarieLib tracker values (`MarieTracking.incrementTracker`/`getCurrentTrackerValue`) instead, and is fully decoupled from the nutrient system: separate storage, separate event, no shared feature flag.
    - `dev.marie.framework.api.progression.TrackerMilestoneDefinition` (+ `Builder`) — `id`, `trackerId` (`ResourceLocation`), `goal` (float), `scope` (new `MilestoneScope` enum: `LIFETIME` vs `CURRENT_PERIOD`), plus the same reward plumbing `MilestoneDefinition` has (`rewardEffectId`/`rewardAmplifier`/`rewardDuration`/`advancementId`).
    - `dev.marie.framework.api.registry.TrackerMilestoneRegistry` — same `AbstractRegistry` register/freeze/reset pattern as `MilestoneRegistry`, with `getForTracker(ResourceLocation)` in place of `getForValue`/`getForAll` (no "all trackers" cross-check variant); wired into `MarieApiRegistries`' datapack reset/freeze cycle alongside `MilestoneRegistry`.
    - `dev.marie.framework.tracking.TrackerMilestoneProgressData`/`TrackerMilestoneProgressAttachment` — new per-player attachment (`tracker_milestone_progress`) holding lifetime cumulative-by-tracker-id and completed-milestone-ids, entirely separate from `MilestoneProgressAttachment`'s storage.
    - `dev.marie.framework.tracking.TrackerMilestoneTracker.onTrackerIncremented(ServerPlayer, ResourceLocation, float)` — sibling call site alongside `MarieTracking.incrementTracker`, not wired inside it; always accumulates into the lifetime counter, then checks each matching not-yet-completed milestone against the lifetime counter or `MarieTracking.getCurrentTrackerValue` depending on its `scope`, applying reward/advancement and posting `MarieEvents.TrackerMilestoneTriggeredEvent` on completion.
    - Datapack support mirrors nutrient milestones exactly: `MarieDataLoader.Callbacks.registerTrackerMilestone`, a new `tracker_milestones/<id>.json` schema (`SchemaDefinition.forTrackerMilestone`, swapping `value_key` for `tracker_id` and adding the required `scope` field), `MarieDataLoader.parseTrackerMilestone`, and `loadedTrackerMilestones`/`getLoadedTrackerMilestones()` — all wired into the same reload pass nutrient milestones already use.
    - KubeJS bridging (marie-commands), mirroring `MarieMilestoneTriggeredEvent`/`MILESTONE_TRIGGERED` exactly: `dev.marie.framework.kubejs.events.MarieTrackerMilestoneTriggeredEvent` wrapper, `MarieKubeEvents.TRACKER_MILESTONE_TRIGGERED`/`TRACKER_MILESTONE_TRIGGERED_ID`, and `KubeEventBridge.onTrackerMilestoneTriggered`, registered in `register()` and gated by `KubeGuard.hasListeners(...)` the same way every other handler is.
- `dev.marie.framework.ui.edit` (marie-ui) — `EditableComponent` (functional-style `enterEditMode`/`exitEditMode`/`isEditModeActive`, implementable directly or via a lambda-backed adapter for retrofitting an existing class) and `EditModeCoordinator`, a static registry that lets any consumer mod register a component under an id and toggle every registered instance together as a group (`register`/`unregister`, `toggleAll`, plus explicit `enterAll`/`exitAll`) — domain-agnostic, no knowledge of what's actually being edited. Backed by a plain `LinkedHashMap`, not `AbstractRegistry`: unlike `CommandCenterRegistry`'s boot-time category/card definitions, components here register and unregister continuously through gameplay (as HUDs/screens are created/destroyed), which doesn't fit `AbstractRegistry`'s register-then-freeze lifecycle (no removal support) or the `MarieAPIState.assertRegistrationAllowed` mod-init gate.
- `SnapRegistry` (`dev.marie.framework.ui.api`, marie-ui) — a static, bounds-only registry (`register(String id, Supplier<Bounds> boundsSupplier)`/`unregister`, same `LinkedHashMap` shape and no-lifecycle contract as `EditModeCoordinator`'s registry) so consumer mods can register their own draggable components' bounds and have other `DraggableResizable` instances — including ones owned by a different mod — snap to their edges. `computeSnapLines(String excludeId)` iterates every registrant except the caller's own id, converts each `Bounds` into left/right edge candidates for X and top/bottom edge candidates for Y (no center lines — `DraggableResizable`'s existing snap math doesn't have that concept), and returns them as a `SnapLines(xLines, yLines)` record in the shape `DraggableResizable.setSnapTargets` takes. `DraggableResizable` gained `setSnapRegistryId(String)`: when set, every `mouseDragged` call recomputes and re-applies snap targets from the registry (excluding its own id) before processing the drag, so a tracker opted into registry-driven snapping needs no other per-frame wiring — the actual snap-within-threshold math is unchanged, still governed by that tracker's own `snapThresholdPx` (`DEFAULT_SNAP_THRESHOLD_PX` by default); `SnapRegistry` introduces no threshold constant of its own.
- `ContentScaleController` (`dev.marie.framework.ui.edit`, marie-ui) — generalizes Nourished's hand-rolled Diet Screen zoom pattern (`DietZoomController`) into a reusable, keyed-by-component-id primitive that lets a user fine-tune a component's *existing* box-driven proportional content scale (`Math.min(widthScale, heightScale)`) and padding, without decoupling either from the box:
    - Double-left-click inside a component toggles text/content-scale adjustment mode for it; double-right-click toggles padding adjustment mode. Only one component/mode pair is active at a time — entering one implicitly exits whichever other was active, same contract as `DietZoomController`. Reuses `DoubleClickRecognizer` and the same stale-bounds-on-second-click handling.
    - While a mode is active, scroll adjusts that component's persisted `ComponentState.contentScale`/`paddingScale` multiplier by a fixed step, clamped to a wide storage range; `resolveContentScale(userAdjustment)`/`resolvePadding(userPaddingAmount)` return the user's persisted adjustment as-is, only sanity-clamped against degenerate values — not against the caller's own box-driven scale. Text size is always exactly the user's persisted adjustment; if the box is too small to fit it, the content overflows and is cut off by the caller's own clip region rather than being shrunk to fit.
    - `ComponentState` gained a `paddingScale` field (default `1.0`), persisted by `MarieConfigPersistenceProvider` the same way as `contentScale`; existing 8-/9-arg constructors are preserved for source compatibility.
- `dev.marie.framework.ui.scaleconfig` (marie-ui) — a generic, domain-agnostic dashboard overlay for adjusting `ContentScaleController`-managed contentScale/paddingScale, as an alternative to in-world double-click+scroll:
    - `ScaleConfigEntry` — `record(String componentId, Component label, @Nullable Integer accentColor)`; a null accent falls back to `ScaleConfigPanel`'s cycling palette rather than forcing every caller to pick a color. That's the package's only knowledge of what a "box" is.
    - `ScaleConfigPanel` — an embeddable overlay, deliberately **not** a `Screen`: a host screen owns one instance and calls `render(RenderContext, Bounds)`/`mouseClicked`/`mouseScrolled` itself only while visible, toggling visibility on its own. One rounded dark card per entry (`ThemeKey.PANEL_BACKGROUND`/`BORDER`), laid out as a vertical list (chosen over a 2-column grid — each card already holds a header, a status pill, and two labeled sliders, and a corner-anchored panel is rarely wide enough to give a second column enough slider-drag precision to be worth the added layout complexity) anchored to a screen corner via the existing `Anchor` enum, passed into the constructor rather than hardcoded. Each card: the entry's label as an accent-colored header (no font-weight primitive on `RenderContext`, so a slightly larger scale stands in for "bold"), a small rounded status pill showing the average of both sliders' live percentages, then "Text Scale"/"Padding" sub-labels (new `config.marieslib.scaleconfig.textScale`/`.padding` lang keys) each with its own live percentage and slider track (`RenderContext.drawBar`, filled in the card's accent color) ranged to `ContentScaleController.SCALE_STORAGE_MIN`/`MAX` (now public) — the actual valid range for the raw persisted multipliers this panel edits, not `resolveContentScale`/`resolvePadding`'s sanity clamps, which are in different (already-resolved, caller-specific pixel) units. A click on a track jumps that slider straight to the clicked position; scrolling over a track nudges it by one step, mirroring `ContentScaleController.handleScroll`'s in-world gesture. Each interaction re-reads the current persisted `ComponentState` (or a default) fresh and writes back only its own field, immediately, no separate Save/Apply step — so adjusting one slider never clobbers the other's already-saved value. `mouseClicked`/`mouseScrolled` hit-test against the panel's own rendered bounds first: uncontested (`false`) for anything outside them so the host screen's own dragging keeps working underneath, consumed (`true`) for anything inside, whether or not it lands on a slider track.
    - **Replaces** the previous `ContentScaleConfigScreen`/`ScaleSliderButton` (both deleted): a vanilla `AbstractSliderButton` needs `Screen`-hosted widget-list rendering through raw `GuiGraphics`, which doesn't fit an embeddable, `RenderContext`-only overlay — sliders are now drawn procedurally with `RenderContext` primitives instead. **Nourished's `ClientEvents.openScaleConfigScreen()` still references the deleted types and will not compile until updated to wrap `ScaleConfigPanel` in a thin host `Screen`** — out of scope for this marie-ui-only pass, flagging rather than silently leaving it broken.
- `dev.marie.framework.ui.commandcenter` (marie-ui) — a pluggable, domain-agnostic registry + shared screen for consumer mods to surface cards under sidebar-navigated categories, with zero knowledge of what any card actually does:
    - `CommandCenterCategory` — `record(String id, Component label, int sortOrder)`, a sidebar entry.
    - `CommandCenterCard` — the standard templated card (`title`/`subtitle`/`accentColor`/nullable `onClick`, inert if `onClick` is null) and `CustomCommandCenterCard` — the escape hatch for a card needing fully custom content (a caller-supplied `MarieComponent`, rendered inside the card body and forwarded `mouseClicked`/`mouseScrolled`) — same "caller-supplied in, caller-supplied out" principle as `MarieNotifications`'s merge function. Both implement a shared internal `CommandCenterCardEntry` so the registry can store/order them together.
    - `CommandCenterRegistry` — `registerCategory`/`registerCard`/`registerCustomCard`, gated by the same `MarieAPIState.assertRegistrationAllowed` phase check `MarieColors`/`MarieTracking` use; `categories()` sorted by `sortOrder`, `cardsFor(categoryId)` in registration order. Backed by two `AbstractRegistry` instances with `freezeInternal`/`resetInternal`/`unfreezeInternal`, mirroring `TrackerRegistry`/`ColorDefinitionRegistry`'s shape — **note**, unlike those two, nothing currently calls `freezeInternal()`: their freeze is orchestrated centrally by marie-core's own `MarieApiRegistries`/`ReloadGuardListener`, which can't reach a marie-ui-owned registry under the locked one-directional module graph. The registration-phase gate still applies (it's a global phase check, not tied to this registry's own frozen flag); wiring an actual freeze point is left for a future pass — flagging rather than inventing a new cross-module orchestration path.
    - `CommandCenterScreen` — sidebar of categories (click to select, selection highlighted via `ThemeKey.BORDER_HOVER`, filtering the main content grid to that category's cards) plus a wrapping grid of the selected category's cards, all sitting inside a single dark boxed dashboard container spanning the sidebar + content area (`ThemeKey.PANEL_BACKGROUND`/`BORDER`, same rounded-rect look as `ScaleConfigPanel`'s own cards) rather than floating over the game world; individual cards keep their own panel background/border, title in `accentColor`, subtitle in `ThemeKey.TEXT_SECONDARY`; whole card clickable when `onClick`/custom content is present. The whole chrome (sidebar + card grid together) is now draggable/resizable as one unit via `DraggableResizable`, always active — no separate edit mode or toggle key, since `DraggableResizable`'s own hit-testing already gates a drag/resize to clicks on its handles/edges. Position/size persist through a new `MarieConfigPersistenceProvider(MarieCore.MOD_ID)` instance (component id `marieslib.commandcenter.panel`), defaulting to the previous fixed `460x320` centered box when nothing's persisted yet; min/max bounds are `300x200`/`1000x800`.
    - `MarieCommandCenter.openScreen()` — the facade, mirroring `MarieNotifications`'s shape. **No default keybind was added**: this codebase has zero precedent for a marie-core/marie-ui-owned `KeyMapping` (every existing keybind, e.g. Nourished's `EDIT_CALORIE_HUD`/`OPEN_SCALE_CONFIG`, is registered per-mod) — inventing a keybind-ownership convention here wasn't a call to make unilaterally; flagging for a decision instead.
- `MarieColors.withOpacity(int rgb, double opacity)` — centralizes the alpha-from-opacity math already duplicated across consumer mods (e.g. `panelColorWithOpacity`); clamps opacity to `[0.0, 1.0]` and applies it as the alpha channel, discarding any existing alpha bits.
- `MarieColors.shade(int rgb, double amount)` — per-channel linear blend toward black (`amount < 0`) or white (`amount > 0`) by `|amount|` (clamped to `[-1.0, 1.0]`); alpha bits are preserved, unlike `withOpacity`.
- Generic client-side notification subsystem, `dev.marie.framework.notification` (marie-ui), a peer folder alongside `client`/`compat`/`tooltips`/`ui` with zero domain knowledge (no food/nutrient concepts anywhere in the package):
    - `TextSegment` (`String` + packed-ARGB color) is the atomic content unit; a notification's content is `List<List<TextSegment>>` — an ordered list of lines, each an ordered list of colored segments rendered left-to-right on one line. `NotificationRequest`/`.Builder` carries content, `durationTicks`, an `emphasized` flag (renders at `NotificationConfig#emphasizedScale`), and an optional `mergeKey`/`mergeWindowTicks`/`mergeFunction` (`BiFunction<old content, new content, merged content>` — MarieLib never interprets what merging means, only invokes the caller's function)
    - `MarieNotifications.show(NotificationRequest)` is the facade trigger: if `mergeKey` matches an active slot's key (via `.equals()`) within that slot's own merge window (measured from that slot's last-triggered time, not app-start), the merge function replaces the slot's content and resets its timer in place; otherwise a new slot is pushed
    - Up to 4 visible slots stack above the XP bar, newest closest to the bar; a 5th push evicts the oldest immediately. Each slot has an independent fade-in+slide-in / hold / fade-out timer; remaining slots ease toward their new stack position (frame-rate-independent lerp) instead of snapping when a slot disappears
    - `NotificationRenderer` is a `RenderGuiEvent.Post` listener, wired via `MarieNotifications.registerClientListeners()` (called once from `MariesLibClient`), anchored above vanilla's XP bar by replicating `Gui#renderExperienceBar`'s own Y calculation (shifts when the bar is hidden, e.g. mounted on a jumpable vehicle) rather than a hardcoded offset; text-only, no background, respects `Minecraft#hideGui`
    - `NotificationConfig` exposes this subsystem's own position/scale/duration defaults (vertical gap above the bar, inter-slot gap, base/emphasized scale, default duration, fade-in/out ticks), mirroring `MariesLibConfigHolder`'s mutable-singleton shape
- `MarieTracking` (`dev.marie.framework.tracking.tracker`, marie-core) — self-contained public facade for the generic tracker/period-history subsystem, mirroring `MarieColors`'s pattern exactly (registration-phase guard via `MarieAPIState.assertRegistrationAllowed`, zero footprint in `MarieAPI`). `registerTracker`/`incrementTracker`/`getCurrentTrackerValue`/`getTrackerHistory` replace the old `MarieAPI` methods of the same names one-for-one. **Breaking change**: `MarieAPI.registerTracker`/`incrementTracker`/`getCurrentTrackerValue`/`getTrackerHistory` and the internal `TrackerRegistrationDelegate` are removed entirely — same precedent as the earlier `MarieAPI`-delegate-to-`MarieColors` rework for the color system; this is the second and (for now) last such move. Consuming mods must switch call sites to `MarieTracking`.
- Two-tier client sync for tracker state, fixing the client-side correctness gap `MarieAPI.getCurrentTrackerValue` previously had no way to close (attachments are server-authoritative and were never kept live-synced to the client, so a client-side call silently read stale/default data):
    - High-frequency path: `MarieTracking.incrementTracker` marks the touched tracker dirty per-player (new internal `TrackerDirtyState`); `TrackerManager.sweepDirtySync`, piggybacked onto the same per-player tick loop as `checkTrackers` (`ValueDecayListener.onPlayerTick`), pushes only the dirty trackers' current values to that player once at least `IMarieConfig#trackerSyncIntervalTicks()` (new config value, default `20`) ticks have passed since their last sync, then clears the dirty set. Carried by new `TrackerLiveSyncPayload` (`dev.marie.framework.tracking.tracker.network`, server→client, per-player, dirty-only — not `GenericStateSyncPayload`/`GenericConfigSyncPayload`, neither of which fit this shape).
    - Low-frequency path: on player login, `PlayerTrackingLifecycle.onPlayerJoin` sends every registered tracker's full history + period state via new `TrackerHistorySyncPayload`; on period completion, `TrackerManager`'s existing period-close logic sends a targeted single-tracker resync to just that player (not a broadcast). marie-core cannot depend on marie-resources per the locked one-directional module graph, so this does not reuse `MarieResourcesNetworking`'s per-player snapshot primitive — it's a marie-core-owned equivalent (`TrackerNetworking`, same `registrar.playToClient(...)` pattern).
    - `ClientTrackerCache` (`dev.marie.framework.tracking.tracker`, marie-core) — client-side-only transient keyed cache for current values, history, and period state, mirroring `ColorPreviewOverrides`'s shape, populated by `TrackerNetworking`'s payload handlers.
    - `MarieTracking.getCurrentTrackerValue`/`getTrackerHistory` now branch on side: a `ServerPlayer` reads the live server attachment as before; any other `Player` instance (client-side) reads `ClientTrackerCache` instead of the attachment.
    - New config surface: `IMarieConfig`/`MarieContext`/`FallbackConfig#trackerSyncIntervalTicks()` (default `20`), following the same `Supplier<Integer>` pattern as `trackerMaxRetention`/`trackerWeeklyPeriodDays`.
    - Opt-in immediate-sync mode alongside the throttled dirty-flag sweep: `TrackerDefinition.Builder#immediateSync(boolean)` (default `false`, reachable only through the full builder — not exposed via the `daily`/`weekly`/`monthly` convenience factories). When set, `MarieTracking.incrementTracker` sends that tracker's updated value to the client immediately via `TrackerNetworking.sendLiveValues`, bypassing the dirty-flag mark and throttled sweep entirely for that tracker. Intended for low-frequency, latency-sensitive trackers, not high-frequency ones — the existing `MarieNetworking` rate limiter (20 packets/sec per player) still applies and will silently drop excess traffic if misused.
- Generic server→client config/registry sync mechanism (`dev.marie.framework.resources`, marie-resources), replacing the need for consuming mods to hand-roll their own full-state sync payload/channel/login-handler per registry (this is what Nourished's `SyncNourishedConfigSnapshot`/`NourishedSyncHandler` did before):
    - `GenericConfigSyncPayload` (`dev.marie.framework.resources.network`) — `CustomPacketPayload` carrying a registry id plus an opaque `CompoundTag`, sent server→client via `registrar.playToClient(...)`
    - `MarieResourcesAPI` (`dev.marie.framework.resources.api`) — the module's public facade: `registerConfigSyncSupplier`/`registerConfigSyncClientHandler` register the server-side snapshot builder and client-side apply function for a registry id; `broadcastConfigSyncReload` rebuilds and resends one registry's snapshot to all connected players; `getConfigSyncState` (client-side) reports `SyncState.UNINITIALIZED`/`PENDING`/`ACTIVE` per registry id
    - On player login, every registered registry's snapshot is built and sent automatically; unknown/unregistered incoming registry ids on the client are logged and ignored, not thrown
    - Self-registers its `RegisterPayloadHandlersEvent` listener and `PlayerEvent.PlayerLoggedInEvent` hook via `@EventBusSubscriber(modid = MarieCore.MOD_ID)`, so marie-core never references marie-resources directly, per the locked one-directional module graph
- `GameplayTriggerListener` (`dev.marie.framework.handler`, marie-core) wires real detection into the previously-dormant `ValueSourceTrigger.TriggerType.BLOCK_BROKEN` and `ENTITY_KILLED` cases — `BlockEvent.BreakEvent` and player-caused `LivingDeathEvent`s now fire `MarieAPI.fireSourceTrigger` directly. Detection only; MarieLib still has no opinion on what these triggers mean.
- `ValueEffectsListener.onPlayerTick` (marie-core) now also fires `ValueSourceTrigger.TriggerType.TICK` (via `ValueSourceTrigger.tick("sprint")` / `tick("swim")`) whenever a player is sprinting or swimming, reusing the existing `APPLY_INTERVAL_TICKS` gating rather than adding a new listener.
- Generic tracker/period-history framework (`dev.marie.framework.tracking.tracker` and `.tracker.definition`/`.tracker.registry`, marie-core) — MarieLib manages per-player accumulators, period boundaries, and bounded history for arbitrary trackers with zero domain knowledge (no calories, no nutrients):
    - `TrackerDefinition` (`.tracker.definition`) — id, `TrackerPeriod` (`DAILY`/`WEEKLY`/`MONTHLY`/`REAL_TIME`/`SESSION`/`CUSTOM`), and retention (validated at registration, not construction); `daily(id, retention)`/`weekly(id, retention)`/`monthly(id, retention)` factories plus a full builder for `REAL_TIME`/`CUSTOM`
    - `TrackerHistoryEntry` (`.tracker.definition`) — immutable record of one completed period (tracker id, period id, start/end boundary, accumulated value)
    - `TrackerRegistry` (`.tracker.registry`) — enforces `IMarieConfig#trackerMaxRetention()` at `register()` time, throwing `IllegalArgumentException` if retention is out of bounds
    - `TrackerManager` (`.tracker`) — `checkTrackers(player, tracking)` runs once per player tick (from `ValueDecayListener`, independent of the decay feature flag) to open/close `DAILY`/`WEEKLY`/`MONTHLY`/`REAL_TIME` periods; `SESSION` trackers open/close once per login from `PlayerTrackingLifecycle.onPlayerJoin`; `CUSTOM` trackers close only via `fireCustomTrackerReset`
    - `MarieAPI.registerTracker` / `incrementTracker` / `getCurrentTrackerValue` / `getTrackerHistory` — the public registration/read surface, backed by a new `TrackerRegistrationDelegate`
    - `TrackingData` gained `trackingAccumulators`/`trackingHistory`/`trackerPeriodStates` maps, kept separate from the existing `values`/`total` nutrient-bar state, wired into the codec and `copySnapshot` the same way as `sourceMemory`/`categoryMemory`
    - New config surface on `IMarieConfig`/`MarieContext`/`FallbackConfig`: `trackerSystemEnabled()` (default `true`), `trackerMaxRetention()` (default `90`), `trackerWeeklyPeriodDays()` (default `7`), `trackerMonthlyPeriodDays()` (default `30`); `MarieContext.Builder.onTrackerPeriodCompleted(...)` hook fires once per boundary crossing
- Color system definition/identity/resolve layer, flat in `dev.marie.framework.color` (marie-core), completing the bridge between `ColorRegistry`'s existing override storage and the rest of MarieLib — `ColorRegistry` itself is unchanged, this is purely additive:
    - `ColorKey` — `record` wrapping a `ResourceLocation` identity; `ColorKey.of(id)` factory
    - `ColorDefinition` — a `ColorKey` plus its default packed-ARGB value; `ColorDefinition.of(key, defaultArgb)`
    - `ColorDefinitionRegistry` — `AbstractRegistry<ColorKey, ColorDefinition>` wrapper mirroring `MilestoneRegistry`'s shape (`freezeInternal`/`resetInternal`/`register`/`get`/`getAll`)
    - `MarieColors` — a self-contained public facade for this subsystem, separate from `MarieAPI`. `MarieColors.registerColor(ColorDefinition)` is gated by the same registration-phase check as every other MarieLib registration (`MarieAPIState.assertRegistrationAllowed`). `MarieColors.resolveColor(ColorKey)` checks `ColorRegistry` (keyed by `key.id().toString()`, the fixed `ColorKey`↔`ColorRegistry` mapping) for a user/datapack override first, falls back to the registered `ColorDefinition`'s default, and falls back to a logged-warning magenta error color if the key was never registered at all
- `ColorHexRowWidget` (`dev.marie.framework.client.config.cloth`, marie-ui) — a generic Cloth Config color row (swatch, `#RRGGBB` hex field, reset, live preview) built on `MarieColors`, generalizing the swatch/hex/reset/preview behavior of Nourished's `NutrientHudHexColorRowEntry` without that entry's nutrient-specific default-resolution logic. `NutrientHudHexColorRowEntry` itself is untouched; migrating Nourished onto the generic widget is a separate follow-up.
- `ColorPreviewOverrides` (`dev.marie.framework.color`, marie-core) — transient, non-persisted `ColorKey`→ARGB overrides, mirroring the string-keyed transient override map `MarieValueColors` already used for nutrient values, generalized to `ColorKey`. `ColorHexRowWidget` now pushes the in-progress (unsaved) hex value here on every keystroke and clears it on reset, so its own swatch preview reflects an edit live instead of only after Save+reopen. `ColorRegistry`'s own persistence is unchanged — it's still only written on `save()`.
- `ColorHexRowWidget.syncFromEffectiveColor()` — re-reads the current effective color (`ColorRegistry` override, else default via `MarieColors.resolveColor`) and updates the row's own swatch/hex field in place, without rebuilding the containing screen. Lets a bulk "reset all" flow refresh every visible row directly instead of reopening the config screen, mirroring the old `NutrientHudHexColorRowEntry.syncAfterBulkReset()`.
- `ColorKeyPair` (`dev.marie.framework.color`, marie-core) — a flat identity record pairing a background `ColorKey` with a text `ColorKey`, no logic. `MarieColors.registerColorPair(modId, id, backgroundDefaultArgb, textDefaultArgb)` builds the `panel.<id>`/`text.<id>` keys, registers both under the same registration-phase guard as `registerColor`, and returns the pair — so panels needing a background+text combo no longer hand-register two separate `ColorDefinition`s. `MarieColors` stays theme-free (no `ColorKeyPair`-specific resolve helper; call `resolveColor` per-key as before). `ColorPairRowGroup.buildRows(pair, backgroundLabel, textLabel)` (`dev.marie.framework.client.config.cloth`, marie-ui) builds the matching pair of `ColorHexRowWidget` rows for a Cloth Config category.

### Removed

- `DoubleClickRecognizer` (`dev.marie.framework.ui.edit`, marie-ui) — orphaned repo-wide after `ContentScaleController`'s cleanup; no remaining callers.

### Changed

- `MarieValueColors` / `GuiValueRenderer` (`dev.marie.framework.client.config.render`, marie-ui) now carry `@ApiStatus.Experimental`, matching their confirmed use by an external consumer mod — documentation/API-surface clarity only, no behavior change
- Runtime keyword-vs-recipe merge (`RuntimeResolutionMerge`) now recomputes confidence over the combined signal and can conditionally let recipe data override the keyword winner — **behaviour change, test opted-in categories via the scanner, not a pure bugfix**
    - **Background:** previously a keyword/suffix match permanently locked its category as the winner at that match's own spread; recipe-inheritance data could only append brand-new categories, never contest or dilute an existing one, and the merged result's confidence was `primary.confidence()` passed through verbatim — so an item whose recipe strongly disagreed with its name still reported high confidence and the name always won silently. The premature collapse in `StageMath.normalizeWithRejections` (`{winner: 1.0}`, runner-up mass discarded) meant the merge never even saw the corroborating/contesting recipe signal.
    - **Honest confidence (always, every category):** `mergePrimaryWithRecipeSupplement` now takes the keyword *raw* scores (pre-collapse — `KeywordResolutionStage` already emits `rawScores == values`, and the merge falls back to `values()` for any stage that leaves `rawScores` empty), sums in the recipe supplement's decayed raw contributions, and computes confidence once over that combined map as `spread(top two) / topWeight` clamped to `[0,1]` (new shared `StageMath.confidenceRatio`, same ratio `ClassificationResult.confidenceScore()` already used). Recipe data that agrees with the keyword keeps confidence high; data that disagrees lowers it, and — via the existing `RuntimeResolver.buildTraceFromResult` path — can now flag the result UNCERTAIN. This applies to **every** item that has both a keyword match and a recipe supplement, regardless of contestability; the merged `rawScores` now carries the combined signal (visible in the `SIGNAL_AGGREGATION` trace step and `marie_unknown_items` log).
    - **Conditional override (opt-in only):** new `contestable_values` array in `scanner_spec.json` (`ScannerSpecRegistry.ScannerSpec#contestableValues()` + static `ScannerSpecRegistry.contestableValues()` accessor, same per-mod `MarieJsonUtils`/`parseStringSet` mechanism as `community_tag_directory` / `excluded_items`), **defaulting to an empty set** in the schema itself and in `ScannerSpec.empty()`. For a category on that list: if the combined (keyword-raw + decayed recipe-raw) weight of a *different* contestable category exceeds the keyword-matched category's combined weight, that recipe-derived category becomes the winning classification outright (result `values` rebuilt so it is the argmax, stage compounded to `*_RECIPE`, debug reason records the override). For every category **not** on the list, the append-only / keyword-locks-the-winner behaviour is byte-for-byte what it was — the only change is the honest confidence above, plus a `"recipe contested '<cat>' … keyword held (not contestable)"` note in the debug reason when a non-contestable category outweighed the keyword winner but was not allowed to take it.
    - **This can change classification results for any Nourished (or other consumer) category later added to `contestable_values`** — e.g. opting in `proteins` would make a "chicken_salad" whose recipe is overwhelmingly vegetables resolve to `vegetables` instead of `proteins`. With no `contestable_values` entries (the shipped default) no winner changes; only confidence numbers and UNCERTAIN flags move. Recommend running the item scanner before/after and diffing dominant categories for any mod that opts a category in.
    - **`scannerConfidenceSpreadThreshold` default raised `0f` → `0.15f`** (`MariesLibConfigHolder`, and the matching `MarieContext.Builder` supplier default). At `0f` the `RuntimeResolver.buildTraceFromResult` UNCERTAIN path was dead (`threshold > 0f` guard) and `StageMath.normalizeWithRejections` always took the collapse branch (`spread >= 0` is always true). Post-merge the value is consumed as the `spread/max` ratio in `[0,1]`; `0.15` on that scale corresponds to the `0.10` absolute-spread ambiguity convention `MultiValueAnalysisPipeline` uses for a typical two-way category split (`{0.55,0.45}` → ratio ≈ 0.18), chosen slightly conservative so it does not mass-flag. Effect is confined to diagnostics: the runtime UNCERTAIN trace flag activates, `marie_unknown_items` logs a few more low-confidence items, and the offline `ItemClassifier` marks near-exact ties uncertain (its `spread` is on the raw weighted scale where `0.15` is tiny, so near-zero impact there). `RuntimeResolutionMerge`'s class javadoc updated — its old "recipe values are add-only — existing primary keys are never overwritten" claim is no longer universally true.
- `CommunityTagResolutionStage`'s `c`-namespace tag directory is now configurable per-consumer-mod instead of hardcoded
    - New `ScannerSpecRegistry.ScannerSpec#communityTagDirectory()` field (`community_tag_directory` JSON key), loaded through the exact same per-mod `scanner_spec.json` mechanism as `communityTagWeights()`/`excludedItems()`, defaulting to `"foods/"` in the config schema itself (not hardcoded fallback logic) — a new static `ScannerSpecRegistry.communityTagDirectory()` accessor mirrors the existing `stemmerDictionary()`-style convenience methods
    - `CommunityTagResolutionStage`'s private `C_COMMUNITY_TAG_PREFIX` constant (previously split as `"fo" + "ods/"` to dodge a domain-word grep) is removed entirely; `tagDirectory()` now delegates to `ScannerSpecRegistry.get().communityTagDirectory()` while keeping its existing public signature, and the resolve loop reads the same value
    - Preserves Nourished's current behavior with zero required changes on its side (the shipped default is still `"foods/"`); any other consumer mod can override it in its own `scanner_spec.json` (e.g. `"materials/"` for a non-food classifier). Traced every caller of `tagDirectory()`/`C_COMMUNITY_TAG_PREFIX`: only `ItemClassifier`'s `COMMUNITY_TAG` signal label reads it, and only as a runtime string, not a compile-time constant — `RecipeInheritanceStage` and other scanner stages do not depend on this value at all
- New `dev.marie.framework.ui.api` package (marie-ui), mirroring marie-core's `dev.marie.framework.api` naming convention as the shared home for MarieUI's slim public-facade classes — internal implementation stays exactly where it was, only the front door moved:
    - `MarieNotifications` relocated from `dev.marie.framework.notification`; `NotificationManager.show`/`clear` and `NotificationRenderer.onRenderGuiPost` widened from package-private to `public` (marked `@ApiStatus.Internal`) so the facade can delegate to them across packages. Also picked up the `@ApiStatus.Experimental` annotation it was missing entirely — the only one of the four facades without it before this change.
    - `MarieCommandCenter` relocated from `dev.marie.framework.ui.commandcenter`. `CommandCenterRegistry`/`CommandCenterScreen` and the card/category record types are unaffected and stay put.
    - `EditModeCoordinator` relocated from `dev.marie.framework.ui.edit`. `EditableComponent`/`EditModeController` stay in `dev.marie.framework.ui.edit` as internal implementation detail; `EditableComponent`'s javadoc `@link`s to `EditModeCoordinator` now cross-package import it.
    - New `MarieScaleConfig` facade (`dev.marie.framework.ui.api`) — `ScaleConfigPanel` previously doubled as both the public entry point and its own ~200 lines of layout/drag/persistence implementation, with every consumer constructing it directly (`new ScaleConfigPanel(...)`). `MarieScaleConfig.create(List<ScaleConfigEntry>, PersistenceProvider, Anchor)` now front-doors construction; `ScaleConfigPanel`/`ScaleConfigEntry` stay in `dev.marie.framework.ui.scaleconfig` as the returned handle/data type, same pattern as `NotificationRequest`/`CommandCenterCard` staying in their own subsystem packages.
    - All four facades gained real javadoc (subsystem summary, `@param`/`@return` where applicable, a usage-example snippet) in place of the previous one-line summaries — they're meant for other mod developers to call without reading MarieUI's internals.
    - **Breaking change for consuming mods**: `dev.marie.framework.notification.MarieNotifications`, `dev.marie.framework.ui.commandcenter.MarieCommandCenter`, and `dev.marie.framework.ui.edit.EditModeCoordinator` no longer exist at their old package paths — update imports to `dev.marie.framework.ui.api`. Nourished's `MarieNotifications`/`MarieCommandCenter`/`EditModeCoordinator` call sites and its 4 direct `ScaleConfigPanel` construction sites are not yet updated (out of scope for this marie-ui-only pass) and will not compile until switched over.
- `ModuleRegistry` / `ComponentState` / `MarieComponent` (`dev.marie.framework.ui.component`, marie-ui) now carry `@ApiStatus.Experimental`, matching the tier already used on `DraggableResizable` / `ForeignScreenDetector` in the same module — these three were previously unannotated despite being load-bearing extension points with real external consumers. Documentation/API-surface clarity only, no behavior change.
- `RuntimeResolver` (`dev.marie.framework.runtime`, marie-core) now owns the shared `RecipeInheritanceResolver` instance directly instead of a dead, never-populated `recipeCache` field — `invalidateCache()` now actually clears the real recipe-inheritance index instead of a no-op field. Consumers (e.g. Nourished's `RuntimeFoodResolver`) now source the shared instance from `RuntimeResolver.getInstance()` instead of owning their own copy, so there is exactly one instance and one owner.

### Fixed

- `ForeignScreenDetector.onScreenOpening` no longer crashes when the opened screen's menu wasn't constructed through the standard type-registry path (e.g. `advancements_reloaded`'s custom advancements screen), which previously threw an uncaught `UnsupportedOperationException` from `AbstractContainerMenu.getType()`
    - A screen with no menu is now skipped silently; a menu that rejects `getType()` is logged at DEBUG and treated as no match instead of propagating the exception
- Datapack `tracker_milestones/*.json` files now actually reach `TrackerMilestoneRegistry` (marie-core). `MarieDatapackCallbacks` (the default `MarieDataLoader.Callbacks`) overrode `registerMilestone` but had no override for `registerTrackerMilestone`, so every parsed `TrackerMilestoneDefinition` fell through to the interface's no-op default and datapack-defined tracker milestones were silently dropped — only Java-side `TrackerMilestoneRegistry.register(...)` calls worked. Added the missing override; it calls `TrackerMilestoneRegistry.register(def)` directly, which is safe in the datapack apply window because `MarieApiRegistries.onDatapackApplyBegin()` resets (unfreezes) that registry and `onDatapackApplyEnd()` re-freezes it — the same reset/refreeze cycle `MilestoneRegistry` goes through for the `registerMilestone` path.
- Login tracker snapshot now carries the live current-period accumulator value (marie-core). `TrackerNetworking.sendFullSnapshot` / `buildTrackerEntry` only serialized `history` and `period`, so a freshly-logged-in client showed a stale/zero in-progress tracker value until the first dirty-sweep `TrackerLiveSyncPayload` arrived. `buildTrackerEntry` now also writes `current` (`tracking.trackingAccumulators.getOrDefault(trackerId, 0f)`) into each entry, and `handleHistorySync` calls `ClientTrackerCache.setCurrentValue(trackerId, entry.getFloat("current"))` for every tracker in the snapshot alongside the existing history/period population.
- Login tracker snapshot no longer drops a tracker that has accumulated a value but has no history and no opened period (marie-core). `buildTrackerEntry`'s early-return guard (`(history == null || history.isEmpty()) && period == null`) ran before `current` was written, so a CUSTOM-period tracker mid-accumulation — nonzero accumulator, no history entry yet, no period ever opened — was excluded from `sendFullSnapshot` entirely and its value never reached the client on login/reconnect. The guard now also checks `current == 0f` before returning null, and the entry reuses that same `current` local. `sendPeriodResync`'s only caller (`TrackerManager.closePeriodAndOpenNext`) invokes it after resetting the accumulator to `0f` and opening the next period, so `period != null` there already kept the entry non-null and the written `current` is still `0f` — no behavior change on that path. Unit test added (`TrackerNetworkingBuildEntryTest`).
- Client caches are now cleared on disconnect (marie-core). Several client-side caches had `clear()`/`reset()` methods documented "e.g. on disconnect" but no caller anywhere — there was no `ClientPlayerNetworkEvent.LoggingOut` listener in the codebase — so a client that disconnected and reconnected (or joined a different server) briefly showed the previous session's tracker values, notification stack, and config-sync state until fresh packets arrived.
    - New `dev.marie.framework.client.ClientSessionTeardownListener` (marie-core, domain-agnostic): registers one `ClientPlayerNetworkEvent.LoggingOut` handler that clears `ClientTrackerCache` directly and then runs any steps contributed via `registerAdditionalTeardown(Runnable)` (each guarded so one failure doesn't block the rest). `register()` is idempotent and must be called client-side only (the class references the client-only event type).
    - `MariesLibClient` (marie-ui) now calls `ClientSessionTeardownListener.register()` next to `MarieNotifications.registerClientListeners()` and contributes the three marie-ui teardown steps marie-core cannot reference directly: `MarieClientCache::resetDiagnostics`, `MarieClientState::reset`, `MarieNotifications::clear`.
    - `ClientEntityValueCache` is intentionally left out of scope: it is a generic per-consumer instance class (not a singleton), so marie-core holds no instance to clear and the consumer owns its lifecycle, including any dimension-change clearing.
- `SourceRegistry` no longer destroys a genuine `registerClassification()` entry when an authoritative classification override is applied on top of it and later removed (marie-core). Previously `applyAuthoritativeOverride` did `API_REGISTERED_CLASSIFICATIONS.put(sourceId, <mirror of override values>)`, unconditionally overwriting any real mod-init/KubeJS `registerClassification()` entry already stored for that `sourceId`. Once overwritten there was no way to tell an override-mirrored entry from a genuine one, so when `unregisterClassification()` later removed it (the `SourceClassificationRegistry` override being disabled or dropped on the next datapack reload) the original registration was gone for good — the source fell back to empty instead of its real classification.
    - New `PRE_OVERRIDE_API_CLASSIFICATIONS` map holds a snapshot of the genuine entry that existed for a `sourceId` at the moment an override was *first* applied on top of it. The snapshot is taken only on the first override (guarded on `OVERRIDE_LOCKED_SOURCES`, which also blocks `registerClassification()` while held, so the snapshot stays the correct genuine state), and only from a value already present in `API_REGISTERED_CLASSIFICATIONS` — override-mirrored values are never copied in, so a restored entry is always a real API registration and never a stale datapack/tag-derived one.
    - `unregisterClassification()` now restores the preserved genuine entry into both `EXTERNAL_CLASSIFICATIONS` and `API_REGISTERED_CLASSIFICATIONS` when one exists; when no genuine entry existed before the override, it still deletes the `sourceId` outright as before. Survives any number of override apply/remove cycles (one `unregister` + re-`applyAuthoritativeOverride` happens per datapack reload via `SourceClassificationRegistry.pushToSourceRegistry`).
    - Unit test added (`SourceRegistryOverrideRestoreTest`): `registerClassification(id, "grains", 1.0)` outside `DATAPACK_RELOAD`, then `applyAuthoritativeOverride(id, {"proteins": 1.0})`, then `unregisterClassification(id)` leaves the source back at `{"grains": 1.0}`. NeoForge moddev `unitTest` support enabled on `marie-core` to give plain JUnit tests access to game types.
- Scanner recipe-inheritance merge (`RecipeInheritanceStage.mergeQualifyingContributions`) no longer silently drops recipe mass that lands on an already-scored category — **behaviour change: the next scanner run will likely report more multi-value (and more ambiguous) sources; this is expected and desired, not a regression.** This is the scanner-side counterpart of the `RuntimeResolutionMerge` change above.
    - **Background:** the merge gated every recipe contribution on `scores.getOrDefault(key, 0f) <= 0f`, so a recipe ingredient set that genuinely contested the keyword/suffix/namespace result — but on a category those signals had already touched — left *no trace* in the score vector. `ItemClassifier.buildResult` then measured confidence as `topScore - secondScore` over that already-pruned vector, and `MultiValueAnalysisPipeline.analyze` derived dominant/secondary/ambiguous from the same pruned `scores()`. Net effect: recipe evidence that disagreed with an item's name was invisible to every downstream multi-value / ambiguity check, mirroring the runtime "confidence inherited from primary" bug.
    - **B1 — contested mass is now added, not dropped:** `mergeQualifyingContributions` applies *every* non-authoritative recipe category. For a category the primary signals already scored, the decayed, `recipeInheritance`-multiplier-scaled weight (`value = raw * multiplier`, unchanged) is now summed into that category's existing score instead of being discarded. `authoritativeKeys` protection is **unchanged** — a category the item carries via its own community/datapack value tag is ground truth and recipe *inference* still cannot touch it. The returned `scaled` map still reports exactly the delta applied, for the diagnostic `RECIPE_INHERITANCE` signal.
    - **B2 — honest confidence over the combined vector:** `ItemClassifier.buildResult` now computes confidence as `StageMath.confidenceRatio(scores)` (top-two spread over the top weight, clamped `[0,1]`) — the same shared helper `RuntimeResolutionMerge` uses, computed *after* B1's augmentation over the final combined `scores`. `ClassificationResult.confidenceSpread()` is consequently now a `[0,1]` ratio rather than a raw-weight difference; the `CONFIDENCE`/`WINNER_SELECTION` trace steps and `confidenceScore` report on that scale, and `uncertain` is `ratio < scannerConfidenceSpreadThreshold` (default `0.15`), consistent with the runtime `buildTraceFromResult` path. Downstream ratio consumers (`ConfidenceValidator`) shift onto the same scale.
    - **B3 — no `contestable_values` gating:** unlike the runtime merge, none of this is gated behind `ScannerSpecRegistry.contestableValues()`. That opt-in exists so mods keep deterministic control over when recipe data can flip the *gameplay* classification. The scanner analysis / tag-recommendation path exists to surface every genuinely contested category for human review, so it fires unconditionally. The scanner merge still only adjusts category magnitudes — it never picks a winner; dominant/secondary/multi-value selection stays downstream in `buildResult` and `MultiValueAnalysisPipeline`.
    - **Downstream flow confirmed:** `MultiValueAnalysisPipeline.analyze` reads only `r.scores()` and `r.tagClassified()` (not `confidenceSpread()`/`uncertain()`), recomputing dominant, `spread = dominant - second`, the `< 0.10` ambiguity gate, and the qualifying-secondary test (`score >= 0.15` absolute **and** `score >= dominant * 0.35` relative) from the raw map. B1's augmented scores therefore feed it directly: a contested category that clears both thresholds now becomes a qualifying secondary (single → multi-value), and a contest that narrows the top-two gap below `0.10` now marks the source ambiguous. Both scanner entry points are covered — `ItemScanner` → `MultiValueAnalysisPipeline.run`, and `MarieScannerCommands` → `SourceCollector` (`mergeTagAndRecipeScores`) → `runFullRegistry`.
    - Out of scope (deferred, product decision): per-ingredient provenance plumbing (the dead 4-arg `RecipeInheritanceResolver.resolve` overload / trace enrichment) and a "distinct ingredients disagree" compound multi-value trigger — neither is touched here.
- `SourceApplicationPipeline` no longer scales the aggregate "total" tracking value by the fatigue/novelty `multiplier`. Inside the `FeatureFlagCache.enableTotalTracking()` block, `totalAdded` (from a `SourceClassificationRegistry` override's `total()` or the resolver's `SourceDelta.total()`) is now passed to `tracking.addTotal(...)` unscaled — a source registered with `total=60` always adds exactly 60 to the running total regardless of streak/fatigue/novelty state. The multiplier still diminishes repeat-source per-value/nutrient gains; it was never meant to touch the total. Debug log reworded so it no longer implies the multiplier is applied to the total.
- `SourceApplicationPipeline` no longer zeroes or synthesises an item's calorie total just because the item is source-classified. In the `override != null` branch, `totalAdded` was previously the **sum of the classification entry's per-category `values` weights** (category weights and calorie deltas are different units — an item in 2 categories got ~2 calories regardless of its real value). That sum-conflation is removed, and the precedence is now: **`totalAdded = resolverDelta.total()` when non-zero, otherwise the classification entry's own explicit calorie value** (`SourceClassification.total()`, populated from the new `calories` field — see Added). An item's pre-existing calorie value (vanilla default, or a `food_overrides.json` entry the consumer routes through `resolverDelta`) is therefore preserved by default when the item gains a `source_classifications` category; the entry only overrides calories when it explicitly declares a `calories`/`total`. `valueDeltas`/`matchedBars` construction (tag-first, resolver supplements gaps) is unchanged.
    - **Consumer-side caveat (Nourished):** whether a `food_overrides.json` calorie value actually reaches `resolverDelta.total()` depends on the consumer wiring a calorie-aware `MarieContext.Builder#sourceDeltaResolver`. The lib default (`MarieContext.defaultSourceDeltaResolver`, `payload * barWeight / scale`) has no calorie awareness, so with the default resolver a food-override calorie only survives if the consumer feeds it in as the trigger `payload`. This is unverified against Nourished's resolver and may need a consumer-side change.
- `ScaleConfigPanel` (`dev.marie.framework.ui.scaleconfig`, marie-ui) no longer overlaps multiple visible panels sharing the same `Anchor` at identical coordinates. Each `render()` call now claims a vertical stacking slot in a shared, static per-anchor registry (`VISIBLE_CLAIMS`), and `anchorY` adds the combined height of other panels already claiming that anchor earlier in the same render pass — same registration-order shape `EditOverlayScreen` already uses for its render targets. Since a panel has no explicit "hide" call (a host screen simply stops calling `render()` on it), staleness is self-healing: a panel finding itself already registered means a new render pass has started, so the whole anchor's claim list — including any now-invisible panels — is cleared before this pass's claims are recorded. Works for any number of panels/combination of visibility at an anchor, and reproduces the exact pre-fix position when only one panel is visible (no collision to resolve).
- `ValueEffectsListener.fireStateTicks` (marie-core) now requires `player.isInWater()` in addition to `player.isSwimming()` before firing the swim tick trigger. `isSwimming()` reflects vanilla's smoothed swim-pose flag and can stay `true` briefly after the player has actually left the water (e.g. exiting at speed while sprinting), which was causing swim triggers — and the swim HUD count — to keep firing on dry land.
- `SourceApplicationPipeline.process` (marie-core) now injects the player's `DiminishingReturnsConfig` into `TrackingData` (`tracking.setMemoryConfig(...)`) before posting `MarieEvents.SourceTriggerEvent`, not after. Consumer-mod listeners subscribed to `SourceTriggerEvent` that synchronously read tracking data (e.g. `toDeltaPayload()`, `getMostFatiguedFamilies()`, `config()`) previously ran against a `TrackingData` with a null `memoryConfig` and crashed with `IllegalStateException("[MarieLib] DiminishingReturnsConfig not injected...")`. Root cause was event/injection ordering, not a missing injection call — no other injection sites were touched.
- `GameplayTriggerListener` / `ValueEffectsListener.fireStateTicks` (marie-core) now pass `ItemStack.EMPTY` instead of `null` when firing `BLOCK_BROKEN` / `ENTITY_KILLED` / `TICK` triggers — the two-arg `MarieAPI.fireSourceTrigger` overload resolves to a `null` stack internally, which crashed downstream item-agnostic consumers (e.g. Nourished's `registerSlim` callback) with an NPE on `.getItem()` on every sprint/swim tick. `ItemStack.EMPTY` is the documented "no item" sentinel for non-item triggers per `ValueSourceTrigger`'s own convention.
- Removed leftover `TEMPDEBUG` diagnostic logging from `InstanceTagSourceRegistry.contains()` (marie-core) and `InstanceTagRegistry.contains()` (marie-resources) — both fired an ungated `LOGGER.info` on every call and had become log noise now that `CommunityTagResolutionStage` calls into this path far more frequently.
- `MarieNetworking.RATE_LIMIT_STATE` (marie-core) no longer grows unboundedly on a long-running server — `PlayerTrackingLifecycle.onPlayerLogout` now also calls new `MarieNetworking.clearRateLimitState(UUID)`, alongside its existing `TrackerManager.clearDirtySyncState` cleanup call.
- Closed the documented "no general reload happened, please re-register" gap for `TrackerRegistry`/`ColorDefinitionRegistry` (`MarieApiRegistries.onDatapackApplyBegin`'s known-gap doc): `ReloadGuardListener` now subscribes to NeoForge's `OnDatapackSyncEvent` and invokes `MarieContext.reloadBroadcastHook()` whenever `event.getPlayer() == null` (the full-reload case, as opposed to a single player's join-time sync). That event fires from `PlayerList.reloadResources()`, itself only called after `MinecraftServer.reloadResources()`'s returned future resolves — i.e. strictly after `MarieApiRegistries`' reset/refreeze of `TrackerRegistry`/`ColorDefinitionRegistry` for that reload pass has already completed — and it fires for both vanilla `/reload` and a mod's own reload command, whereas the existing `reloadAndBroadcast(server)` call was previously wired into only the latter (`MarieDatapackCommands.reloadAll`, now removed in favor of the automatic hook so it doesn't fire twice on that path). Reused the existing `MarieContext.reloadBroadcastHook()`/`onReloadBroadcast(...)` hook rather than adding a new one — it was already `@ApiStatus.Experimental` with no prior callers to break, and its "reload happened, notify" semantics already fit; only its javadoc needed clarifying, not its name. `TrackerRegistry.register`/`ColorDefinitionRegistry.register` now upsert on a duplicate key (new `AbstractRegistry.upsert`) instead of throwing `IllegalStateException`, since consumers are now expected to legitimately call them again from this hook on every reload; every other `AbstractRegistry` subclass keeps the original duplicate-throwing `register()`.
- `ReloadGuardListener.reloadAndBroadcast` was invoking `MarieContext.reloadBroadcastHook()` while `TrackerRegistry`/`ColorDefinitionRegistry` were already frozen (that freeze completes in `MarieApiRegistries.onDatapackApplyEnd`, strictly before this hook fires), so a consumer's hook calling `registerTracker`/`registerColor` — the intended re-registration pattern the hook exists for — threw `IllegalStateException("registry is frozen")` instead of upserting. New internal `AbstractRegistry.unfreeze()` (exposed as `TrackerRegistry.unfreezeInternal()`/`ColorDefinitionRegistry.unfreezeInternal()`) restores the frozen entries into the mutable map without clearing them; `reloadAndBroadcast` now calls it on both registries immediately before invoking the hook and refreezes both in a `finally` block immediately after, so the freeze guarantee holds everywhere except this one coordinated window and survives a throwing hook. No other registry is touched.
- Closed a second, more severe instance of the same "no general reload happened, please re-register" gap: `AddReloadListenerEvent` (which `MarieApiRegistries` resets `TrackerRegistry`/`ColorDefinitionRegistry` in response to, via `MarieDataLoader.apply`) fires on *every* world/server boot as well as on `/reload`, but `OnDatapackSyncEvent` — the event `reloadAndBroadcast` was wired to — only fires from `PlayerList.reloadResources()`, which is reachable exclusively from the explicit-`/reload` code path (`MinecraftServer.reloadResources(Collection)`), never from ordinary server startup (traced via NeoForge's vanilla patches: the boot-time resource load runs through `WorldLoader.load`, called from `Main.main` before a `MinecraftServer` instance even exists, and never touches `PlayerList.reloadResources()`). So every mod-init registration made via `MarieTracking.registerTracker`/`MarieColors.registerColor` was being wiped by the very first reload pass on every world load with nothing to restore it, and would only reappear after an explicit `/reload`. `ReloadGuardListener.onServerStarting` (`ServerStartingEvent`, already subscribed for `ReloadPipeline.reloadAll()`) now also calls `reloadAndBroadcast(event.getServer())` — the first point after boot with both a valid server reference and a guarantee the initial `AddReloadListenerEvent` reload pass has already completed. Since `MarieAPIState`'s `DatapackReloadScope` (opened for `MarieDataLoader.apply()`) already closed by the time `ServerStartingEvent` fires, `reloadAndBroadcast` now also explicitly reopens the registration window (`MarieAPIState.openForDatapackReload()`) around the hook call, rather than assuming it's still open — the same unfreeze/refreeze-in-`finally` safety it already applied to the two registries now also covers the phase gate.
- `ThemeKey.PANEL_BACKGROUND` (`dev.marie.framework.ui.theme`, marie-ui) was defined with alpha `0x00` in both `DarkTheme` (`0x00101010`) and `LightTheme` (`0x00F0F0F0`) — fully transparent, so every fill using it (`ScaleConfigPanel`'s cards, `CommandCenterScreen`'s cards and boxed dashboard container) rendered nothing regardless of caller, leaving only borders visible. Checked every call site first — none relied on the transparency for a hit-region or ghost/preview effect, all expected a solid panel. Now `0xF0101010`/`0xF0F0F0F0`, matching the near-opaque alpha (`0xF0`) already used for solid dark panels elsewhere (`ImportExportToast`, `PresetsWidget`).
- `CommandCenterScreen`'s constructor was still passing the misspelled `marielib.commandcenter.title` translation key (no such key ever existed in `en_us.json` under either spelling, so the screen fell back to the raw key string) — corrected to `marieslib.commandcenter.title`, matching the `marieslib` namespace every other key in the file uses, and added the missing entry ("Command Center") to `en_us.json`.
- `CommandCenterScreen`'s outer chrome is replaced again, superseding the previous (unsuccessful, ad hoc left-aligned-title-plus-accent-bar) styling attempt above: the actual visual quality of Nourished's Diet Screen comes from `DietPanelContainer`'s specific chrome — a `drawRoundedRect` fill/border, a centered title row, and a divider line beneath it — so that exact recipe is now a generic marie-ui primitive instead of something only Diet Screen had. New `RenderContext.drawWindowChrome(x, y, width, height, title, titleColor)` (plus supporting `RenderContext.textWidth(String, float)`, needed to center the title without leaking `Font` into the `RenderContext` contract) draws the fill/border via the existing `drawRoundedRect`, centers the title at the same local title-row geometry `DietPanelContainer` uses (title baseline at local y `9`, divider at local y `26`, divider color `0xFF2E2E2E` — ported as exact numbers, not approximated) and returns the content `Bounds` below the divider. `CommandCenterScreen` now calls this once for its whole frame ("Command Center") and lays its sidebar + card grid out inside the returned content region, replacing the ad hoc background-panel/header methods from the previous attempt. `ScaleConfigPanel`'s own card styling is unchanged — this was scoped to `CommandCenterScreen`'s outer frame only.
- `CommandCenterScreen`'s `drawWindowChrome` box was stretched to span the *entire* screen (full `this.width`/`this.height` minus a small margin), but its geometry — 100px sidebar, 150×48px cards, 9px title-row text — was ported from `DietPanelContainer`, which sizes that same chrome for a small fixed panel, not a full window. On any non-tiny resolution this rendered as a huge near-empty box with a tiny title crammed in the corner and one tiny card floating in a mostly-dead void. The chrome is now a fixed `460×320` box (`PANEL_WIDTH`/`PANEL_HEIGHT`) centered on screen via `(this.width - PANEL_WIDTH) / 2`/`(this.height - PANEL_HEIGHT) / 2`, matching Diet Screen's own bounded-panel-on-top-of-the-world look instead of a stretched full-screen container; the sidebar/card grid still lay out relative to the chrome's returned content `Bounds`, unchanged.
- `CommandCenterScreen.render` was drawing its `drawWindowChrome` box/sidebar/cards *before* calling `super.render(...)`, but `Screen.render` calls `renderBackground(...)` as its own first statement — which runs the world-blur post-process shader and then paints a semi-transparent dark overlay across the whole framebuffer, over whatever was already drawn. Because our chrome was drawn first, it (and the world behind it) got caught in that blur/overlay pass, rendering the whole screen soft and washed-out — not a color or style issue, a straight render-order bug (regressed several commits back when the background panel was moved ahead of `super.render()` to sit behind the sidebar's vanilla buttons, before those buttons were replaced with hit-tested rows). `super.render()` now runs first again, so `renderBackground`'s blur/overlay only ever catches the game world behind the screen, and the chrome/sidebar/card grid draw crisp on top of it, matching every other vanilla `Screen`'s contract (and Diet Screen's own crisp look, which never went through this pipeline in the first place since it's a HUD overlay, not a `Screen`).
- `GenericStateSyncPayload`'s size ceiling (`MarieNetworking.MAX_PAYLOAD_BYTES`) is now enforced by its `STREAM_CODEC` decoder from the raw wire byte count, before the `CompoundTag` is decoded, instead of `MarieNetworking.exceedsMaxSize` re-serializing the already-decoded tag to measure it after the fact. A malicious client could previously force a full NBT decode plus a second full re-serialization on the server thread for every oversized packet before it was rejected. The record gained a decode-time-only `oversized` field (never written to the wire, never true for a client-constructed payload) that `MarieNetworking.handleServer` now checks first, ahead of the unloaded-chunk/reach checks; externally-visible behavior (silent debug-logged drop, no disconnect) is unchanged.
- `TrackingData` full-sync payload previously carried no "recent ids" field at all — only raw `sourceMemory` — so `MarieClientCache.Snapshot.fromFullTracking` (marie-ui) derived its own "recent" list client-side from `sourceMemory` recency, duplicating logic the consuming mod already computes correctly server-side and had already wired through for delta sync (`toDeltaPayload(recentIds)`). `TrackingData` gained a new `recentIds` field (`List<String>`, wired into the codec and `copySnapshot` the same way as `sourceMemory`) and a `setRecentIds(List<String>)` setter for the consuming mod to populate before a full sync; `fromFullTracking` now reads `snapshot.recentIds` directly and the sourceMemory-derived fallback is removed. MarieLib still has zero knowledge of what a "recent id" represents — Nourished-side wiring (populating `recentIds` before `syncDiet`) is a follow-up.
- `SourceClassificationRegistry` (marie-core) entries loaded from `source_classifications.json` were never reaching `SourceRegistry`, so `getScore()` — used by both default source-value and tooltip resolvers in `MarieContext` — always fell straight through to `SourceRegistry`'s scanner-derived tag classifications and silently ignored every datapack override. `pushToSourceRegistry()` now bridges enabled `SourceClassificationRegistry` entries into `SourceRegistry.registerClassification(...)` after every successful `load()`/`reload()`/`loadFromDatapack()`/`setOverride()`/`removeOverride()`, and unregisters the previous pass's entries first (new package-private `SourceRegistry.unregisterClassification`) so a value key or item removed from the JSON actually disappears instead of lingering — `SourceRegistry.clearExternalClassifications()` alone could not do this, since it only rebuilds `EXTERNAL_CLASSIFICATIONS` from `API_REGISTERED_CLASSIFICATIONS`, which itself was never cleared.
- `compositeRatioThreshold`'s default (`MariesLibConfigHolder`/`MarieContext.Builder`/the Cloth Config scanner category reset value) was `0f`, so `ItemScanner.isComposite`/`applyScanResult` kept *any* category with a nonzero secondary signal score as a full composite classification — a single stray tag another mod adds to a vanilla item (e.g. a broad `c:foods/*` tag on `minecraft:porkchop`) was enough to attach an unrelated category (e.g. "grains"/"vegetable") alongside the correct one, purely from `ItemClassifier`'s per-category signal sum having no discrimination floor. Default raised to `0.5f` (secondary category must now reach at least half the dominant category's score to qualify as composite); still user-configurable.
- `SourceApplicationPipeline.process` (marie-core) computed the tracked "total" stat from an override's raw JSON `"total"` field even when it diverged from the actual sum of that override's per-value deltas (e.g. `"total": 1.0` with `"values": {"sugar": 0.8}`), so the total-tracking bar and the sum of applied per-value deltas could disagree for the same source application. `totalAdded` now sums the actual merged per-value deltas that are about to be applied, falling back to the declared `total()`/resolver total only when there are no per-value deltas at all.
- `SourceRegistry.registerClassification` writes one `(sourceId, valueKey)` pair at a time, so an enabled `source_classifications.json` override for an item didn't stop other callers (e.g. Nourished's `NutrientRegistry.registerClassificationsFromTags`, tag-derived) from registering additional keys onto the same sourceId — an override of `{proteins: 0.8}` for `minecraft:honey_bottle` could still show `sugar`/`grains` alongside it, from tag-derived registrations the override was never meant to coexist with. New `SourceRegistry.applyAuthoritativeOverride(sourceId, values)` (package-private, used only by `SourceClassificationRegistry.pushToSourceRegistry`) fully replaces — not merges — a sourceId's entry and adds it to a new `OVERRIDE_LOCKED_SOURCES` set; `registerClassification` now ignores calls for any locked sourceId, so tag/API registrations for an overridden item are dropped instead of silently re-polluting it, including ones that arrive after the override was applied. `unregisterClassification` (called when an override is removed/disabled/reloaded away) also clears the lock, so the item becomes writable by tag/API registration again once its override is gone. An enabled override entry with no (or empty) `"values"` is never pushed/locked at all — it has nothing authoritative to contribute, so the item is left to normal tag/API classification instead of being locked out with zero values.
- `ValueDecayListener.onPlayerTick` / `ValueEffectsListener.onPlayerTick` (marie-core) now skip creative/spectator players — decay is no longer applied to them, and `ValueEffectsListener` now calls `MarieContext.get().effectClearer()` for them the same as when effects are disabled, stripping any already-active Nourished effects the moment a player switches into creative or spectator mode. Vanilla hunger is inert for these game modes; MarieLib's value/effect system wasn't matching that behavior.
- `CommandCenterScreen`'s card grid and sidebar drew unconditionally past the panel's own bounds once the panel became drag/resizable — a category with enough cards to overflow the visible height (or a manually shrunk panel) rendered cards straight through the chrome's bottom border instead of being contained. `drawCards`/`drawSidebar` now wrap their render region in `RenderContext.pushClip`/`popClip` (the same scissor mechanism `CustomCommandCenterCard` content already uses) sized to the panel's live content `Bounds`, and skip drawing/hit-registering any row or card fully outside that clipped band. The card grid also gained a clamped `cardScrollOffset`: hovering it and scrolling shifts the grid by `CARD_SCROLL_STEP` (24px) per notch, clamped every `drawCards` pass to `[0, totalContentHeight - visibleHeight]` so a resize or category switch can't leave it scrolled past the end; switching categories resets it to `0`. No existing scrollable-list/panel precedent was found anywhere in marie-ui to match, so this is a plain from-scratch offset rather than a reused pattern.
- `CommandCenterScreen` didn't override `renderBackground`, so it inherited `Screen`'s default dirt/blur-and-dark-overlay pass over the whole game world behind it — appropriate for a full-screen modal menu, but this chrome is a small floating dashboard box centered on screen (per its own "not stretched to fill it" design), so the heavy blur made the world behind it needlessly unreadable. Now overrides `renderBackground` as a no-op, same fix `EditOverlayScreen` already applies for the same reason, leaving the world crisp behind the panel.
- `NotificationRenderer.xpBarTopY` (marie-ui) anchored the notification stack only above vanilla's XP bar, with no clearance for vanilla's selected-item-name text (`Gui#maybeRenderSelectedItemName`/`renderSelectedItemName`), which vanilla anchors independently using `Math.max(leftHeight, rightHeight)` (floored at `59`, plus `14` more when `!canHurtPlayer()`, e.g. peaceful/creative-adjacent game modes) — so a highlighted item name could render underneath or through the notification stack whenever armor/health/vehicle-health rows pushed it higher than the XP bar's own offset. `xpBarTopY` now folds the same `Math.max(mc.gui.leftHeight, mc.gui.rightHeight, 59)` clearance (plus the same conditional `+14`) into its anchor, read live from vanilla's own HUD state each frame rather than a hardcoded offset. `toolHighlightTimer` is private with no accessor, so this clearance is reserved unconditionally rather than only when the text happens to be visible.
- `EditModeCoordinator.enterAll()` (marie-ui) previously called `enterEditMode()` on every registered `EditableComponent` unconditionally, which — for any component whose `enterEditMode()` opened its own `EditModeController`-owned screen — meant N separate `Minecraft#setScreen()` calls for N registered components; only the last one's overlay survived, silently orphaning the rest even though their controllers had already flipped `isActive()` to `true`. `EditOverlayScreen` now accepts a `List<MarieComponent>` instead of a single target (every input method keeps its existing discard-and-return-true forwarding shape, just looped across the list — each target still self-gates on mouseX/mouseY internally exactly as before); `EditModeController` gained a static `enterGroup`/`isGroupActive`/`exitGroup` lifecycle, independent of any single-target instance, that opens exactly one shared `EditOverlayScreen` for a whole group. `EditModeCoordinator` gained `registerGroupCapable(id, targetSupplier, hintText, exitKeyCode)` alongside the existing domain-agnostic `register(id, EditableComponent)`; `enterAll()` now combines every group-capable registrant into that one shared overlay instead of triggering N competing screens, while anything still registered the plain way keeps going through the unchanged per-component loop. `exitAll()` now also calls `EditModeController.exitGroup()`.
- `EditModeCoordinator.registerGroupCapable` took an eager `MarieComponent target` — since registration typically happens once at HUD-construction time, this could hand the coordinator a stale reference for a target whose identity is meant to be re-resolved later (e.g. one that gets rebuilt/replaced before edit mode is actually entered). Changed to `Supplier<MarieComponent> targetSupplier`; `enterAll()` now calls `.get()` on each registered supplier only at the moment a group-entry actually happens (i.e. when `EDIT_ALL_HUDS` is pressed), never at registration time, so registration stores only a reference to how to get the target rather than the target itself.
- `EditModeCoordinator.registerGroupCapable` had no way for a registrant to run its own logic when a group entry actually happens — only `targetSupplier` was invoked at that moment, so a caller needing to react to its own component joining the shared overlay (e.g. resetting some per-entry state) had no hook to do so. Gained an optional `Runnable onGroupEnter` parameter (new 5-arg overload; the existing 4-arg `registerGroupCapable` delegates to it with `null`), invoked by `enterAll()` for each group-capable registrant right after its `targetSupplier` is resolved. Stays fully generic — `EditModeCoordinator` invokes the callback without any knowledge of what it does, same "caller-supplied in, caller-supplied out" principle as the rest of this facade.
- `RuntimeResolver.resolveUncached` (`dev.marie.framework.runtime`, marie-core) now also checks `ExcludedItemsRegistry.isExcluded(itemId)` alongside the existing `ScannerSpecRegistry.get().excludedItems()` check — an item is excluded if either says so. The two registries are separate, non-communicating exclusion sources (`scanner_spec.json`'s embedded list vs. `excluded_items.json`'s independently loaded/mutable list); `RuntimeResolver` only consulted the former, so items excluded solely via `ExcludedItemsRegistry` (e.g. through its runtime `addExcluded`/`removeExcluded` API) were not actually excluded from resolution, unlike the pre-migration `RuntimeFoodResolver` behavior which checked both.

## [MariesLib 0.1.1-beta.4] — 2026-07-24

Scanner signal stages extracted into the `ResolutionStageHandler` cascade shape, `ComponentClassifier` wired into recipe-inheritance fallback, `DeathNutritionBehavior` renamed for domain-agnosticism, a marie-core/marie-ui/marie-commands audit for leftover food/nutrition-specific naming, tooltip message/color customization, excluded-item tooltip handling, a bundled-resource lookup fix, an excluded-items registry, and recipe-inheritance score merging for tagged items.

### Added

- `CommunityTagResolutionStage` / `SuffixResolutionStage` / `KeywordResolutionStage` (`dev.marie.framework.scanner.stages`)
    - Mechanical extractions of `ItemClassifier`'s former `analyzeSignal1CommunityTags` / `analyzeSignal3Suffix` / `analyzeSignal4Keywords` private methods into proper `ResolutionStageHandler` implementations
    - Same weight-table lookups and scoring math as before, unchanged output
- `CommunityTagResolutionStage.tagDirectory()`
    - Exposes the actual `c` namespace tag directory the stage scans, so callers never need to hardcode a duplicate copy of the value
- `RecipeInheritanceStage` now falls back to `ComponentClassifier.classify(...)` for a recipe ingredient when both `classifiedLookup` and its direct tag scores come back empty
- `RespawnValueBehavior` (`dev.marie.framework.tracking`)
    - Renamed from `DeathNutritionBehavior`; enum constants and config IDs (`preserve`, `reset_to_starting`, `vanilla_half`) unchanged
    - `MarieContext#respawnValueBehavior()` / `#respawnValueHandler()`
    - `MarieContext.Builder#respawnValueBehavior(Supplier)` / `#respawnValueHandler(BiConsumer)`
    - `TrackingResetSupport.applyRespawnValueBehavior(player, tracking)`
- `TooltipMessageRegistry` / `TooltipColorRegistry` (`dev.marie.framework.tooltips`)
    - Two-tier override lookup per `modId`: `config/<modId>/tooltips/tooltip_messages.json` / `tooltip_colors.json` (modpack-creator tier), overridden by `data/<modId>/marie/tooltips/tooltip_messages.json` / `tooltip_colors.json` (datapack tier)
    - Both files share a `byKey` / `byItem` shape; `byItem` takes priority over `byKey` for `getForItem(modId, itemId, key)`
    - `seedDefaultsIfAbsent(modId, defaults)`, `reload(modId)`, `loadFromDatapack(modId, resourceManager)`
- `MarieTooltipHelper` now checks `ExcludedItemsRegistry.isExcluded(itemId)` / `ScannerSpecRegistry.get().excludedItems()`
    - For excluded items, renders an `"excluded"`-keyed tooltip line/color sourced from `TooltipMessageRegistry` / `TooltipColorRegistry` instead of the usual unclassified-item line
- `ExcludedItemsRegistry` (`dev.marie.framework.scanner`), wired into `MarieBootstrap`'s lifecycle
- `ColorRegistry` / `ScannerSpecRegistry` now write a README (`COLORS_README.md`, `SCANNER_SPEC_README.md`) into the config folder on first load
    - Mirrors the pattern already used by `SourceClassificationRegistry` / `ExcludedItemsRegistry`
- `MarieComponent.mouseScrolled(double, double, double, double)` default method (no-op)
    - Forwarded unconditionally by `EditOverlayScreen` alongside the existing click/release/drag forwards
- `DoubleClickRecognizer` (`dev.marie.framework.ui.edit`)
    - Standalone per-button double-click gesture tracker (~300ms window, 4px tolerance)
    - Exposes separate left/right double-click callbacks for a component to wire into its own `mouseClicked` ahead of the normal `DraggableResizable.mouseClicked` forward
- `ComponentState.contentScale`
    - Optional, domain-agnostic scale for a component's content independently of its own box bounds (e.g. a zoomable inner element)
    - Defaults to `1.0`; `MarieConfigPersistenceProvider` reads/writes the new field with the same default fallback for existing persisted state that predates it
- `DraggableResizable(MarieComponent, Constraint, BiConsumer, Bounds)` constructor overload and `setParentBounds(Bounds)`
    - Clamps every subsequent drag/resize preview and commit to stay fully inside the given parent bounds, for a second tracker positioning a component's content within its own (separately tracked) box bounds
    - `null`/omitted parent bounds keeps the original unclamped behavior

### Changed

- `ItemClassifier.classify()`
    - Primary (non-fallback) scan path now runs community-tag/suffix/keyword scoring through the same `ResolutionStageHandler` mechanism as the runtime resolver cascade, instead of calling private methods directly; functionally unchanged
    - Stage instances are built once per `classify()` call and threaded through to `RecipeInheritanceStage.apply()` rather than re-instantiated per ingredient lookup
- `RecipeInheritanceStage.apply()` / `buildLookup()`
    - New `validKeys` / `componentFallbackStages` parameters to support the `ComponentClassifier` fallback
- `MarieTooltipHelper` moved from `dev.marie.framework.compat` to `dev.marie.framework.tooltips`
    - `MarieEmiPlugin` / `MarieReiPlugin` updated to the new import
- `SourceClassificationRegistry`'s override folder layout
    - `config/<modId>/overrides/source_classifications.json` moved under a dedicated `overrides/Overrides/` subfolder, with its README under `overrides/Read_Me/`
    - Existing flat-layout and legacy root-level `source_classifications.json` files are migrated/deleted automatically on load
- `SourceCollector` now merges qualifying recipe-inherited secondary scores into already-tagged items instead of skipping them outright
- `SourceApplicationPipeline.process()` no longer short-circuits when a `SourceClassificationRegistry` override is present
    - `ctx.sourceValueResolver()` / `ctx.sourceDeltaResolver()` are now always invoked and merged with the override (override wins per-key on conflict, resolver fills any gaps; `total` falls back to the resolver's computed delta when the override's `total` is 0/unset)
- `AutoGrowPanelContainer`'s footprint calculation simplified to respect a sibling's true committed size instead of also maxing against its natural height
- `ModuleRegistry` stays the static, singleton-facade shape shared by every other registry in this codebase (private constructor, static backing store) rather than becoming instantiable
    - Documented explicitly, since a prior design pass considered making it instantiable to support per-region module lists (e.g. a screen's left vs. right column)
    - Its `register`/`get` key parameter (previously documented only as "modId") is now documented as an opaque registry key: independent module lists are obtained by registering under distinct keys (conventionally `<modId>.<region>`, e.g. `"nourished.diet.right"`), not by constructing a second registry object
    - No behavioral change
- `@ApiStatus` tier corrections for classes that had real cross-mod consumers despite being marked (or left unmarked as) `Internal`
    - `MarieContext`, `ItemScanner`, `ModCompat`, `ScannerSpecRegistry`, `RecipeInheritanceResolver`, `TokenStemmer` (all marie-core) and `DraggableResizable` (marie-ui) are now `@ApiStatus.Experimental`
    - `MarieDataLoader` and `DraggableResizable` previously had no annotation at all; both now carry `@ApiStatus.Experimental` explicitly
    - `Internal` means "never referenced by any consuming mod" — these were all referenced by Nourished, so `Internal` was the wrong tier. No promotion to `Stable`; annotation-only change, no logic touched
- `RuntimeResolver` (`dev.marie.framework.runtime`) split
    - Cache-hit/miss counters, resolve-timing telemetry, and `computeSpread` extracted to a new package-private `RuntimeResolverStats`, held by composition
    - `RuntimeResolver` delegates to it internally; `getCacheStats()`, `invalidateCache()`, and `recordRecipeTimeout()` keep their existing public signatures and behavior unchanged
- `ItemClassifier` (`dev.marie.framework.scanner`) split
    - The namespace/negative-keyword/archetype/source-property/namespace-peer signal-analysis methods (formerly `analyzeSignal2/5/6/7/9`) extracted to a new package-private `ItemClassificationSignals`, called as static delegates from `classify(...)`
    - Contribution-scaling and result-building stay in `ItemClassifier` since they're shared across every signal, not signal-specific. No behavior change
- `ClassificationTraceFormatter` (`dev.marie.framework.classification`) split
    - The "Diagnostics" and "Developer Metadata" sections (`appendDiagnostics`, `appendDeveloperMetadata`, `collectDiagnostics`, the `DiagnosticEntry` record) extracted to a new package-private `ClassificationDiagnosticsFormatter`, called as static delegates from `format(...)`
    - Shared rendering helpers (`appendLine`, `appendKv`, `getString`, `collectSteps`, `SEP_HALF`) widened from `private` to package-private so the new class can reuse them instead of duplicating. No behavior change
- `MultiValueAnalysisWriter` (`dev.marie.framework.scanner.analysis`) trimmed, not split (already package-private with a single caller, so a class split wasn't warranted)
    - The repeated banner border/title block and `"Generated: " + timestamp` line, previously re-typed in every `write*Txt` method, are now `writeBanner(Writer, String)` / `writeGeneratedLine(Writer)` private helpers
    - Byte-identical output to before at every call site; no behavior change
- `MarieAPI` (`dev.marie.framework.api.marieapi`, 746 → 570 lines) split
    - Every public method's body moved into one of 13 new package-private delegate classes (`PlayerStateDelegate`, `ValueSourceRegistrationDelegate`, `EffectRegistrationDelegate`, `CompatRegistrationDelegate`, `SynergyRegistrationDelegate`, `ProfileMilestoneSeasonDelegate`, `ReportingRegistrationDelegate`, `ConfigValidationDelegate`, `TriggerRegistrationDelegate`, `TagAuditRegistrationDelegate`, `CommandCapabilityDelegate`, `SourceTriggerFiringDelegate`, `HookProviderRegistrationDelegate`), grouped by which registry/subsystem each method touches
    - Every `MarieAPI` public method keeps its exact signature, javadoc, `@ApiStatus` annotation, and declared exceptions and now just delegates in one line
    - No public API changes, no consumer call site is affected (verified against every `MarieAPI.register*`/`add*`/`get*` call in Nourished and Thermal_Systems). Pure internal reorganization; no behavior change
- `SourceApplicationPipeline` (`dev.marie.framework.handler`, 599 → 421 lines) split into 4 new package-private delegate classes
    - `SynergyStateRegistry` (per-player synergy cooldown/active-state maps, `clearPlayer`, `meetsSynergyCondition`)
    - `SourceApplyDebugReporter` (`submitSourceApplyDebug`, `buildMultiplierBreakdownJson`)
    - `ThresholdCrossingEvaluator` (`checkThresholdCrossings`, called from both `process()` and `writeDirectValue()`)
    - `ValueAbsorptionAdjuster` (`applySeasonalAbsorption`, `applyAbsorptionModifiers`)
    - `process()`'s synergy loops stay inline exactly as they were, now calling into `SynergyStateRegistry` instead of touching raw maps directly; `process()`'s own structure and the override/resolver merge logic are untouched
    - Every existing public method (`process`, `clearPlayer`, `writeDirectValue`, `applyDirectDelta`, `finalizeDirectWrite`, `resetSnapshotWarnings`) keeps its exact signature — verified against all 7 real callers (`SourceTriggerFiringDelegate`, `ValueDecayListener`, `AttachmentTrackingDataProvider`, `TrackingResetSupport`, `MarieCommandSupport`, `MarieKubeServerBindings`, `MariePlayerCommands`)
    - Confirmed `@ApiStatus.Internal` remains accurate: no consumer mod (Nourished, Thermal_Systems, ThinAir-ReLived) references this class directly. Pure internal reorganization; no behavior change

### Deprecated

- `MarieContext#deathNutritionBehavior()` / `#deathNutritionHandler()`
    - Use `#respawnValueBehavior()` / `#respawnValueHandler()`; old accessors now forward to the new ones
- `MarieContext.Builder#deathNutritionBehavior(Supplier)` / `#deathNutritionHandler(BiConsumer)`
    - Use `#respawnValueBehavior(Supplier)` / `#respawnValueHandler(BiConsumer)`; old builder methods now forward to the new ones

### Fixed

- `ItemClassifier`'s `COMMUNITY_TAG` classification signal label
    - Was a hardcoded `"c:foods/*"` literal left over from a mechanical extraction; now derived from `CommunityTagResolutionStage.tagDirectory()`
- Domain-agnostic audit: removed leftover food/nutrition-specific wording from marie-core comments (`MilestoneRegistry`, `DatapackSchema`, `SourceClassificationRegistry`, `TagAuditContext`) and from marie-ui (`MarieValueColors`) and marie-commands (`MarieMilestoneTemplateCommand`'s user-facing `_comment_value_key` output)
- `ScannerSpecRegistry.writeBundledTo` now uses the context classloader (matching `parseBundled`), so the bundled `scanner_spec.json` resolves correctly across module boundaries
- `SourceClassificationRegistry.parseEntry()` no longer throws an unguarded `NullPointerException` when an entry omits `source_id` (or any array element isn't a JSON object)
    - Malformed entries are now logged and skipped individually (by `source_id` if readable, otherwise by array index) instead of aborting the whole file and crashing mod bootstrap
    - `load()` also gained a broader catch as a second line of defense against whole-file corruption (invalid JSON syntax, non-array top-level value)
- Removed `TOOLTIP_COLORS_README.md` / `TOOLTIP_MESSAGES_README.md` from marie-ui's bundled resources
    - `writeReadmeIfAbsent` looks up `data/<modId>/config/...` using the _consuming_ mod's runtime `modId`, so a copy bundled under marie-ui's own `marieslib` namespace was never reachable by any real consumer, same bug class as `SOURCE_CLASSIFICATIONS_README`
    - Consumer mods must now bundle their own copy under their own `data/<modid>/config/` namespace
- Removed `COLORS_README.md` / `SCANNER_SPEC_README.md` from marie-core's bundled resources for the same reason
    - `ColorRegistry`/`ScannerSpecRegistry`'s `writeReadmeIfAbsent` resolve `data/<modId>/config/...` by the consuming mod's runtime `modId`, so the copies bundled under marie-core's own `marieslib` namespace were never reachable
    - Only affects the READMEs — `ScannerSpecRegistry`'s bundled `scanner_spec.json` _defaults_ use a separate, already-correct resolution path (`data/<modId>/<modId>/scanner/scanner_spec.json`, supplied by each consumer) and were not touched
- Removed dead-code `dev.marie.framework.registry.ExportResolverRegistry` (and its nested `Core`/`Entry` types)
    - Zero callers anywhere in the codebase, and not wired into `MarieApiRegistries`'s freeze/reset lifecycle like every other registry in that package
    - Its `AbstractRegistry`-based frozen/locked design predates and directly contradicts the shipped `dev.marie.framework.export.ExportResolverRegistry` (0.1.1-beta.1), which is documented as never frozen so resolvers can register throughout mod init — an abandoned false start, not an in-progress migration. `export.ExportResolverRegistry` is untouched
    - `TagRuleRegistry` / `TagAuditContextRegistry` Javadoc comments that referenced "ExportResolverRegistry" by bare name now name `export.ExportResolverRegistry` explicitly, now that only one class exists
- `MarieAPI.registerExportResolver(ExportResolver<T>)` (single-arg overload) no longer silently registers a permanently-null export
    - It previously called `ExportResolverRegistry.register(resolver.resolverId(), () -> null)`, discarding the passed-in `resolver` entirely — any dump run through this overload would always produce nothing
    - Root cause: `ExportResolver<T>` carries no registry key, so this overload structurally has no way to know which registry to iterate (unlike the two-arg overload, which takes a `ResourceKey<Registry<T>>` explicitly) — there is no correct implementation for it, not just a wiring mistake
    - Confirmed via grep that no real consumer (Nourished, Thermal_Systems, ThinAir-ReLived) has ever called this overload — Nourished's only `registerExportResolver` call site uses the two-arg form — so this was dead code from the moment it was introduced, not a regression that silently broke a working export
    - The delegate body (now in `HookProviderRegistrationDelegate`) throws `UnsupportedOperationException` instead of silently no-oping, and the method is marked `@Deprecated` with a `@deprecated` javadoc pointing callers at the two-arg overload. No signature change; both overloads keep their exact public shape

### Notes

- Version: **0.1.1-beta.4**
- `TrackingResetSupport.applyDeathNutritionOnRespawn` (internal-only, `@ApiStatus.Internal`) was renamed directly to `applyRespawnValueBehavior` with no deprecated forwarder, since it isn't public API
- Flagged but intentionally untouched: `CommunityTagResolutionStage`'s underlying `"foods/"` NeoForge tag directory value (real scoring behavior, not a naming artifact) and marie-ui's javadoc comments documenting generalization from Nourished's original screens (attribution, not a leak)

---

## [MariesLib 0.1.1-beta.3] — 2026-06-30

Source-pair synergy value buffs, unified source classification, tag-audit/export command wiring, and player logout cleanup for synergy state.

### Added

- `MarieAPI.registerConfigValidator(validator)` now validates `modId()` (must be non-empty and registered); throws `IllegalArgumentException` otherwise
- `SourcePairSynergy.getValueModifier()` / `getModifierDurationTicks()` (`@ApiStatus.Stable`)
- Builder: `valueModifier(float)`, `modifierDurationTicks(int)`
- Temporary value multiplier applied when synergy fires
- `SynergyBuffTracker` (tracking)
- Per-player, per-value-key temporary modifier store
- `activate(playerId, valueKey, modifier, expiryTick)`
- `getActiveModifier(playerId, valueKey, currentTick)` (auto-expire)
- `clearPlayer(playerId)`
- `SynergyAbsorptionModifier` (`AbsorptionModifier`)
- Applies `SynergyBuffTracker` modifiers during absorption
- Registered via `MariesLibBootstrap`
- `SourceApplicationPipeline.clearPlayer(playerId)`
- Clears synergy runtime state on logout
- Wired into `PlayerLoggedOutEvent`
- Datapack schema:
- `value_modifier`
- `modifier_duration_ticks`
- `MarieDataLoader.parseSynergy` / `parseSourcePairSynergy`
- Fully implemented datapack loading for synergies
- `CommandCapability` / `CommandCapabilityRegistry` (`@ApiStatus.Experimental`)
- Mod-registered command extensions keyed by `(modId, capability)`
- `ExportWriter.writeExport(resolverId)` / `RegistryExporter`
- Writes `config/<modid>/<resolverId>_export.json`
- Exposed via `/marieslib dump <resolverId>`
- `TagAuditReportWriter`
- Writes full tag audit reports to disk
- Hooked into `/marieslib audit_tags <modid>`
- `MariesLibCommand.runDump` / `runAuditTags`
- `SourceClassificationRegistry` rewrite
- Config-backed registry replacing `SourceOverrideRegistry` + `SourceValueRegistry`
- Supports migration from legacy files
- `RuntimeResolver` trace enhancement
- Adds `EXTERNAL_CLASSIFICATION` trace step when overrides apply

### Changed

- Removed `SourceOverrideRegistry` and `SourceValueRegistry`
- Fully merged into `SourceClassificationRegistry`
- `MarieAPI` reorganized (no behavior change)
- Registration methods regrouped (export, validators, audit, commands)
- `MariesLib` constructor
- No longer auto-bootstraps `MariesLibBootstrap`
- Consumers must explicitly bootstrap
- `RecipeInheritanceResolver.buildIndex`
- Now actually builds recipe index
- `PresetRegistry.PresetValues`
- Replaced fixed schema with raw `JsonObject values`
- `MarieValueColors.resolvedDefaultArgb`
- Now prioritizes definition override → palette → base color
- `ModCompat` resource loading
- Switched to context classloader-first lookup
- `ScannerSpecRegistry.parseBundled`
- Uses context classloader for mod JAR compatibility

### Fixed

- `ColorRegistry.parseArgbString`
- Fixed `0x` parsing inconsistency using `Long.parseLong`
- `MarieDebugCommand` trace dump overwrite bug
- Now writes unique timestamped files per run

### Notes

- Version: **0.1.1-beta.3**

---

## [MariesLib 0.1.1-beta.2] — 2026-06-27

Config validation, tag auditing system, curve math utilities, and scanner spec resilience improvements.

### Added

- `ConfigValidator` (`@ApiStatus.Stable`)
- `ConfigValidatorRegistry` (`@ApiStatus.Internal`)
- `Finding` (validation issue model)
- `ValidationResult` (PASS / WARN / FAIL + findings)
- `ValidationRunner.runForMod(modId)`
- Tag audit system:
- `TagAuditContext`
- `TagRule`
- `TagIssue`
- `TagFixSuggestion`
- `TagAuditSeverity`
- `TagReport`
- `TagScanner.scan(context)`
- `TagRuleRegistry`
- `TagAuditContextRegistry`
- `CurveGrid`
- 2D bilinear interpolation over normalized axes
- `CurveGridJson`
- `SourceClassificationRegistry` (read-only external view)
- `MarieAPI.registerConfigValidator`
- `MarieAPI.registerExportResolver` (overloads)
- `MarieAPI.registerTagRule`
- `MarieAPI.registerTagAuditContext`
- `/set_all <value> <player>` command
- `/validate` command
- `/analyze <item>` command
- `MarieValidationCommands`
- `SourceRegistry.getAllExternalClassifications`

### Changed

- `MariesLibCommand`
- Removed duplicate command tree registration
- `RecipeInheritanceResolver.getIngredients()`
- Made public; improved index construction
- `MarieValueColors`
- Expanded accessors for audit/export tooling

### Fixed

- `ScannerSpecRegistry.writeBundledTo`
- Now returns boolean
- Eliminates false warnings for missing bundled specs

### Notes

- Version: **0.1.1-beta.2**

---

## [MariesLib 0.1.1-beta.1] — 2026-06-20

Registry export framework, scanner fix, explicit bootstrap requirement, and registry lifecycle exposure.

### Added

- `ExportResolver<T>`
- `ExportResolverRegistry`
- `RegistryExporter`
- `ExportWriter.writeExport`
- `/marieslib dump <resolverId>`
- `/marie dump <resolverId>`
- `MarieAPI.registerExportResolver`
- `ValueRegistry.isFrozen()`

### Changed

- `MariesLib` constructor
- Removed implicit bootstrap behavior
- Requires explicit `MariesLibBootstrap.attach/bootstrap`

### Fixed

- `ScannerSpecRegistry` resource loading
- Switched to context classloader for mod JAR compatibility

### Notes

- Version: **0.1.1-beta.1**

---

## [MariesLib 0.1.0-beta.5] — 2026-06-16

Color resolution fixes and decay/config correctness improvements.

### Added

- `MarieValueColors.resolvedDefaultArgb`
- `IMarieLibConfig.decayRateFor`
- `MarieLibContext.Builder.decayRateFor`

### Fixed

- `ColorRegistry.parseArgbString` (`0x` handling fixed)
- Decay system now respects config overrides
- Commands now use config-based decay values
- API classification persistence fixed across reloads
- Critical toast fallback label fixed

### Notes

- Version: **0.1.0-beta.5**

---

## [MariesLib 0.1.0-beta.4] — 2026-06-16

Milestone system expansion, datapack effects, and tooling improvements.

### Added

- `ValueDefinition.colorOverride`
- `MilestoneRegistry.getForAll()`
- Cross-nutrient milestone tracking (`"all"`)
- `generate_milestone_template` command
- `MarieDataLoader.parseEffect`
- `SourceRegistry.clearSessionWarnings`

### Changed

- Datapack validator ignores `_comment_` fields
- Registration logging reduced/noise control improved
- Dependency pins fixed for JEI/REI/EMI
- CI workflow updated

### Notes

- Version: **0.1.0-beta.4**

---

## [MariesLib 0.1.0-beta.3] — 2026-06-15

Milestone tracking system implementation.

### Added

- `MilestoneTriggeredEvent`
- `MilestoneProgressData`
- `MilestoneProgressAttachment`
- `MilestoneTracker`
- `MarieDatapackCallbacks`
- Datapack milestone loader
- Bootstrap milestone wiring
- KubeJS milestone binding (`milestoneTriggered`)

### Architecture

- Pipeline hook: `SourceApplicationPipeline → MilestoneTracker`
- Reward system: effects + advancements
- Feature flag gating for milestones

### Notes

- Version: **0.1.0-beta.3**

---

## [MariesLib 0.1.0-beta.2] — 2026-06-13

Core stability, tracking pipeline, and KubeJS integration.

### Added

- `ValueModifierContext`
- `DeathNutritionBehavior`
- `TrackingResetSupport`
- `AttachmentTrackingDataProvider`
- `SourceApplicationPipeline` direct write APIs
- Full KubeJS event bridge set
- EMI/REI services

### Changed

- `ValueModifierEvent` refactored to context-based model
- Player lifecycle now uses `DeathNutritionBehavior`
- API stability annotations expanded
- Commands refactored to pipeline-based writes
- Build system improvements

### Breaking Changes

- KubeJS internal package relocation (non-breaking for scripts)

### Notes

- Version: **0.1.0-beta.2**

---

## [MariesLib 2.0.0] — 2026-06-11

Library purification release (domain-agnostic extraction from Nourished).

### Breaking Changes

- Removed all gameplay config ownership
- Replaced `ModuleCache` → `FeatureFlagCache`
- `IMarieLibConfig` → `MarieLibSettings`
- Handler renames across pipeline
- Command namespace split (library vs consumer mods)
- Preset system fully moved to consuming mods

### Added

- `MarieModRegistry`
- `MarieModFeatureFlags`
- `FeatureFlagCache`
- Library-only `/marieslib` commands
- Multi-mod registry architecture
- Scanner + tracking infrastructure foundation
- Milestone tracking system foundation
- Datapack loaders (values/effects/synergies/milestones)
- Registry framework rewrite
- Client UX tooling (toasts, preset UI, etc.)

### Migration Notes

- Move all gameplay config to consuming mods
- Implement feature flag syncing via `FeatureFlagCache`
- Delete legacy preset files
- Replace `ModuleCache` calls with feature flag API

### Notes

- Version: **2.0.0**

---

## [MariesLib 0.1.0-beta.2] — 2026-06-10

Initial extraction beta.

### Added

- Core shared library bootstrap
- Scanner pipeline system
- Classification trace system
- Runtime resolver system
- Tracking system (decay, synergy, milestones)
- Compat system (3-tier resolution)
- Datapack loaders (partial)
- Module system (`ModuleCache`)
- Preset system (Cloth Config integration)
- Full `/marie` command suite
- Client UI (HUD, toasts, config screens)
- Source application pipeline
- Mod lifecycle integration (`MarieLibContext`)
- KubeJS + JEI/REI/EMI integrations

### Architecture

- Registry framework with snapshots + reload locks
- Handler pipeline: source → decay → effects → events
- Hot-path caching systems
- Context-based bootstrap model

### Notes

- First stable beta extraction baseline
- Version: **0.1.0-beta.2**

---

## [MariesLib 0.1.0-beta.1] — 2026-06-10

First beta release.

### Added

- Standalone bootstrap system
- Cloth Config integration
- Source trigger pipeline
- Value scaling system (`amountScale`)
- Debug tooling
- Import/export system
- Full handler audit hardening
- Lazy datapack loading system
- Registry safety checks

### Architecture

- `MarieLibContext` bootstrap model
- Handler-based pipeline system
- Client/server separation enforcement

### Notes

- Version: **0.1.0-beta.1**
