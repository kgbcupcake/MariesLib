package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Stable
public final class MarieAPIVersion {

    public static final int MAJOR = 1;
    public static final int MINOR = 1;
    public static final int PATCH = 0;
    public static final String VERSION = "1.1.0";

    private MarieAPIVersion() {}

    public static boolean isCompatible(int requiredMajor) {
        return MAJOR >= requiredMajor;
    }
}
