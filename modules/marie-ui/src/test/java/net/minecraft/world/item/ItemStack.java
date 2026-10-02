package net.minecraft.world.item;

/**
 * Test-only stand-in: Minecraft is not on this module's test runtime classpath, but {@code RenderContext}
 * mentions {@code ItemStack} in a method signature, so reflecting over it (a counting Proxy in the picker
 * test) needs the class to exist. Never instantiated.
 */
public class ItemStack {
}
