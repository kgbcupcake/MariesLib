/**
 * Command Center card registry and screen backing the MarieCommandCenter facade.
 *
 * <p>Stability is mixed at the class level rather than package-wide: {@link
 * dev.marie.framework.ui.commandcenter.CommandCenterRegistry}, {@link
 * dev.marie.framework.ui.commandcenter.CommandCenterCategory}, {@link
 * dev.marie.framework.ui.commandcenter.CommandCenterCard}, {@link
 * dev.marie.framework.ui.commandcenter.CustomCommandCenterCard}, and {@link
 * dev.marie.framework.ui.commandcenter.CommandCenterCardEntry} are the actual registration
 * surface consuming mods call directly and are {@code @Experimental}, while {@link
 * dev.marie.framework.ui.commandcenter.CommandCenterScreen} is rendering machinery opened only
 * through the {@code MarieCommandCenter} facade and is {@code @Internal}.</p>
 */
package dev.marie.framework.ui.commandcenter;

import dev.marie.framework.api.ApiStatus;
