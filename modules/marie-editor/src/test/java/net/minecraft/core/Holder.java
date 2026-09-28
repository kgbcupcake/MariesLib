package net.minecraft.core;

/**
 * Test-only stand-in: Minecraft is not on this module's test runtime classpath, but {@code RenderContext}
 * mentions {@code Holder} in a method signature, so reflecting over it (a counting Proxy in the picker
 * test) needs the class to exist. Never instantiated.
 */
public interface Holder<T> {
}
