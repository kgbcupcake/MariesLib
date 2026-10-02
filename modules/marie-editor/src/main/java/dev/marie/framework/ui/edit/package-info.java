/**
 * Edit-mode controller and overlay screen for the in-game module editor.
 *
 * <p>Stability is mixed at the class level rather than package-wide: {@link
 * dev.marie.framework.ui.edit.EditModeController}, {@link
 * dev.marie.framework.ui.edit.EditableComponent}, and {@link
 * dev.marie.framework.ui.edit.ContentScaleController} back the {@code EditModeCoordinator}/{@code
 * BoxFitScale} facades and are {@code @Experimental}, while {@link
 * dev.marie.framework.ui.edit.EditOverlayScreen} is rendering machinery reached only through
 * those facades and is {@code @Internal}.</p>
 */
package dev.marie.framework.ui.edit;

import dev.marie.framework.api.ApiStatus;
