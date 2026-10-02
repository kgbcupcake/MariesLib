package dev.marie.framework.data;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Experimental
public record DatapackDiagnostic(Severity severity, String filePath, String field, String message) {

    public enum Severity {
        WARN,
        ERROR
    }

    @Override
    public String toString() {
        return "[" + severity + "] " + filePath + " > " + field + ": " + message;
    }
}
