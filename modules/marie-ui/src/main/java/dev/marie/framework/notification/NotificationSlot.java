package dev.marie.framework.notification;

import dev.marie.framework.api.ApiStatus;

import java.util.List;
import java.util.function.BiFunction;

/** Mutable runtime state for one active slot in the notification stack. */
@ApiStatus.Internal
final class NotificationSlot {

    List<List<TextSegment>> content;
    final int durationTicks;
    final boolean emphasized;
    final Object mergeKey;
    final int mergeWindowTicks;
    final BiFunction<List<List<TextSegment>>, List<List<TextSegment>>, List<List<TextSegment>>> mergeFunction;

    long lastTriggeredMs;
    double currentY;
    boolean yInitialized;

    NotificationSlot(NotificationRequest request, long nowMs) {
        this.content = request.content();
        this.durationTicks = request.durationTicks();
        this.emphasized = request.emphasized();
        this.mergeKey = request.mergeKey();
        this.mergeWindowTicks = request.mergeWindowTicks();
        this.mergeFunction = request.mergeFunction();
        this.lastTriggeredMs = nowMs;
    }

    /** Merge window is measured from this slot's own last-triggered time, not app-start. */
    boolean withinMergeWindow(Object key, long nowMs) {
        return mergeKey != null && mergeKey.equals(key) && mergeWindowTicks > 0
                && (nowMs - lastTriggeredMs) <= ticksToMs(mergeWindowTicks);
    }

    void mergeIn(List<List<TextSegment>> newContent, long nowMs) {
        content = mergeFunction != null ? mergeFunction.apply(content, newContent) : newContent;
        lastTriggeredMs = nowMs;
    }

    long durationMs() {
        return ticksToMs(durationTicks);
    }

    static long ticksToMs(int ticks) {
        return ticks * 50L;
    }
}
