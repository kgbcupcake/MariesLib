package dev.marie.framework.api.modscan;

import dev.marie.framework.api.ApiStatus;

import java.time.Duration;

/**
 * Outcome of a scan, counted per (file, extractor) pair: {@code scanned} were extracted this run,
 * {@code skipped} were answered from the cache, {@code failed} threw and have no result.
 */
@ApiStatus.Experimental
public record ModScanFinish(Duration duration, int scanned, int skipped, int failed) {}
