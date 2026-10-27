package dev.marie.framework.color;

import com.google.gson.JsonElement;

/** Reads a {@code colors.json} {@code "argb"} value: a number, {@code 0xAARRGGBB}, or 6/8 hex digits with an optional {@code #}; anything else is opaque white. */
final class ColorArgbParser {

    private ColorArgbParser() {}

    static int parse(JsonElement el) {
        if (el == null || el.isJsonNull()) {
            return 0xFF_FF_FF_FF;
        }
        if (el.isJsonPrimitive()) {
            if (el.getAsJsonPrimitive().isNumber()) {
                int n = el.getAsInt();
                return n | 0xFF00_0000;
            }
            return parse(el.getAsString());
        }
        return 0xFF_FF_FF_FF;
    }

    static int parse(String raw) {
        if (raw == null) {
            return 0xFF_FF_FF_FF;
        }
        String s = raw.trim();
        try {
            if (s.startsWith("0x") || s.startsWith("0X")) {
                String hex = s.substring(2);
                return (int) (Long.parseLong(hex, 16) & 0xFF_FF_FF_FF);
            }
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            if (s.length() == 6) {
                return (int) (Long.parseLong(s, 16) & 0xFF_FF_FF) | 0xFF00_0000;
            }
            if (s.length() == 8) {
                return (int) (Long.parseLong(s, 16) & 0xFF_FF_FF_FF);
            }
        } catch (NumberFormatException ignored) {
        }
        return 0xFF_FF_FF_FF;
    }
}
