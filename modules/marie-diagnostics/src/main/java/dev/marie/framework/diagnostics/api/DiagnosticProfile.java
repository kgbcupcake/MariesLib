package dev.marie.framework.diagnostics.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.registry.ModIds;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A mod's packages, retention and providers. */
@ApiStatus.Experimental
public final class DiagnosticProfile {

    public static final int DEFAULT_RETENTION = 20;
    public static final int MAX_RETENTION = 200;
    public static final int MAX_PROVIDER_ID_LENGTH = 64;

    private final String modId;
    private final List<String> packages;
    private final int retention;
    private final Map<String, DiagnosticProvider> providers;

    private DiagnosticProfile(Builder b) {
        this.modId = b.modId;
        this.packages = Collections.unmodifiableList(new ArrayList<>(b.packages));
        this.retention = b.retention;
        this.providers = Collections.unmodifiableMap(new LinkedHashMap<>(b.providers));
    }

    public static Builder builder(String modId) {
        return new Builder(ModIds.require(modId, "DiagnosticProfile"));
    }

    public String modId() {
        return modId;
    }

    public List<String> packages() {
        return packages;
    }

    public int retention() {
        return retention;
    }

    public Map<String, DiagnosticProvider> providers() {
        return providers;
    }

    public static final class Builder {
        private final String modId;
        private final List<String> packages = new ArrayList<>();
        private int retention = DEFAULT_RETENTION;
        private final Map<String, DiagnosticProvider> providers = new LinkedHashMap<>();

        private Builder(String modId) {
            this.modId = modId;
        }

        public Builder packages(String... packagePrefixes) {
            for (String p : packagePrefixes) {
                String trimmed = Objects.requireNonNull(p, "package").trim();
                if (trimmed.isEmpty() || trimmed.equals(".")) {
                    throw new IllegalArgumentException("DiagnosticProfile: blank package prefix");
                }
                String normalized = trimmed.endsWith(".") ? trimmed : trimmed + ".";
                if (!packages.contains(normalized)) {
                    packages.add(normalized);
                }
            }
            return this;
        }

        public Builder retention(int maxReports) {
            if (maxReports < 1 || maxReports > MAX_RETENTION) {
                throw new IllegalArgumentException("DiagnosticProfile: retention must be 1.."
                        + MAX_RETENTION + ", was " + maxReports);
            }
            this.retention = maxReports;
            return this;
        }

        public Builder provider(String id, DiagnosticProvider provider) {
            Objects.requireNonNull(provider, "provider");
            if (id == null || id.isBlank() || id.length() > MAX_PROVIDER_ID_LENGTH) {
                throw new IllegalArgumentException("DiagnosticProfile: provider id must be 1.."
                        + MAX_PROVIDER_ID_LENGTH + " characters");
            }
            if (providers.putIfAbsent(id, provider) != null) {
                throw new IllegalArgumentException("DiagnosticProfile: duplicate provider id '" + id + "'");
            }
            return this;
        }

        public DiagnosticProfile build() {
            return new DiagnosticProfile(this);
        }
    }
}
