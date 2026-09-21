package dev.marie.framework.api.modscan;

import com.google.gson.JsonElement;
import dev.marie.framework.api.ApiStatus;

import java.io.IOException;

/**
 * Plug-in point: reads one mod file and returns whatever a consumer wants to know about it.
 *
 * <p>Runs on the scan thread (never the game thread), only for files that are new, changed, or have
 * no cached result for this extractor's {@link #id()} and {@link #version()}. Must be
 * side-effect free and must not load classes from the file. Bump {@link #version()} whenever the
 * shape or meaning of the result changes, so its cached results are rebuilt.</p>
 */
@ApiStatus.Experimental
public interface ModScanExtractor {

    /** Stable id, unique within the registering namespace, e.g. {@code "mymod:recipe_types"}. */
    String id();

    /** Result format version; a different value invalidates this extractor's cached results only. */
    int version();

    /**
     * Extracts a result for one mod file. Throwing is fine: the file is logged, counted as failed and
     * skipped; nothing is cached for it.
     */
    JsonElement extract(ModFileHandle file) throws IOException;
}
