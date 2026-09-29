/**
 * Caching utilities.
 *
 * <p>Stability is mixed at the class level rather than package-wide: {@link
 * dev.marie.framework.cache.RunningAverage} is a zero-dependency numeric helper already
 * relied on directly by consuming mods and is {@code @Stable}, while {@link
 * dev.marie.framework.cache.BoundedLRU} backs internal caching and is {@code @Internal}.</p>
 */
package dev.marie.framework.cache;
