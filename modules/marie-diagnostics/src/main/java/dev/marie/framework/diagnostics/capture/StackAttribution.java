package dev.marie.framework.diagnostics.capture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.diagnostics.api.DiagnosticProfile;
import dev.marie.framework.diagnostics.registry.ModIds;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public final class StackAttribution {

    public static final String MARIESLIB_PACKAGE = "dev.marie.framework.";
    public static final int MAX_RELEVANT_FRAMES = 40;

    private StackAttribution() {}

    public record Result(List<String> implicatedMods, JsonArray relevantFrames) {

        @Nullable
        public String primary() {
            return implicatedMods.isEmpty() ? null : implicatedMods.get(0);
        }
    }

    public record Trace(String exception, StackTraceElement[] frames) {}

    public static Result attribute(Throwable t, Collection<DiagnosticProfile> profiles) {
        List<Trace> traces = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable c = t; c != null && seen.add(c) && traces.size() <= ThrowableCapture.MAX_CAUSES; c = c.getCause()) {
            traces.add(0, new Trace(c.getClass().getName(), c.getStackTrace()));
        }
        return attribute(traces, profiles);
    }

    public static Result attributeSite(StackTraceElement[] site, Collection<DiagnosticProfile> profiles) {
        return attribute(List.of(new Trace("reportSite", site)), profiles);
    }

    public static Result attribute(List<Trace> traces, Collection<DiagnosticProfile> profiles) {
        LinkedHashSet<String> mods = new LinkedHashSet<>();
        Set<String> listed = new HashSet<>();
        JsonArray relevant = new JsonArray();
        int dropped = 0;
        for (Trace trace : traces) {
            for (StackTraceElement frame : trace.frames()) {
                String cls = frame.getClassName();
                String mod = ownerOf(cls, profiles);
                boolean mariesLib = mod != null ? ModIds.MARIESLIB.equals(mod) : cls.startsWith(MARIESLIB_PACKAGE);
                if (mod == null && !mariesLib) {
                    continue;
                }
                if (mod != null) {
                    mods.add(mod);
                }
                String text = String.valueOf(frame);
                if (!listed.add(text)) {
                    continue;
                }
                if (relevant.size() >= MAX_RELEVANT_FRAMES) {
                    dropped++;
                    continue;
                }
                JsonObject f = new JsonObject();
                f.addProperty("frame", text);
                f.addProperty("exception", trace.exception());
                f.addProperty("mod", mod);
                f.addProperty("isMariesLib", mariesLib);
                relevant.add(f);
            }
        }
        if (dropped > 0) {
            JsonObject more = new JsonObject();
            more.addProperty("frame", "... " + dropped + " more matching frame(s)");
            relevant.add(more);
        }
        return new Result(List.copyOf(mods), relevant);
    }

    @Nullable
    public static String ownerOf(String className, Collection<DiagnosticProfile> profiles) {
        String best = null;
        int bestLength = -1;
        for (DiagnosticProfile profile : profiles) {
            for (String prefix : profile.packages()) {
                if (prefix.length() > bestLength && className.startsWith(prefix)) {
                    best = profile.modId();
                    bestLength = prefix.length();
                }
            }
        }
        return best;
    }
}
