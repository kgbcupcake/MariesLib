package dev.marie.framework.ui.hub;

import dev.marie.framework.api.ApiStatus;

import net.minecraft.network.chat.Component;

/**
 * One row of a {@link HubPanel}'s sidebar — either a {@link HubEntry} (selecting it shows its
 * content inline in the content pane) or a {@link HubGroupEntry} (selecting it shows a dynamically
 * rebuilt list of {@link HubChildEntry} rows instead, each opening its own popup when clicked).
 */
@ApiStatus.Internal
public sealed interface HubSidebarEntry permits HubEntry, HubGroupEntry {
    String id();

    Component label();
}
