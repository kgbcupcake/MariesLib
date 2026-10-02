/**
 * Public MarieEditor facade API.
 *
 * <p>This package holds the slim, static entry-point classes consumer mods are meant to call
 * without reading marie-editor's internals — {@code MarieScaleConfig}, {@code
 * EditModeCoordinator}, {@code MarieToolbox}, {@code MarieWidgets}, and their peers. Each
 * facade's actual implementation (rendering, hit-testing, persistence, registries) stays in its
 * own subsystem package (e.g. {@code dev.marie.framework.ui.edit}, {@code
 * dev.marie.framework.ui.scaleconfig}, {@code dev.marie.framework.ui.toolbox}); only the front
 * door lives here. Mirrors marie-ui's {@code dev.marie.framework.ui.api} package, which plays
 * the same role for MarieLib's non-editor UI surface.
 */
@ApiStatus.Experimental
package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
