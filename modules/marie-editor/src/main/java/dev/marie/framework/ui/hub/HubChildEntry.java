package dev.marie.framework.ui.hub;

import dev.marie.framework.ui.component.MarieComponent;
import net.minecraft.network.chat.Component;

/**
 * One row of a {@link HubGroupEntry}'s dynamically-built child list — clicking it in {@link HubPanel}
 * opens {@code content} in a small popup window above the hub (see {@link HubChildPopup}), the same
 * "click something, get a small closable popup" interaction the color picker already uses.
 */
public record HubChildEntry(String id, Component label, MarieComponent content) {}
