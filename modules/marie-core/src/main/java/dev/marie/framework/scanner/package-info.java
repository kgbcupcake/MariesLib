/**
 * Source item scanner and classification orchestrator.
 *
 * <p>This package provides the high-level scanning API that discovers all
 * registered items and routes them through the classification pipeline.
 * It also writes scan reports and tag recommendations.</p>
 *
 * <p>Key types:
 * <ul>
 *   <li>{@link ItemScanner} — entry point; call scanAndApply() at server start</li>
 *   <li>{@link ItemClassifier} — classifies a single item using all registered signals</li>
 *   <li>{@link ClassificationResult} — output of a single-item classification</li>
 *   <li>{@link ScanCache} — persists results between server restarts</li>
 * </ul></p>
 *
 * <p>The scanner layer is separate from the classification pipeline so that
 * bulk scan behaviour (caching, diff reporting, recipe inheritance) does not
 * bleed into the per-item signal logic.</p>
 */
@ApiStatus.Experimental
package dev.marie.framework.scanner;

import dev.marie.framework.api.ApiStatus;
