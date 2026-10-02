/**
 * Notification queueing and rendering backing the MarieNotifications facade.
 *
 * <p>Stability is mixed at the class level rather than package-wide: {@link
 * dev.marie.framework.notification.NotificationRequest}, {@link
 * dev.marie.framework.notification.TextSegment}, and {@link
 * dev.marie.framework.notification.NotificationConfig} are built/tuned directly by consuming
 * mods and are {@code @Experimental}, while {@link
 * dev.marie.framework.notification.NotificationManager} and {@link
 * dev.marie.framework.notification.NotificationRenderer} are queueing/rendering machinery
 * reachable only through the {@code MarieNotifications} facade and are {@code @Internal}.</p>
 */
package dev.marie.framework.notification;

import dev.marie.framework.api.ApiStatus;
