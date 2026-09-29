package dev.marie.framework.util;

import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;

/**
 * Gson {@link JsonObject} helpers, modeled on {@link dev.marie.framework.data.MarieDataLoader}.
 */
@ApiStatus.Stable
public final class MarieJsonUtils {

    private MarieJsonUtils() {}

    public static String getRequiredString(JsonObject obj, String key) {
        if (!obj.has(key)) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return obj.get(key).getAsString();
    }

    public static String getOptionalString(JsonObject obj, String key, String defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsString();
    }

    public static int getOptionalInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }

    public static float getOptionalFloat(JsonObject obj, String key, float defaultValue) {
        return obj.has(key) ? obj.get(key).getAsFloat() : defaultValue;
    }

    public static double getOptionalDouble(JsonObject obj, String key, double defaultValue) {
        return obj.has(key) ? obj.get(key).getAsDouble() : defaultValue;
    }

    public static boolean getOptionalBoolean(JsonObject obj, String key, boolean defaultValue) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : defaultValue;
    }
}
