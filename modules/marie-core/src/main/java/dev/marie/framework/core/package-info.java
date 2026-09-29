/**
 * Bootstrap and runtime wiring for MarieCore.
 *
 * <p>Deliberately left without a package-wide {@code @ApiStatus} label: this package spans
 * every stability tier by design, from the addon bootstrap entry point ({@link
 * dev.marie.framework.core.MarieBootstrap}, {@code @Stable}) and the extension context ({@link
 * dev.marie.framework.core.MarieContext}, {@code @Experimental}) down to internal wiring never
 * meant for addons ({@link dev.marie.framework.core.MarieCore}, {@link
 * dev.marie.framework.core.MariesLibInternalContext}, {@code @Internal}). Check each class's own
 * annotation rather than assuming a package default.</p>
 */
package dev.marie.framework.core;
