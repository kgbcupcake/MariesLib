package net.minecraft.world.effect;

/**
 * Test-only stand-in: Minecraft is not on this module's test runtime classpath, but {@code RenderContext}
 * mentions {@code MobEffect} in a method signature, so reflecting over it (a counting Proxy in the picker
 * test) needs the class to exist. Never instantiated.
 */
public class MobEffect {
}
