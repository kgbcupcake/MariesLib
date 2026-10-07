package dev.marie.framework.ui.hub;

import dev.marie.framework.api.ApiStatus;

import java.util.List;

/** One titled group of rows in a {@link HubInfoPage}'s scrollable info panel — e.g. "Climate", "System", "Integrations". */
@ApiStatus.Experimental
public record HubInfoSection(String title, List<Row> rows) {

    /** One label/value line. {@code color} 0 means the theme's primary text color. */
    public record Row(String label, String value, int color) {

        public Row(String label, String value) {
            this(label, value, 0);
        }
    }
}
