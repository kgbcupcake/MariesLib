package net.minecraft.network.chat;

/**
 * Test-only stand-in: Minecraft is not on this module's test runtime classpath, but production code
 * (e.g. {@code StandardPanelBuilder}, {@code ModuleOptionRows}) resolves row labels through {@code
 * Component.translatable(key).getString()}. An interface, like the real class, so the {@code
 * invokeinterface} bytecode compiled against the real Minecraft jar still links. Returns the key
 * itself (no language file is loaded), which is fine since tests compare against the same call
 * rather than hardcoded translated text.
 */
public interface Component {

    String getString();

    static MutableComponent translatable(String key) {
        return new MutableComponent(key);
    }
}
