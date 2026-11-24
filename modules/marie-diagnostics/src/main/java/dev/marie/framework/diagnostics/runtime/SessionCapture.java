package dev.marie.framework.diagnostics.runtime;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.diagnostics.capture.ContextCapture;

@ApiStatus.Internal
public final class SessionCapture implements ContextCapture {

    @Override
    public String section() {
        return "session";
    }

    @Override
    public JsonElement capture() {
        JsonObject o = new JsonObject();
        o.addProperty("sessionId", MarieCore.SESSION_ID.toString());
        try {
            o.addProperty("modlistHash", MarieCore.getModlistHash());
        } catch (Throwable t) {
            o.addProperty("modlistHash", "unavailable: " + t.getClass().getSimpleName());
        }
        return o;
    }
}
