package dev.marie.framework.diagnostics.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.api.marieapi.MarieAPIVersion;
import dev.marie.framework.core.MarieCore;
import dev.marie.framework.diagnostics.capture.ContextCapture;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;
import net.neoforged.neoforgespi.language.IModInfo;

import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class EnvironmentCapture implements ContextCapture {

    @Override
    public String section() {
        return "environment";
    }

    @Override
    public JsonElement capture() {
        JsonObject o = new JsonObject();
        put(o, "minecraft", () -> SharedConstants.getCurrentVersion().getName());
        put(o, "neoforge", NeoForgeVersion::getVersion);
        put(o, "fml", NeoForgeVersion::getFmlVersion);
        put(o, "side", () -> FMLEnvironment.dist.name());
        put(o, "production", () -> String.valueOf(FMLEnvironment.production));

        JsonObject java = new JsonObject();
        put(java, "version", () -> System.getProperty("java.version"));
        put(java, "vendor", () -> System.getProperty("java.vendor"));
        put(java, "vm", () -> System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"));
        o.add("java", java);

        JsonObject os = new JsonObject();
        put(os, "name", () -> System.getProperty("os.name"));
        put(os, "arch", () -> System.getProperty("os.arch"));
        o.add("os", os);

        JsonObject lib = new JsonObject();
        put(lib, "modVersion", () -> DiagnosticsRuntime.modVersion(MarieCore.MOD_ID));
        put(lib, "apiVersion", () -> MarieAPIVersion.VERSION);
        o.add("marieslib", lib);

        try {
            ModList list = ModList.get();
            if (list == null) {
                o.addProperty("mods", "unavailable: mod list not loaded yet");
            } else {
                JsonArray mods = new JsonArray();
                List<IModInfo> infos = list.getMods().stream()
                        .sorted(Comparator.comparing(IModInfo::getModId)).toList();
                for (IModInfo info : infos) {
                    JsonObject m = new JsonObject();
                    m.addProperty("id", info.getModId());
                    m.addProperty("version", String.valueOf(info.getVersion()));
                    mods.add(m);
                }
                o.add("mods", mods);
            }
        } catch (Throwable t) {
            o.addProperty("mods", "unavailable: " + t);
        }
        return o;
    }

    private static void put(JsonObject o, String key, Supplier<String> value) {
        try {
            o.addProperty(key, value.get());
        } catch (Throwable t) {
            o.addProperty(key, "unavailable: " + t.getClass().getSimpleName());
        }
    }
}
