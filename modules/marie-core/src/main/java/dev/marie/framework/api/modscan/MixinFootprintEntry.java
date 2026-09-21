package dev.marie.framework.api.modscan;

import dev.marie.framework.api.ApiStatus;

import java.util.List;

/**
 * One mixin class and one of its targets, as read from bytecode without loading anything.
 * Fields and methods are those the mixin declares (not {@code @Shadow}, constructors, initializers or
 * synthetic members) and therefore adds to, or injects into, the target.
 *
 * @param modId       the first mod id of the file that declares the mixin config
 * @param mixinClass  dotted binary name of the mixin class
 * @param targetClass dotted binary name of the target class; if {@code unresolved}, the raw name as
 *                    found in the jar (e.g. {@code net.minecraft.class_310})
 * @param unresolved  true when the target is a Fabric intermediary name that could not be mapped to a
 *                    Mojang name (no Sinytra Connector remapped copy of the jar was available), so
 *                    it may or may not be the class you asked about
 */
@ApiStatus.Experimental
public record MixinFootprintEntry(
        String modId,
        String mixinClass,
        String targetClass,
        List<Member> addedFields,
        List<Member> addedMethods,
        boolean unresolved) {

    public record Member(String name, String descriptor) {}

    public MixinFootprintEntry {
        addedFields = List.copyOf(addedFields);
        addedMethods = List.copyOf(addedMethods);
    }
}
