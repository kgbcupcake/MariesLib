package dev.marie.framework.curve.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.curve.math.CurveGrid;

import javax.annotation.Nullable;

/**
 * JSON serialization helpers for {@link CurveGrid}.
 * Schema: {@code { "xCells": int, "yCells": int, "multipliers": [float...] }}
 */
@ApiStatus.Stable
public final class CurveGridJson {

    private CurveGridJson() {}

    public static @Nullable CurveGrid fromJson(JsonObject obj) {
        try {
            int xCells = obj.get("xCells").getAsInt();
            int yCells = obj.get("yCells").getAsInt();
            JsonArray arr = obj.getAsJsonArray("multipliers");
            int expected = (xCells + 1) * (yCells + 1);
            if (arr == null || arr.size() != expected) {
                return null;
            }
            float[] grid = new float[expected];
            for (int i = 0; i < expected; i++) {
                grid[i] = arr.get(i).getAsFloat();
            }
            return new CurveGrid(xCells, yCells, grid);
        } catch (Exception e) {
            return null;
        }
    }

    public static JsonObject toJson(CurveGrid grid) {
        JsonObject obj = new JsonObject();
        obj.addProperty("xCells", grid.xCells());
        obj.addProperty("yCells", grid.yCells());
        JsonArray arr = new JsonArray();
        for (float v : grid.gridData()) {
            arr.add(v);
        }
        obj.add("multipliers", arr);
        return obj;
    }
}
