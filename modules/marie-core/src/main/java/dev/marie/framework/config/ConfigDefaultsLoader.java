package dev.marie.framework.config;

import dev.marie.framework.api.ApiStatus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.util.MarieJsonUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@ApiStatus.Internal
public final class ConfigDefaultsLoader {

    private static final Gson GSON = new Gson();

    private ConfigDefaultsLoader() {}

    public static JsonObject loadOrEmpty(String resourcePath) {
        try (InputStream in = ConfigDefaultsLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return new JsonObject();
            }
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                return obj != null ? obj : new JsonObject();
            }
        } catch (Exception ex) {
            MarieCore.LOGGER.warn("[MarieLib] Failed to load config defaults from {}", resourcePath, ex);
            return new JsonObject();
        }
    }

    public static double getDouble(JsonObject obj, String key, double fallback) {
        return MarieJsonUtils.getOptionalDouble(obj, key, fallback);
    }

    public static int getInt(JsonObject obj, String key, int fallback) {
        return MarieJsonUtils.getOptionalInt(obj, key, fallback);
    }

    public static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        return MarieJsonUtils.getOptionalBoolean(obj, key, fallback);
    }

    public static String getString(JsonObject obj, String key, String fallback) {
        return MarieJsonUtils.getOptionalString(obj, key, fallback);
    }
}
