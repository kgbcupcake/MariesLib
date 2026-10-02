package dev.marie.framework.api.modscan;

import dev.marie.framework.api.ApiStatus;

import java.util.List;

/**
 * Difference between two mod sets, by file (a changed size or mtime counts as changed) and by mod id.
 * All lists are sorted; {@link #isEmpty()} is true when nothing changed.
 */
@ApiStatus.Experimental
public record ModSetDiff(
        List<String> addedFiles,
        List<String> removedFiles,
        List<String> changedFiles,
        List<String> addedMods,
        List<String> removedMods,
        List<VersionChange> versionChangedMods) {

    public record VersionChange(String modId, String oldVersion, String newVersion) {}

    public static final ModSetDiff EMPTY = new ModSetDiff(List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    public ModSetDiff {
        addedFiles = List.copyOf(addedFiles);
        removedFiles = List.copyOf(removedFiles);
        changedFiles = List.copyOf(changedFiles);
        addedMods = List.copyOf(addedMods);
        removedMods = List.copyOf(removedMods);
        versionChangedMods = List.copyOf(versionChangedMods);
    }

    public boolean isEmpty() {
        return addedFiles.isEmpty() && removedFiles.isEmpty() && changedFiles.isEmpty()
                && addedMods.isEmpty() && removedMods.isEmpty() && versionChangedMods.isEmpty();
    }
}
