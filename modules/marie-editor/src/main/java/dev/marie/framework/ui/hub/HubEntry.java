package dev.marie.framework.ui.hub;

import dev.marie.framework.ui.component.MarieComponent;
import net.minecraft.network.chat.Component;

/**
 * A leaf sidebar entry: selecting it in {@link HubPanel}'s sidebar renders {@code content} inline in
 * the content pane, full remaining body size, with every mouse event forwarded straight into it —
 * the same "content embedded in a window body" contract {@code ScaleConfigPanel}'s hosted windows
 * already use.
 */
public record HubEntry(String id, Component label, MarieComponent content) implements HubSidebarEntry {}
