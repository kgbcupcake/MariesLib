package dev.marie.framework.ui.edithost;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.api.EditableModule;
import dev.marie.framework.ui.api.MarieModuleSettings;
import dev.marie.framework.ui.api.MoveDrag;
import dev.marie.framework.ui.geometry.Bounds;

/**
 * One module's move-mode drag: start from its current offsets, apply clamped offsets live, commit on
 * release — the same semantics the hand-rolled Diet Screen edit target used, ALL moving text, icons,
 * bars and header together by the pointer's movement since the press.
 */
@ApiStatus.Internal
final class ContentMoveSession {

    private final MoveDrag moveDrag = new MoveDrag();
    private EditableModule module;
    private Bounds box;

    boolean isActive() {
        return moveDrag.isActive();
    }

    String moduleId() {
        return module != null ? module.id() : null;
    }

    MoveDrag.Mode mode() {
        return moveDrag.mode();
    }

    Bounds box() {
        return box;
    }

    void start(PersistenceProvider st, EditableModule target, Bounds targetBox, MoveDrag.Mode mode, double mouseX, double mouseY) {
        String id = target.id();
        module = target;
        box = targetBox;
        switch (mode) {
            case TEXT -> moveDrag.start(mode, mouseX, mouseY, MarieModuleSettings.textOffsetX(st, id), MarieModuleSettings.textOffsetY(st, id));
            case ICONS -> moveDrag.start(mode, mouseX, mouseY, MarieModuleSettings.iconOffsetX(st, id), MarieModuleSettings.iconOffsetY(st, id));
            case ICON_INNER -> moveDrag.start(mode, mouseX, mouseY, MarieModuleSettings.iconInnerOffsetX(st, id), MarieModuleSettings.iconInnerOffsetY(st, id));
            case BARS -> moveDrag.start(mode, mouseX, mouseY, MarieModuleSettings.barOffsetX(st, id), MarieModuleSettings.barOffsetY(st, id));
            case HEADER -> moveDrag.start(mode, mouseX, mouseY, MarieModuleSettings.headerOffsetX(st, id), MarieModuleSettings.headerOffsetY(st, id));
            case ALL -> moveDrag.startAll(mouseX, mouseY,
                    MarieModuleSettings.textOffsetX(st, id), MarieModuleSettings.textOffsetY(st, id),
                    MarieModuleSettings.iconOffsetX(st, id), MarieModuleSettings.iconOffsetY(st, id),
                    MarieModuleSettings.barOffsetX(st, id), MarieModuleSettings.barOffsetY(st, id),
                    MarieModuleSettings.headerOffsetX(st, id), MarieModuleSettings.headerOffsetY(st, id));
        }
    }

    void apply(PersistenceProvider st, double mouseX, double mouseY) {
        String id = module.id();
        MoveDrag.Mode mode = moveDrag.mode();
        int rawX = moveDrag.offsetX(mouseX);
        int rawY = moveDrag.offsetY(mouseY);
        switch (mode) {
            case TEXT -> MarieModuleSettings.setTextOffset(st, id, x(mode, rawX), y(mode, rawY));
            case ICONS -> MarieModuleSettings.setIconOffset(st, id, x(mode, rawX), y(mode, rawY));
            case ICON_INNER -> MarieModuleSettings.setIconInnerOffset(st, id, x(mode, rawX), y(mode, rawY));
            case BARS -> MarieModuleSettings.setBarOffset(st, id, x(mode, rawX), y(mode, rawY));
            case HEADER -> MarieModuleSettings.setHeaderOffset(st, id, x(mode, rawX), y(mode, rawY));
            case ALL -> {
                MarieModuleSettings.setTextOffset(st, id, x(mode, moveDrag.baseX(MoveDrag.Mode.TEXT) + rawX), y(mode, moveDrag.baseY(MoveDrag.Mode.TEXT) + rawY));
                MarieModuleSettings.setIconOffset(st, id, x(mode, moveDrag.baseX(MoveDrag.Mode.ICONS) + rawX), y(mode, moveDrag.baseY(MoveDrag.Mode.ICONS) + rawY));
                MarieModuleSettings.setBarOffset(st, id, x(mode, moveDrag.baseX(MoveDrag.Mode.BARS) + rawX), y(mode, moveDrag.baseY(MoveDrag.Mode.BARS) + rawY));
                MarieModuleSettings.setHeaderOffset(st, id, x(mode, moveDrag.baseX(MoveDrag.Mode.HEADER) + rawX), y(mode, moveDrag.baseY(MoveDrag.Mode.HEADER) + rawY));
            }
        }
    }

    void finish(PersistenceProvider st) {
        String id = module.id();
        MoveDrag.Mode mode = moveDrag.mode();
        moveDrag.stop();
        boolean all = mode == MoveDrag.Mode.ALL;
        if (all || mode == MoveDrag.Mode.TEXT) {
            MarieModuleSettings.commitTextOffset(st, id);
        }
        if (all || mode == MoveDrag.Mode.ICONS) {
            MarieModuleSettings.commitIconOffset(st, id);
        }
        if (mode == MoveDrag.Mode.ICON_INNER) {
            MarieModuleSettings.commitIconInnerOffset(st, id);
        }
        if (all || mode == MoveDrag.Mode.BARS) {
            MarieModuleSettings.commitBarOffset(st, id);
        }
        if (all || mode == MoveDrag.Mode.HEADER) {
            MarieModuleSettings.commitHeaderOffset(st, id);
        }
        module = null;
        box = null;
    }

    private int x(MoveDrag.Mode mode, int raw) {
        return module.clampMoveOffsetX(mode, raw, box);
    }

    private int y(MoveDrag.Mode mode, int raw) {
        return module.clampMoveOffsetY(mode, raw, box);
    }
}
