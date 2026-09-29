package dev.marie.framework.notification;

import dev.marie.framework.api.ApiStatus;

/** One colored run of text, rendered left-to-right with the segments around it on the same line. */
@ApiStatus.Experimental
public record TextSegment(String text, int argbColor) {}
