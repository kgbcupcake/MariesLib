package dev.marie.framework.api.modscan;

import dev.marie.framework.api.ApiStatus;

/**
 * What changed in the mod set since the previous scan (all files count as added on the first run),
 * and how many mod files are being scanned in total.
 */
@ApiStatus.Experimental
public record ModScanStart(int added, int removed, int changed, int totalFiles) {}
