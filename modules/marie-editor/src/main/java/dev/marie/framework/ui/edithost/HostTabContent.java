package dev.marie.framework.ui.edithost;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.api.EditHostRenderer;
import dev.marie.framework.ui.api.ModuleEditHost;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import net.minecraft.client.Minecraft;

/**
 * Backs {@link ModuleEditHost#asComponent}: a plain {@link MarieComponent} (e.g. a hub tab's content)
 * that runs one host frame per render and routes input into the host. While editing, any press inside
 * it is consumed so it never falls through to dragging the window around it.
 */
@ApiStatus.Internal
public final class HostTabContent implements MarieComponent {

    private final String id;
    private final ModuleEditHost host;
    private final EditHostRenderer renderer;
    private Bounds area = new Bounds(0, 0, 0, 0);

    public HostTabContent(String id, ModuleEditHost host, EditHostRenderer renderer) {
        this.id = id;
        this.host = host;
        this.renderer = renderer;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Constraint constraint() {
        return Constraint.preferred(0, 0);
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        area = bounds;
        host.beginFrame();
        context.pushClip(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        try {
            renderer.render(context, bounds, host);
            int[] mouse = scaledMouse();
            host.renderOverlay(context, mouse[0], mouse[1]);
        } finally {
            context.popClip();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!host.isActive()) {
            return false;
        }
        return host.mouseClicked(mouseX, mouseY, button) || area.contains((int) mouseX, (int) mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return host.mouseDragged(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return host.mouseReleased(mouseX, mouseY, button);
    }

    private static int[] scaledMouse() {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        return new int[]{(int) (mc.mouseHandler.xpos() / scale), (int) (mc.mouseHandler.ypos() / scale)};
    }
}
