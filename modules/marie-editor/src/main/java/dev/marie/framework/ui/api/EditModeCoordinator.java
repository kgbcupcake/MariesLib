package dev.marie.framework.ui.api;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.edit.EditModeController;
import dev.marie.framework.ui.edit.EditableComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Public facade and static registry for edit mode: lets any consumer mod register one or more
 * editable targets under an id and toggle every registered target together, as one group,
 * without MarieLib knowing anything about what's actually being edited.
 *
 * <p>Two registration paths exist. {@link #register(String, EditableComponent)} is for a target
 * that already owns its own {@link EditModeController} (or equivalent) and just needs to be
 * toggled in lockstep with everything else. {@link #registerGroupCapable} is for a target that
 * should join one <em>shared</em> overlay screen spanning every group-capable registrant — e.g.
 * several independent HUD panels that should all become draggable/resizable together under a
 * single "enter edit mode" action, with one shared hint banner and exit key.
 *
 * <pre>{@code
 * // once, e.g. during client setup, for a target with its own EditModeController:
 * EditModeCoordinator.register("mymod.my_hud", myHudPanel);
 *
 * // or, for a target that should share one overlay with other HUDs:
 * EditModeCoordinator.registerGroupCapable(
 *         "mymod.my_other_hud", () -> myOtherHudPanel,
 *         "Press E to stop editing", GLFW.GLFW_KEY_E);
 *
 * // wherever edit mode should be toggled, e.g. a keybind handler:
 * EditModeCoordinator.toggleAll();
 * }</pre>
 */
@ApiStatus.Experimental
public final class EditModeCoordinator {

    private static final Map<String, EditableComponent> COMPONENTS = new LinkedHashMap<>();
    private static final Map<String, GroupEntry> GROUP_COMPONENTS = new LinkedHashMap<>();

    private EditModeCoordinator() {}

    /** Registers a component under {@code id}; re-registering an id replaces the existing one. */
    public static void register(String id, EditableComponent component) {
        COMPONENTS.put(id, component);
    }

    /**
     * Registers a component under {@code id} for the group-entry path: {@link #enterAll()} combines
     * every group-capable registrant into one shared overlay (one {@code setScreen()} call) instead
     * of each triggering its own screen. {@code targetSupplier} is only invoked at the moment a
     * group-entry actually happens, never at registration time — registering here stores just a
     * reference to how to get the target, not the target itself. Re-registering an id replaces the
     * existing one.
     *
     * @param id unique id for this registrant
     * @param targetSupplier lazily resolves the {@link MarieComponent} to add to the shared overlay
     * @param hintText banner text for the shared overlay; the first-registered entry's value wins
     * @param exitKeyCode key that exits the shared overlay; the first-registered entry's value wins
     */
    public static void registerGroupCapable(String id, Supplier<MarieComponent> targetSupplier, String hintText, int exitKeyCode) {
        registerGroupCapable(id, targetSupplier, hintText, exitKeyCode, null);
    }

    /**
     * Registers a component under {@code id} for the group-entry path: {@link #enterAll()} combines
     * every group-capable registrant into one shared overlay (one {@code setScreen()} call) instead
     * of each triggering its own screen. {@code targetSupplier} is only invoked at the moment a
     * group-entry actually happens, never at registration time — registering here stores just a
     * reference to how to get the target, not the target itself. Re-registering an id replaces the
     * existing one.
     *
     * @param id unique id for this registrant
     * @param targetSupplier lazily resolves the {@link MarieComponent} to add to the shared overlay
     * @param hintText banner text for the shared overlay; the first-registered entry's value wins
     * @param exitKeyCode key that exits the shared overlay; the first-registered entry's value wins
     * @param onGroupEnter optional callback invoked for this registrant when {@link #enterAll()} runs
     *     a group entry, after its {@code targetSupplier} has been resolved; may be {@code null} for none
     */
    public static void registerGroupCapable(String id, Supplier<MarieComponent> targetSupplier, String hintText, int exitKeyCode, Runnable onGroupEnter) {
        GROUP_COMPONENTS.put(id, new GroupEntry(targetSupplier, hintText, exitKeyCode, onGroupEnter));
    }

    /** Unregisters {@code id} from both the plain and group-capable registries. No-op if not registered. */
    public static void unregister(String id) {
        COMPONENTS.remove(id);
        GROUP_COMPONENTS.remove(id);
    }

    /** If any registered component is active, exits all; otherwise enters all. */
    public static void toggleAll() {
        if (isAnyActive()) {
            exitAll();
        } else {
            enterAll();
        }
    }

    /** Enters edit mode on every plain-registered component, plus one shared overlay for every group-capable registrant. */
    public static void enterAll() {
        for (EditableComponent component : COMPONENTS.values()) {
            component.enterEditMode();
        }
        if (GROUP_COMPONENTS.isEmpty()) {
            return;
        }
        List<MarieComponent> targets = new ArrayList<>();
        for (GroupEntry entry : GROUP_COMPONENTS.values()) {
            targets.add(entry.targetSupplier().get());
            if (entry.onGroupEnter() != null) {
                entry.onGroupEnter().run();
            }
        }
        // Hint text/exit key are shared by the whole overlay; the first-registered entry's values win.
        GroupEntry first = GROUP_COMPONENTS.values().iterator().next();
        EditModeController.enterGroup(targets, first.hintText(), first.exitKeyCode(), () -> {});
    }

    /** Exits edit mode on every plain-registered component, plus the shared group overlay if one is active. */
    public static void exitAll() {
        for (EditableComponent component : COMPONENTS.values()) {
            component.exitEditMode();
        }
        EditModeController.exitGroup();
    }

    private static boolean isAnyActive() {
        for (EditableComponent component : COMPONENTS.values()) {
            if (component.isEditModeActive()) {
                return true;
            }
        }
        return EditModeController.isGroupActive();
    }

    /**
     * One group-capable registrant's lazy target plus the shared overlay's hint/exit-key policy, and
     * an optional callback fired for this registrant when a group entry happens.
     */
    private record GroupEntry(Supplier<MarieComponent> targetSupplier, String hintText, int exitKeyCode, Runnable onGroupEnter) {}
}
