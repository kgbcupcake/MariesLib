package dev.marie.framework.ui.hub;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.Theme;
import dev.marie.framework.ui.ThemeKey;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.geometry.Bounds;
import dev.marie.framework.ui.toolbox.OptionStyle;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.function.Supplier;

/**
 * A Hub's "home" page: an entity rendered in 3D inside a framed box (see {@link EntityPreviewBox}),
 * beside a scrollable list of titled {@link HubInfoSection}s. Ported out of Thermal Systems' own
 * Hub home page, which combined this exact layout with its own climate/system/integration rows —
 * here that data comes from the caller instead, so any {@link dev.marie.framework.ui.hub.HubPanel}
 * (Thermal Systems' climate summary, Nourished's nutrition summary, ...) can reuse the same page
 * shape without re-deriving the box/scroll layout.
 *
 * <p>Neither {@code entity} nor {@code sections} is cached here — both suppliers are called fresh
 * every {@link #render}, same as any other {@link MarieComponent}. A caller whose data comes from
 * the server (e.g. a polled status payload) owns its own polling/caching and just returns the
 * latest known values from these suppliers.
 */
@ApiStatus.Experimental
public final class HubInfoPage implements MarieComponent {

    private static final int PAD = 6;
    private static final int ROW_HEIGHT = 12;
    private static final int HEADER_HEIGHT = 16;
    private static final int SECTION_GAP = 6;
    private static final int SCROLL_STEP = 20;

    private final String id;
    private final Supplier<LivingEntity> entity;
    private final Supplier<String> entityName;
    private final String emptyHint;
    private final Supplier<List<HubInfoSection>> sections;

    private int scroll;
    private Bounds lastInfoBounds = new Bounds(0, 0, 0, 0);

    /**
     * @param id         this component's {@link #id()}, e.g. {@code "yourmod.hub.home"}.
     * @param entity     the entity to preview — {@code null} is a valid result (e.g. no player in
     *                   the world yet) and shows {@code emptyHint} instead of a render.
     * @param entityName the name strip's text; called even when {@code entity} is {@code null} so
     *                    the caller can show a placeholder there too.
     * @param emptyHint  shown centered in the preview box in place of a render, once, up front —
     *                   not re-resolved per frame, since unlike the other two suppliers it never
     *                   depends on which entity (if any) is currently showing.
     * @param sections   the info panel's content for this frame, top to bottom.
     */
    public HubInfoPage(String id, Supplier<LivingEntity> entity, Supplier<String> entityName, String emptyHint,
                        Supplier<List<HubInfoSection>> sections) {
        this.id = id;
        this.entity = entity;
        this.entityName = entityName;
        this.emptyHint = emptyHint;
        this.sections = sections;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Constraint constraint() {
        return Constraint.preferred(320, 260);
    }

    @Override
    public void render(RenderContext context, Bounds bounds) {
        int boxWidth = Math.max(70, Math.min(110, bounds.width() / 3));
        int boxHeight = Math.min(bounds.height(), Math.max(110, boxWidth * 17 / 10));
        Bounds box = new Bounds(bounds.x(), bounds.y(), boxWidth, boxHeight);
        EntityPreviewBox.render(context, box, entity.get(), entityName.get(), emptyHint);

        int infoX = box.x() + box.width() + PAD * 2;
        lastInfoBounds = new Bounds(infoX, bounds.y(), Math.max(0, bounds.x() + bounds.width() - infoX), bounds.height());
        drawSections(context, lastInfoBounds);
    }

    private void drawSections(RenderContext context, Bounds area) {
        List<HubInfoSection> current = sections.get();
        int contentHeight = 0;
        for (HubInfoSection section : current) {
            contentHeight += HEADER_HEIGHT + section.rows().size() * ROW_HEIGHT + SECTION_GAP;
        }
        scroll = Math.max(0, Math.min(scroll, contentHeight - area.height()));

        Theme theme = context.theme();
        int accent = theme.color(ThemeKey.BORDER_HOVER);
        int labelColor = theme.color(ThemeKey.TEXT_SECONDARY);
        context.pushClip(area.x(), area.y(), area.width(), area.height());
        try {
            int y = area.y() - scroll;
            for (HubInfoSection section : current) {
                context.drawText(section.title(), area.x(), y + 3, accent, 1f);
                context.fillRect(area.x(), y + 13, area.width(), 1, theme.color(ThemeKey.BORDER));
                y += HEADER_HEIGHT;
                for (HubInfoSection.Row row : section.rows()) {
                    int valueWidth = context.textWidth(row.value(), 0.9f);
                    int labelMax = Math.max(0, area.width() - valueWidth - PAD);
                    context.drawText(OptionStyle.fit(context, row.label(), 0.9f, labelMax), area.x(), y + 2, labelColor, 0.9f);
                    int valueColor = row.color() != 0 ? row.color() : theme.color(ThemeKey.TEXT_PRIMARY);
                    context.drawText(row.value(), area.x() + area.width() - valueWidth, y + 2, valueColor, 0.9f);
                    y += ROW_HEIGHT;
                }
                y += SECTION_GAP;
            }
        } finally {
            context.popClip();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || !lastInfoBounds.contains((int) mouseX, (int) mouseY)) {
            return false;
        }
        scroll -= (int) Math.signum(scrollY) * SCROLL_STEP;
        return true;
    }
}
