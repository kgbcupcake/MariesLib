/**
 * Low-level classification signal pipeline.
 *
 * <p>This package implements the multi-step signal evaluation that assigns
 * confidence-weighted value scores to a source item. It is invoked by the
 * scanner layer and should not be called directly by addon mods.</p>
 *
 * <p>Key types:
 * <ul>
 *   <li>{@link ClassificationPipeline} — executes all signal stages in order</li>
 *   <li>{@link ClassificationTrace} — debug trace of each pipeline step</li>
 *   <li>{@link ClassificationTraceStep} — a single step in the trace</li>
 * </ul></p>
 */
@javax.annotation.ParametersAreNonnullByDefault
@ApiStatus.Internal
package dev.marie.framework.classification;

import dev.marie.framework.api.ApiStatus;
