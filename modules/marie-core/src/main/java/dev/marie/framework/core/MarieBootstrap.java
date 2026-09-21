package dev.marie.framework.core;

import java.util.function.Function;
import java.util.function.Supplier;

import dev.marie.framework.api.registry.BlockHoverProviderRegistry;
import dev.marie.framework.api.registry.GenericStateSyncHandlerRegistry;
import dev.marie.framework.api.registry.ModScanRegistry;

import dev.marie.framework.color.ColorRegistry;
import dev.marie.framework.compat.AutoCompatDiscovery;
import dev.marie.framework.compat.ModCompat;
import dev.marie.framework.config.ModCompatRegistry;
import dev.marie.framework.config.FeatureFlagCache;
import dev.marie.framework.config.MarieModFeatureFlags;
import dev.marie.framework.config.PresetRegistry;
import dev.marie.framework.config.LockRegistry;
import dev.marie.framework.handler.GameplayTriggerListener;
import dev.marie.framework.handler.RecipeTriggerListener;
import dev.marie.framework.handler.ReloadGuardListener;
import dev.marie.framework.handler.RestCycleListener;
import dev.marie.framework.handler.PlayerTrackingLifecycle;
import dev.marie.framework.handler.ValueDecayListener;
import dev.marie.framework.handler.ValueEffectsListener;
import dev.marie.framework.api.source.SourceTriggerListener;
import dev.marie.framework.modscan.ModInventory;
import dev.marie.framework.modscan.ModScan;
import dev.marie.framework.registry.MarieApiRegistries;
import dev.marie.framework.runtime.TriggerHandlerRegistry;
import dev.marie.framework.registry.MarieAttributes;
import dev.marie.framework.registry.RegistryLifecycleManager;
import dev.marie.framework.runtime.SourceClassificationRegistry;
import dev.marie.framework.scanner.ExcludedItemsRegistry;
import dev.marie.framework.scanner.ScannerSpecRegistry;
import dev.marie.framework.api.ApiStatus;
import dev.marie.framework.data.MarieDataManager;
import dev.marie.framework.data.MarieDatapackCallbacks;
import dev.marie.framework.tracking.MilestoneProgressAttachment;
import dev.marie.framework.tracking.TrackerMilestoneProgressAttachment;
import dev.marie.framework.tracking.SynergyAbsorptionModifier;
import dev.marie.framework.tracking.TrackingAttachment;
import dev.marie.framework.api.marieapi.MarieAPI;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Entry point for consuming mods.
 *
 * <h2>Minimal setup (new mod)</h2>
 * <pre>{@code
 * // In your @Mod constructor:
 * MarieLibBootstrap.attach("examplemod", modEventBus);
 *
 * // Register values (Java):
 * MarieAPI.registerValue(ValueDefinition.builder("emc")
 *     .displayName("EMC")
 *     .amountScale(1_000_000)
 *     .build());
 *
 * // Or ship datapacks in data/examplemod/marie/values/emc.json
 *
 * // In-game: run /marie scan, copy output JSON into your datapack.
 * }</pre>
 *
 * <h2>Advanced setup</h2>
 * <p>Use {@link MarieContext#builder(String)} for custom resolvers,
 * signal overrides, or advanced compat hooks.</p>
 */
public final class MarieBootstrap {

    private static volatile Supplier<Object> configScreenFactory = () -> null;
    private static volatile Function<Object, Object> exportScreenFactory = parent -> null;
    private static volatile Function<Object, Object> importScreenFactory = parent -> null;
    private static volatile IEventBus attachedModEventBus;

    private MarieBootstrap() {}

    /**
     * Attaches MarieLib to a consuming mod with sensible defaults.
     * Call this from your mod constructor before registries freeze.
     *
     * <p>This registers a MarieContext for your mod using all internal
     * default resolvers. For advanced configuration use
     * {@link MarieContext#builder(String)} directly.</p>
     *
     * @param modId       your mod's ID
     * @param modEventBus your mod's IEventBus (from the mod constructor)
     */
    @ApiStatus.Stable
    public static void attach(String modId, IEventBus modEventBus) {
        if (modId == null || modId.isBlank()) {
            throw new IllegalArgumentException("modId cannot be null or blank");
        }
        if (modEventBus == null) {
            throw new IllegalArgumentException("modEventBus cannot be null");
        }

        attachFrameworkServices(modEventBus);

        MarieContext ctx = MarieContext.builder(modId).build();
        MarieContext.register(ctx);
        MarieDataManager.setCallbacks(MarieDatapackCallbacks.INSTANCE);
        FeatureFlagCache.sync(MarieModFeatureFlags.disabled());

        MarieAttributes.register(modEventBus);
        TrackingAttachment.register(modEventBus);
        MilestoneProgressAttachment.register(modEventBus);
        TrackerMilestoneProgressAttachment.register(modEventBus);

        attachedModEventBus = modEventBus;
        modEventBus.addListener(MarieBootstrap::onCommonSetup);
        modEventBus.addListener(MarieBootstrap::onLoadComplete);

        MarieCore.LOGGER.info("[MarieLib] Attached to mod: {}", modId);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            registerRegistries();
            IEventBus bus = attachedModEventBus;
            if (bus != null) {
                registerHandlers(bus);
            }
            RegistryLifecycleManager.loadAll();
        });
    }

    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        ModCompatRegistry.load();
        ModCompat.initialize();
        AutoCompatDiscovery.discover();
    }

    public static void setConfigScreenFactory(Supplier<Object> factory) {
        configScreenFactory = factory != null ? factory : () -> null;
    }

    public static void setExportScreenFactory(Function<Object, Object> factory) {
        exportScreenFactory = factory != null ? factory : parent -> null;
    }

    public static void setImportScreenFactory(Function<Object, Object> factory) {
        importScreenFactory = factory != null ? factory : parent -> null;
    }

    public static Supplier<Object> getConfigScreenFactory() {
        return configScreenFactory;
    }

    public static Function<Object, Object> getExportScreenFactory() {
        return exportScreenFactory;
    }

    public static Function<Object, Object> getImportScreenFactory() {
        return importScreenFactory;
    }

    /**
     * @deprecated Use {@link #attach(String, IEventBus)} for consuming-mod integration.
     */
    @Deprecated
    public static void bootstrap(IEventBus modEventBus) {
        MarieAttributes.register(modEventBus);
        TrackingAttachment.register(modEventBus);
        MilestoneProgressAttachment.register(modEventBus);
        TrackerMilestoneProgressAttachment.register(modEventBus);
        registerRegistries();
        registerHandlers(modEventBus);
        RegistryLifecycleManager.loadAll();
        ModCompatRegistry.load();
        FeatureFlagCache.sync(MarieModFeatureFlags.disabled());
        MarieCore.LOGGER.info("[MarieCore] Bootstrap complete with owned config");
    }

    private static volatile boolean frameworkServicesAttached;

    /**
     * Unlocks the registration window for domain-agnostic framework registries and wires
     * their freeze — safe to call from any number of mods, including ones that never touch
     * {@link MarieContext}. Future bucket-(a)/domain-agnostic registries should be added here
     * as they appear.
     */
    @ApiStatus.Experimental
    public static void attachFrameworkServices(IEventBus modEventBus) {
        if (frameworkServicesAttached) {
            return;
        }
        frameworkServicesAttached = true;
        modEventBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(BlockHoverProviderRegistry::freezeInternal));
        modEventBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(GenericStateSyncHandlerRegistry::freezeInternal));
        // Load-complete runs after every mod's construction and common setup, so every scan
        // extractor/listener/deferral registered during init is known: freeze them there. It still
        // runs inside game load (on the client, inside the loading overlay's reload chain, before the
        // game event bus starts), so the scan is only armed here and starts once the game is idle:
        // ServerStartedEvent on a dedicated server, the loading overlay finishing on a client
        // (signalled by marie-ui) or the first integrated server start.
        modEventBus.addListener((FMLLoadCompleteEvent event) -> {
            ModScanRegistry.freezeInternal();
            ModScan.arm(FMLPaths.GAMEDIR.get().resolve("marieslib").resolve("cache"), ModInventory::collect,
                    FMLEnvironment.dist.isDedicatedServer());
            NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> ModScan.onServerStarted());
        });
    }

    private static boolean registriesRegistered;

    private static void registerRegistries() {
        if (registriesRegistered) {
            return;
        }
        registriesRegistered = true;
        RegistryLifecycleManager.registerRegistry("LockRegistry", LockRegistry::load, LockRegistry::reload,
                LockRegistry::loadFromDatapack);
        RegistryLifecycleManager.registerRegistry("ColorRegistry", ColorRegistry::load, ColorRegistry::reload,
                ColorRegistry::loadFromDatapack);
        RegistryLifecycleManager.registerRegistry("ScannerSpecRegistry", ScannerSpecRegistry::load,
                ScannerSpecRegistry::reload, ScannerSpecRegistry::loadFromDatapack);
        RegistryLifecycleManager.registerRegistry("ExcludedItemsRegistry", ExcludedItemsRegistry::load,
                ExcludedItemsRegistry::reload, ExcludedItemsRegistry::loadFromDatapack);
        RegistryLifecycleManager.registerRegistry("SourceClassificationRegistry", SourceClassificationRegistry::load,
                SourceClassificationRegistry::reload, SourceClassificationRegistry::loadFromDatapack);
        RegistryLifecycleManager.registerRegistry("PresetRegistry", PresetRegistry::reload, PresetRegistry::reload, null);
        MarieAPI.registerAbsorptionModifier(new SynergyAbsorptionModifier());
    }

    private static boolean handlersRegistered;

    private static void registerHandlers(IEventBus modEventBus) {
        if (handlersRegistered) {
            return;
        }
        handlersRegistered = true;
        NeoForge.EVENT_BUS.register(new ValueDecayListener());
        NeoForge.EVENT_BUS.register(new ValueEffectsListener());
        NeoForge.EVENT_BUS.register(new RestCycleListener());
        NeoForge.EVENT_BUS.register(new PlayerTrackingLifecycle());
        NeoForge.EVENT_BUS.register(new ReloadGuardListener());
        NeoForge.EVENT_BUS.register(new RecipeTriggerListener());
        NeoForge.EVENT_BUS.register(new GameplayTriggerListener());
        // MariesLibCommand / MarieCommand (marie-commands) self-register via
        // @EventBusSubscriber — core has no compile-time dependency on marie-commands.
        KubeIntegration.registerEventBridge();
        MarieApiRegistries.freezeValueTrackingOnlyRegistriesAfterCommonSetup();
        for (SourceTriggerListener handler : TriggerHandlerRegistry.getAll()) {
            handler.register(NeoForge.EVENT_BUS);
        }
    }
}
