package dev.marie.framework.ui.component.widgets;

import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.RenderContext;
import dev.marie.framework.ui.api.MarieModuleSettings;
import dev.marie.framework.ui.component.Constraint;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.component.SelfPositioningModule;
import dev.marie.framework.ui.geometry.Bounds;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Generic centered title-plus-divider header block, with optional decorative prefix/suffix accent
 * text (e.g. Nourished's {@code "✧✧"} flourishes either side of "Intake Breakdown") —
 * ported from {@code DietRightColumnComponent}'s hand-drawn header so any host with a similar
 * "centered title, divider line either side" block can reuse it.
 */
@ApiStatus.Experimental
public final class TitleBarComponent implements MarieComponent, SelfPositioningModule {

    private final String id;
    private final PersistenceProvider store;
    private final Bounds resolvedBounds;
    private final boolean visible;
    private final int naturalLocalHeight;
    private final Supplier<String> titleSupplier;
    private final Supplier<String> prefixAccentSupplier;
    private final Supplier<String> suffixAccentSupplier;
    private final IntSupplier textColorSupplier;
    private final IntSupplier dividerColorSupplier;

    public TitleBarComponent(
            String id,
            PersistenceProvider store,
            Bounds resolvedBounds,
            boolean visible,
            int naturalLocalHeight,
            Supplier<String> titleSupplier,
            Supplier<String> prefixAccentSupplier,
            Supplier<String> suffixAccentSupplier,
            IntSupplier textColorSupplier,
            IntSupplier dividerColorSupplier
    ) {
        this.id = id;
        this.store = store;
        this.resolvedBounds = resolvedBounds;
        this.visible = visible;
        this.naturalLocalHeight = naturalLocalHeight;
        this.titleSupplier = titleSupplier;
        this.prefixAccentSupplier = prefixAccentSupplier;
        this.suffixAccentSupplier = suffixAccentSupplier;
        this.textColorSupplier = textColorSupplier;
        this.dividerColorSupplier = dividerColorSupplier;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public int localHeight() {
        return visible ? naturalLocalHeight : 0;
    }

    public int naturalLocalHeight() {
        return naturalLocalHeight;
    }

    @Override
    public Bounds resolvedBounds() {
        return resolvedBounds;
    }

    public boolean isVisible() {
        return visible;
    }

    @Override
    public Constraint constraint() {
        return Constraint.preferred(resolvedBounds.width(), resolvedBounds.height());
    }

    @Override
    public void render(RenderContext baseContext, Bounds bounds) {
        RenderContext context = MarieModuleSettings.withDisplaySettings(baseContext, store, id);
        if (!visible) {
            return;
        }
        double scale = naturalLocalHeight > 0 ? bounds.height() / (double) naturalLocalHeight : 1.0d;
        float fscale = (float) scale;
        int textColor = textColorSupplier.getAsInt();

        String title = titleSupplier.get();
        int titleW = context.textWidth(title, fscale);
        int titleX = bounds.x() + (bounds.width() - titleW) / 2;
        int titleY = bounds.y();
        context.drawText(title, titleX, titleY, textColor, fscale);

        if (prefixAccentSupplier != null) {
            String prefix = prefixAccentSupplier.get();
            context.drawText(prefix, bounds.x() + (int) Math.round(2 * scale), titleY, textColor, fscale);
        }
        if (suffixAccentSupplier != null) {
            String suffix = suffixAccentSupplier.get();
            int suffixX = bounds.x() + bounds.width() - (int) Math.round(14 * scale);
            context.drawText(suffix, suffixX, titleY, textColor, fscale);
        }

        int lineY = titleY + (int) Math.round(4 * scale);
        int lineThickness = Math.max(1, (int) Math.round(scale));
        int gap = (int) Math.round(3 * scale);
        int leftLineW = Math.max(0, (titleX - gap) - bounds.x());
        context.fillRect(bounds.x(), lineY, leftLineW, lineThickness, dividerColorSupplier.getAsInt());
        int rightLineX = titleX + titleW + gap;
        int rightLineW = Math.max(0, (bounds.x() + bounds.width()) - rightLineX);
        context.fillRect(rightLineX, lineY, rightLineW, lineThickness, dividerColorSupplier.getAsInt());

        MarieModuleSettings.recordHeaderExtent(store, id, titleX, titleY, titleW, Math.max(1, (int) Math.round(9 * scale)));
    }
}
