package dev.marie.framework.ui.color;

import dev.marie.framework.api.ApiStatus;

import java.util.Optional;

/** Pure {@code #RRGGBB} helpers shared by the picker readout and the Cloth hex row; no UI or storage. */
@ApiStatus.Internal
public final class HexColors {

    private HexColors() {}

    /** True while {@code s} could still become a hex color: only {@code #} and hex digits. */
    public static boolean hexInputFilter(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '#' || isHexDigit(c)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** The RGB part of {@code argb} as {@code #RRGGBB} (uppercase); alpha is ignored. */
    public static String formatRgbHex(int argb) {
        return String.format(java.util.Locale.ROOT, "#%06X", argb & 0xFF_FF_FF);
    }

    /** Strict {@code #RRGGBB} (hash required, exactly six hex digits) as an opaque ARGB int, or empty. */
    public static Optional<Integer> parseStrictRgbHex(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.trim();
        if (s.length() != 7 || s.charAt(0) != '#') {
            return Optional.empty();
        }
        for (int i = 1; i < 7; i++) {
            if (!isHexDigit(s.charAt(i))) {
                return Optional.empty();
            }
        }
        try {
            return Optional.of(0xFF00_0000 | Integer.parseInt(s.substring(1), 16));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
