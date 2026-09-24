package dev.marie.framework.ui.api;

import dev.marie.framework.ui.PersistenceProvider;
import dev.marie.framework.ui.component.ComponentState;
import dev.marie.framework.ui.component.MarieComponent;
import dev.marie.framework.ui.toolbox.OptionLayout;
import dev.marie.framework.ui.toolbox.OptionRow;
import dev.marie.framework.ui.toolbox.SectionRow;
import dev.marie.framework.ui.toolbox.SliderOption;
import dev.marie.framework.ui.toolbox.ToggleOption;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the Sizes group's Icon size row: shown by default, suppressed by {@code withoutIcons()}
 * (along with the Move/Hide Icons toggles) and by {@code withoutIconSize()} (toggles unaffected),
 * and the "Size" fallback label the remaining slider gets when no {@code textSizeLabel} was set.
 */
class StandardPanelBuilderTest {

    private final FakeStore store = new FakeStore();

    @Test
    void defaultPanelShowsTextAndIconSizeRows() {
        MarieComponent panel = MarieModuleSettings.standardPanel("Test", store, "m").withoutBars().build();
        assertEquals(List.of(label("config.marieslib.moduleoptions.textSize"), label("config.marieslib.moduleoptions.iconSize")),
                sizeSliderLabels(panel));
    }

    @Test
    void withoutIconsShowsOnlyOneSliderLabeledSizeAndHidesIconToggles() {
        MarieComponent panel = MarieModuleSettings.standardPanel("Test", store, "m").withoutBars().withoutIcons().build();
        assertEquals(List.of(label("config.marieslib.moduleoptions.size")), sizeSliderLabels(panel));
        assertFalse(hasToggleLabeled(panel, label("config.marieslib.moduleoptions.moveIcons")));
        assertFalse(hasToggleLabeled(panel, label("config.marieslib.moduleoptions.hideIcons")));
    }

    @Test
    void withoutIconSizeShowsOnlyOneSliderLabeledSizeButKeepsIconToggles() {
        MarieComponent panel = MarieModuleSettings.standardPanel("Test", store, "m").withoutBars().withoutIconSize().build();
        assertEquals(List.of(label("config.marieslib.moduleoptions.size")), sizeSliderLabels(panel));
        assertTrue(hasToggleLabeled(panel, label("config.marieslib.moduleoptions.moveIcons")));
        assertTrue(hasToggleLabeled(panel, label("config.marieslib.moduleoptions.hideIcons")));
    }

    @Test
    void explicitTextSizeLabelWinsOverTheSizeFallback() {
        MarieComponent panel = MarieModuleSettings.standardPanel("Test", store, "m")
                .withoutBars()
                .withoutIconSize()
                .textSizeLabel("config.marieslib.moduleoptions.hideText")
                .build();
        assertEquals(List.of(label("config.marieslib.moduleoptions.hideText")), sizeSliderLabels(panel));
    }

    private static List<String> sizeSliderLabels(MarieComponent panel) {
        List<String> labels = new ArrayList<>();
        for (OptionRow row : ((OptionLayout) panel).allRows()) {
            if (row instanceof SectionRow section && section.key().equals(label("config.marieslib.moduleoptions.section.sizes"))) {
                for (OptionRow child : section.children()) {
                    if (child instanceof SliderOption slider) {
                        labels.add(slider.label());
                    }
                }
            }
        }
        return labels;
    }

    private static boolean hasToggleLabeled(MarieComponent panel, String toggleLabel) {
        for (OptionRow row : ((OptionLayout) panel).allRows()) {
            if (row instanceof ToggleOption toggle && toggle.label().equals(toggleLabel)) {
                return true;
            }
        }
        return false;
    }

    private static String label(String key) {
        return Component.translatable(key).getString();
    }

    /** Map-backed {@link PersistenceProvider} test double. */
    private static final class FakeStore implements PersistenceProvider {
        final Map<String, ComponentState> data = new HashMap<>();

        @Override
        public Optional<ComponentState> load(String componentId) {
            return Optional.ofNullable(data.get(componentId));
        }

        @Override
        public void save(String componentId, ComponentState state) {
            data.put(componentId, state);
        }

        @Override
        public void remove(String componentId) {
            data.remove(componentId);
        }
    }
}
