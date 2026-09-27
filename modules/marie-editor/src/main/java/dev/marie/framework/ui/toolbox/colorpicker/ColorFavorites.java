package dev.marie.framework.ui.toolbox.colorpicker;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.core.MarieCore;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A small palette of saved colors shared by every {@link ColorPicker}, across every mod using
 * MarieUI — one JSON file, one flat list of {@code 0xRRGGBB} ints, most-recently-added first. Not
 * namespaced per mod/module: like a real color picker's "custom colors" swatches, a saved shade is
 * useful wherever the player opens a picker next, not just the one it was saved from.
 */
@ApiStatus.Internal
final class ColorFavorites {

    private static final Gson GSON = new Gson();
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("marieslib-color-favorites.json");
    /** Kept small enough to always fit the picker's fixed width in one row at any reasonable scale. */
    private static final int MAX = 10;

    private static List<Integer> cache;

    private ColorFavorites() {
    }

    static List<Integer> get() {
        ensureLoaded();
        return Collections.unmodifiableList(cache);
    }

    /** Adds {@code rgb} to the front, moving it there if already saved, and drops the oldest past {@link #MAX}. */
    static void add(int rgb) {
        ensureLoaded();
        cache.remove(Integer.valueOf(rgb));
        cache.add(0, rgb);
        while (cache.size() > MAX) {
            cache.remove(cache.size() - 1);
        }
        save();
    }

    static void remove(int rgb) {
        ensureLoaded();
        if (cache.remove(Integer.valueOf(rgb))) {
            save();
        }
    }

    private static void ensureLoaded() {
        if (cache != null) {
            return;
        }
        cache = new ArrayList<>();
        if (!Files.exists(FILE)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(FILE)) {
            Type type = new TypeToken<List<Integer>>() {}.getType();
            List<Integer> loaded = GSON.fromJson(r, type);
            if (loaded != null) {
                cache.addAll(loaded);
            }
        } catch (IOException e) {
            MarieCore.LOGGER.error("[MarieUI] Failed to load {}, discarding saved color favorites", FILE, e);
        }
    }

    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(cache, w);
            }
        } catch (IOException e) {
            MarieCore.LOGGER.error("[MarieUI] Failed to save {}", FILE, e);
        }
    }
}
