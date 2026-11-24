package dev.marie.framework.diagnostics.runtime;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.config.MarieModFeatureFlags;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.core.MarieModRegistry;
import dev.marie.framework.data.DatapackDiagnostic;
import dev.marie.framework.data.DatapackDiagnostics;
import dev.marie.framework.diagnostics.api.DiagnosticProvider;
import dev.marie.framework.diagnostics.api.DiagnosticSection;
import dev.marie.framework.diagnostics.api.ProviderContext;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class MariesLibStateProvider implements DiagnosticProvider {

    static final int MAX_DATAPACK_ERRORS = 10;

    @Override
    public void contribute(ProviderContext context, DiagnosticSection section) {
        List<MarieContext> contexts = MarieModRegistry.getAll();
        section.entry("attachedMods", contexts.stream().map(MarieContext::modId).collect(Collectors.joining(", ")));

        section.entry("featureFlags.synced", FeatureFlagCache.isInitialized());
        MarieModFeatureFlags flags = FeatureFlagCache.get();
        for (RecordComponent c : MarieModFeatureFlags.class.getRecordComponents()) {
            try {
                section.entry("featureFlags." + c.getName(), c.getAccessor().invoke(flags));
            } catch (ReflectiveOperationException e) {
                section.entry("featureFlags." + c.getName(), "unreadable");
            }
        }

        DatapackDiagnostics datapacks = DatapackDiagnostics.getInstance();
        section.entry("datapack.lastReload", datapacks.getSummary());
        List<DatapackDiagnostic> errors = datapacks.getErrors();
        for (int i = 0; i < Math.min(errors.size(), MAX_DATAPACK_ERRORS); i++) {
            section.entry("datapack.error." + (i + 1), errors.get(i));
        }
        if (errors.size() > MAX_DATAPACK_ERRORS) {
            section.entry("datapack.moreErrors", errors.size() - MAX_DATAPACK_ERRORS);
        }
    }
}
