package dev.marie.framework.diagnostics.capture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@ApiStatus.Internal
public final class ThrowableCapture {

    public static final int MAX_FRAMES = 200;
    public static final int MAX_CAUSE_FRAMES = 50;
    public static final int MAX_CAUSES = 10;
    public static final int MAX_MESSAGE_CHARS = 2000;

    private ThrowableCapture() {}

    public static JsonObject describe(Throwable t) {
        JsonObject out = new JsonObject();
        out.addProperty("exceptionType", t.getClass().getName());
        out.addProperty("message", message(t));
        out.add("stackTrace", frames(t.getStackTrace(), MAX_FRAMES));
        JsonArray causes = new JsonArray();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        seen.add(t);
        Throwable cause = t.getCause();
        while (cause != null && seen.add(cause)) {
            if (causes.size() >= MAX_CAUSES) {
                out.addProperty("causesTruncated", true);
                break;
            }
            JsonObject c = new JsonObject();
            c.addProperty("exceptionType", cause.getClass().getName());
            c.addProperty("message", message(cause));
            c.add("stackTrace", frames(cause.getStackTrace(), MAX_CAUSE_FRAMES));
            causes.add(c);
            cause = cause.getCause();
        }
        out.add("causes", causes);
        return out;
    }

    public static JsonArray frames(StackTraceElement[] trace, int max) {
        JsonArray out = new JsonArray();
        int n = Math.min(trace.length, max);
        for (int i = 0; i < n; i++) {
            out.add(String.valueOf(trace[i]));
        }
        if (trace.length > max) {
            out.add("... " + (trace.length - max) + " more frame(s)");
        }
        return out;
    }

    private static String message(Throwable t) {
        String m;
        try {
            m = t.getMessage();
        } catch (Throwable e) {
            return "<getMessage() threw " + e.getClass().getName() + ">";
        }
        if (m == null) {
            return null;
        }
        return m.length() > MAX_MESSAGE_CHARS ? m.substring(0, MAX_MESSAGE_CHARS) + "..." : m;
    }
}
