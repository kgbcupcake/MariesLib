package dev.marie.framework.ui.toolbox;

import dev.marie.framework.api.ApiStatus;

/** Primitive counterpart of {@code BooleanSupplier}: receives the toggle's new value. Consumers pass a lambda or method reference and never need to name this type. */
@ApiStatus.Experimental
@FunctionalInterface
public interface BooleanSetter {
    void accept(boolean value);
}
