package dev.marie.framework.diagnostics.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.registry.ModIds;
import dev.marie.framework.util.MarieFiles;

import java.io.IOException;
import java.nio.file.Path;

@ApiStatus.Internal
public final class ReportStore {

    public static final String FOLDER = "crash-reports";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .serializeNulls().create();

    private final Path root;

    public ReportStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public Path folder(String modId) {
        ModIds.require(modId, "ReportStore");
        Path dir = root.resolve(modId).resolve(FOLDER).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("ReportStore: " + dir + " is outside " + root);
        }
        return dir;
    }

    public String displayPath(String modId, String fileName) {
        return modId + "/" + FOLDER + "/" + fileName;
    }

    public String write(String modId, String fileName, JsonObject report, int retention) throws IOException {
        if (!fileName.startsWith(IncidentIds.FILE_PREFIX) || fileName.contains("/") || fileName.contains("\\")) {
            throw new IllegalArgumentException("ReportStore: bad report file name " + fileName);
        }
        Path dir = folder(modId);
        MarieFiles.writeAtomically(dir.resolve(fileName), w -> GSON.toJson(report, w));
        try {
            ReportRetention.prune(dir, retention);
            return null;
        } catch (IOException | RuntimeException e) {
            return "retention cleanup failed in " + dir + ": " + e;
        }
    }

    public Path root() {
        return root;
    }
}
