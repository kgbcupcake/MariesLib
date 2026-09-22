package dev.marie.framework.ui.toolbox.colorpicker;

import dev.marie.framework.api.ApiStatus;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * One editable color: a label and the caller's getter/setter/default/commit. Values passed to
 * {@code setter} and {@code defaultValue} are RGB only; only the low 24 bits of {@code getter}'s result are read.
 *
 * <p>{@code onCancel} runs when the picker stops editing the slot — it was closed, or retargeted to another
 * slot — so a caller whose setter only <em>previews</em> a value can drop a preview that was never committed.
 * It may run when nothing is pending (it must be safe to call then), and never after {@code onCommit} has made
 * a value permanent by itself. It cannot revert a setter that writes real state.
 */
@ApiStatus.Internal
public record ColorSlot(String label, IntSupplier getter, IntConsumer setter, int defaultValue, Runnable onCommit,
                        Runnable onCancel) {

    /** A slot with nothing to cancel. */
    public ColorSlot(String label, IntSupplier getter, IntConsumer setter, int defaultValue, Runnable onCommit) {
        this(label, getter, setter, defaultValue, onCommit, () -> {});
    }

    /** The slot's current value as {@code 0xRRGGBB}. */
    public int rgb() {
        return getter.getAsInt() & 0xFFFFFF;
    }
}
