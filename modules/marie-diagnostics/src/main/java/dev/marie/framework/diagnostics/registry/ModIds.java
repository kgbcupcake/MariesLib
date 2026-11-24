package dev.marie.framework.diagnostics.registry;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Internal
public final class ModIds {

    public static final String MARIESLIB = "marieslib";

    private ModIds() {}

    public static boolean isValid(String modId) {
        if (modId == null || modId.isEmpty() || modId.length() > 64) {
            return false;
        }
        for (int i = 0; i < modId.length(); i++) {
            char c = modId.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                return false;
            }
        }
        return true;
    }

    public static String require(String modId, String context) {
        if (!isValid(modId)) {
            throw new IllegalArgumentException(context + ": invalid mod id '" + modId
                    + "' (expected [a-z0-9_]{1,64})");
        }
        return modId;
    }
}
