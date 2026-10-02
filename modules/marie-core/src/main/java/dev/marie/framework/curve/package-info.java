/**
 * Reusable infrastructure for 2D bilinear response curves. Domain-agnostic —
 * consuming mods define what the two input axes and the output represent.
 *
 * <p>Subpackages: {@code math} (the pure CurveGrid type, zero dependencies
 * beyond the JDK) and {@code serialization} (Gson JSON conversion, kept
 * separate so the math type never depends on Gson).</p>
 */
@ApiStatus.Stable
package dev.marie.framework.curve;

import dev.marie.framework.api.ApiStatus;
