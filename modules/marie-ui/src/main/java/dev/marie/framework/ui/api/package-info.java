/**
 * Public MarieUI facade API.
 *
 * <p>This package holds the slim, static entry-point classes consumer mods are meant to call
 * without reading marie-ui's internals — {@code MarieNotifications} and {@code MarieCommandCenter}.
 * Each facade's actual implementation (rendering, hit-testing, persistence, registries) stays in
 * its own subsystem package (e.g. {@code dev.marie.framework.notification}, {@code
 * dev.marie.framework.ui.commandcenter}); only the front door lives here. Mirrors marie-core's
 * {@code dev.marie.framework.api} package, which plays the same role for MarieLib's non-UI
 * surface.
 *
 * <p>The editor-facing facades ({@code MarieScaleConfig}, {@code EditModeCoordinator}, {@code
 * MarieToolbox}, {@code MarieWidgets}, and their peers) live in the equivalent {@code
 * dev.marie.framework.ui.api} package of the marie-editor module.
 */
@ApiStatus.Experimental
package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
