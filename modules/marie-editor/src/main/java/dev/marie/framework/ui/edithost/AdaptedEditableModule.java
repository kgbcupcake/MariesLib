package dev.marie.framework.ui.edithost;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.VisibilityRule;
import dev.marie.framework.ui.api.EditCapabilities;
import dev.marie.framework.ui.api.EditableModule;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.SelfPositioningModule;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.geometry.Size;

import java.util.function.Supplier;

/** Backs {@link EditableModule#of}: an existing module plus a natural size and capabilities. */
@ApiStatus.Internal
public final class AdaptedEditableModule implements EditableModule {

    private final SelfPositioningModule module;
    private final Supplier<Size> naturalSize;
    private final EditCapabilities capabilities;

    public AdaptedEditableModule(SelfPositioningModule module, Supplier<Size> naturalSize, EditCapabilities capabilities) {
        this.module = module;
        this.naturalSize = naturalSize;
        this.capabilities = capabilities;
    }

    @Override
    public Size naturalSize() {
        return naturalSize.get();
    }

    @Override
    public EditCapabilities editCapabilities() {
        return capabilities;
    }

    @Override
    public int localHeight() {
        return module.localHeight();
    }

    @Override
    public Bounds resolvedBounds() {
        return module.resolvedBounds();
    }

    @Override
    public String id() {
        return module.id();
    }

    @Override
    public Constraint constraint() {
        return module.constraint();
    }

    @Override
    public VisibilityRule visibilityRule() {
        return module.visibilityRule();
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        module.render(context, bounds);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return module.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return module.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return module.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return module.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return module.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return module.charTyped(codePoint, modifiers);
    }
}
