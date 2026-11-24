package dev.marie.framework.diagnostics.report;

import dev.marie.framework.api.ApiStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@ApiStatus.Internal
public final class IncidentIds {

    public static final String FILE_PREFIX = "incident-";
    public static final String FILE_SUFFIX = ".json";

    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());

    private IncidentIds() {}

    public static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String fileName(Instant timestamp, String id) {
        return FILE_PREFIX + FILE_TIME.format(timestamp) + "-" + id + FILE_SUFFIX;
    }
}
