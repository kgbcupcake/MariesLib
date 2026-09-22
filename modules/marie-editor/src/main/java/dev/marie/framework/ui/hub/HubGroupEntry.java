package dev.marie.framework.ui.hub;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * A group sidebar entry: selecting it in {@link HubPanel}'s sidebar shows the CURRENT result of
 * {@code children} as a plain list in the content pane, rebuilt fresh every time it's selected or
 * re-rendered — not fixed at construction time — so a host whose child count can change at runtime
 * (e.g. driven by a registry that can grow) stays accurate without the hub needing to know why.
 */
public record HubGroupEntry(String id, Component label, Supplier<List<HubChildEntry>> children) implements HubSidebarEntry {}
