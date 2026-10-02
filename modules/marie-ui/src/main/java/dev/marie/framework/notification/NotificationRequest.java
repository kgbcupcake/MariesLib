package dev.marie.framework.notification;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.api.MarieNotifications;

import java.util.List;
import java.util.function.BiFunction;

/** Immutable description of a notification to trigger via {@link MarieNotifications#show}. */
@ApiStatus.Experimental
public record NotificationRequest(
        List<List<TextSegment>> content,
        int durationTicks,
        boolean emphasized,
        Object mergeKey,
        int mergeWindowTicks,
        BiFunction<List<List<TextSegment>>, List<List<TextSegment>>, List<List<TextSegment>>> mergeFunction) {

    public NotificationRequest {
        content = content.stream().<List<TextSegment>>map(List::copyOf).toList();
    }

    public static Builder builder(List<List<TextSegment>> content, int durationTicks) {
        return new Builder(content, durationTicks);
    }

    /** Builder for {@link NotificationRequest}; merge fields default to "no merging". */
    public static final class Builder {
        private final List<List<TextSegment>> content;
        private final int durationTicks;
        private boolean emphasized;
        private Object mergeKey;
        private int mergeWindowTicks;
        private BiFunction<List<List<TextSegment>>, List<List<TextSegment>>, List<List<TextSegment>>> mergeFunction;

        private Builder(List<List<TextSegment>> content, int durationTicks) {
            this.content = content;
            this.durationTicks = durationTicks;
        }

        public Builder emphasized(boolean emphasized) {
            this.emphasized = emphasized;
            return this;
        }

        public Builder mergeKey(Object mergeKey) {
            this.mergeKey = mergeKey;
            return this;
        }

        public Builder mergeWindowTicks(int mergeWindowTicks) {
            this.mergeWindowTicks = mergeWindowTicks;
            return this;
        }

        public Builder mergeFunction(
                BiFunction<List<List<TextSegment>>, List<List<TextSegment>>, List<List<TextSegment>>> mergeFunction) {
            this.mergeFunction = mergeFunction;
            return this;
        }

        public NotificationRequest build() {
            return new NotificationRequest(content, durationTicks, emphasized, mergeKey, mergeWindowTicks, mergeFunction);
        }
    }
}
