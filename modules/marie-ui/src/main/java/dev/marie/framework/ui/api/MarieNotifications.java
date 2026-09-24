package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.notification.NotificationManager;
import dev.marie.framework.notification.NotificationRenderer;
import dev.marie.framework.notification.NotificationRequest;
import dev.marie.framework.notification.TextSegment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Public facade for the generic client-side notification stack anchored above the XP bar.
 *
 * <p>Consumer mods trigger a notification via {@link #show(NotificationRequest)}; MarieLib owns
 * the stack itself (up to 4 visible slots, newest closest to the XP bar, oldest evicted on
 * overflow) and its timing (fade-in, hold, fade-out). A request's optional {@code mergeKey}/
 * {@code mergeWindowTicks}/{@code mergeFunction} let repeated triggers for the same logical event
 * (e.g. "player triggered a value source") update one slot's content in place, within a time window, instead of
 * stacking a new slot on every trigger — MarieLib never interprets what merging means, only
 * invokes the caller-supplied function.
 *
 * <p>{@link #registerClientListeners()} must be called once, client-side (typically from the
 * consuming mod's client setup), before anything will actually render — {@link #show} can be
 * called either way, but nothing draws until listeners are registered.
 *
 * <pre>{@code
 * // once, e.g. in the mod's client init:
 * MarieNotifications.registerClientListeners();
 *
 * // whenever the triggering event happens:
 * MarieNotifications.show(NotificationRequest.builder(
 *                 List.of(List.of(new TextSegment("+3 apples eaten", 0xFF55FF55))),
 *                 60)
 *         .build());
 * }</pre>
 */
@ApiStatus.Experimental
public final class MarieNotifications {

    private static volatile boolean listenerRegistered;

    private MarieNotifications() {}

    /**
     * Triggers a notification: merges into an in-window matching slot per {@code request}'s merge
     * settings, or pushes a new slot.
     *
     * @param request the notification to show; see {@link NotificationRequest} for content/merge options
     */
    public static void show(NotificationRequest request) {
        NotificationManager.show(request);
    }

    /** Clears the active notification stack, e.g. on disconnect. */
    public static void clear() {
        NotificationManager.clear();
    }

    /** Wires the render listener into the client event bus; safe to call more than once. */
    public static void registerClientListeners() {
        if (listenerRegistered) {
            return;
        }
        synchronized (MarieNotifications.class) {
            if (listenerRegistered) {
                return;
            }
            NeoForge.EVENT_BUS.addListener(NotificationRenderer::onRenderGuiPost);
            listenerRegistered = true;
        }
    }
}
