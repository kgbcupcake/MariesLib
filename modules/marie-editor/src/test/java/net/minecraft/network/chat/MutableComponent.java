package net.minecraft.network.chat;

/** See {@link Component}. */
public final class MutableComponent implements Component {

    private final String string;

    MutableComponent(String string) {
        this.string = string;
    }

    @Override
    public String getString() {
        return string;
    }
}
