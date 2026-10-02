package dev.marie.framework.command;

import dev.marie.framework.api.ApiStatus;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.marie.framework.api.registry.ProfileRegistry;
import dev.marie.framework.core.IMarieConfig;
import dev.marie.framework.core.MarieContext;
import dev.marie.framework.handler.SourceApplicationPipeline;
import dev.marie.framework.tracking.TrackingAttachment;
import dev.marie.framework.tracking.TrackingData;
import dev.marie.framework.tracking.TrackingResetSupport;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApiStatus.Internal
final class MariePlayerCommands {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MariePlayerCommands() {}

    static int reportSelf(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        ServerPlayer target = ctx.getSource().getPlayerOrException();
        return sendReport(ctx.getSource(), target, modId);
    }

    static int reportTarget(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        return sendReport(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), modId);
    }

    static int valueSelf(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        return sendValueDetail(
                ctx.getSource(),
                StringArgumentType.getString(ctx, "key"),
                ctx.getSource().getPlayerOrException(),
                modId);
    }

    static int valueTarget(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        return sendValueDetail(
                ctx.getSource(),
                StringArgumentType.getString(ctx, "key"),
                EntityArgument.getPlayer(ctx, "player"),
                modId);
    }

    static int setValue(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        if (!MarieCommandSupport.ensureConsumerRegistered(ctx.getSource())) {
            return 0;
        }
        String key = StringArgumentType.getString(ctx, "key");
        if (!MarieCommandSupport.isKnownValueKey(key)) {
            ctx.getSource().sendFailure(Component.literal("Unknown value key: " + key));
            return 0;
        }
        float value = FloatArgumentType.getFloat(ctx, "value");
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        if (!TrackingAttachment.isRegistered()) {
            ctx.getSource().sendFailure(Component.literal("Tracking attachment is not registered."));
            return 0;
        }
        TrackingData data = TrackingAttachment.getData(player);
        float old = data.values.getOrDefault(key, 0f);
        if (SourceApplicationPipeline.writeDirectValue(player, data, key, value)) {
            SourceApplicationPipeline.finalizeDirectWrite(player, data);
        }
        float applied = data.values.getOrDefault(key, 0f);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Set %s for %s: %.2f -> %.2f", key, player.getName().getString(), old, applied)), true);
        return 1;
    }

    static int setAllValues(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        if (!MarieCommandSupport.ensureConsumerRegistered(ctx.getSource())) {
            return 0;
        }
        float value = FloatArgumentType.getFloat(ctx, "value");
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        if (!TrackingAttachment.isRegistered()) {
            ctx.getSource().sendFailure(Component.literal("Tracking attachment is not registered."));
            return 0;
        }
        TrackingData data = TrackingAttachment.getData(player);
        for (String key : MarieCommandSupport.registeredValueKeys()) {
            SourceApplicationPipeline.writeDirectValue(player, data, key, value);
        }
        SourceApplicationPipeline.finalizeDirectWrite(player, data);
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Set all values for %s to %.2f", player.getName().getString(), value)), true);
        return 1;
    }

    static int resetPlayer(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        if (!TrackingAttachment.isRegistered()) {
            ctx.getSource().sendFailure(Component.literal("Tracking attachment is not registered."));
            return 0;
        }
        TrackingData data = TrackingAttachment.getData(player);
        float start = TrackingResetSupport.resolveStartingFill();
        boolean changed = TrackingResetSupport.resetAllBarValues(player, data, start);
        if (changed) {
            SourceApplicationPipeline.finalizeDirectWrite(player, data);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Reset values for " + player.getName().getString()), true);
        return 1;
    }

    static int profileList(CommandContext<CommandSourceStack> ctx, String modId) {
        List<String> names = ProfileRegistry.getAll().stream()
                .map(p -> p.getId() + " (" + p.getDisplayName() + ")")
                .toList();
        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("No tracking profiles registered."), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Profiles: " + String.join(", ", names)), false);
        return 1;
    }

    static int profileSetSelf(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        return setProfile(
                ctx.getSource(),
                StringArgumentType.getString(ctx, "profile"),
                ctx.getSource().getPlayerOrException());
    }

    static int profileSetTarget(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        return setProfile(
                ctx.getSource(),
                StringArgumentType.getString(ctx, "profile"),
                EntityArgument.getPlayer(ctx, "player"));
    }

    static int profileGetSelf(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        return getProfile(ctx.getSource(), ctx.getSource().getPlayerOrException());
    }

    static int profileGetTarget(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        return getProfile(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"));
    }

    static int debugTarget(CommandContext<CommandSourceStack> ctx, String modId) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        TrackingData data = TrackingAttachment.getData(target);

        JsonObject root = new JsonObject();
        root.addProperty("player", target.getGameProfile().getName());
        root.addProperty("total", data.total);
        root.addProperty("maxTotal", data.maxTotal);
        root.addProperty("activeProfile", MarieCommandSupport.activeProfile(target));

        JsonObject values = new JsonObject();
        for (Map.Entry<String, Float> entry : new LinkedHashMap<>(data.values).entrySet()) {
            values.addProperty(entry.getKey(), entry.getValue());
        }
        root.add("values", values);

        String[] lines = GSON.toJson(root).split("\n");
        ctx.getSource().sendSuccess(() -> Component.literal("Raw TrackingData for " + target.getName().getString() + ":")
                .withStyle(ChatFormatting.GOLD), false);
        for (String line : lines) {
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int sendReport(CommandSourceStack source, ServerPlayer target, String modId) {
        if (!MarieCommandSource.canTargetOtherPlayer(source, target)) {
            source.sendFailure(Component.literal("You do not have permission to target another player."));
            return 0;
        }
        TrackingData data = TrackingAttachment.getData(target);
        List<Component> lines = MarieCommandSource.buildReportLines(
                target, data, MarieCommandSupport.activeProfile(target));
        for (Component line : lines) {
            source.sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int sendValueDetail(CommandSourceStack source, String key, ServerPlayer target, String modId) {
        if (!MarieCommandSupport.isKnownValueKey(key)) {
            source.sendFailure(Component.literal("Unknown value key: " + key));
            return 0;
        }
        if (!MarieCommandSource.canTargetOtherPlayer(source, target)) {
            source.sendFailure(Component.literal("You do not have permission to target another player."));
            return 0;
        }

        TrackingData data = TrackingAttachment.getData(target);
        float value = data.values.getOrDefault(key, 0f);
        float decay = IMarieConfig.get().decayRateFor(key);
        float critical = IMarieConfig.get().criticalThresholdFor(key);
        float low = IMarieConfig.get().lowThreshold();
        float excess = IMarieConfig.get().excessThreshold();
        Component chip = MarieCommandSource.statusChip(MarieCommandSource.statusFor(value, key));

        source.sendSuccess(() -> Component.literal("Value: " + key + " (" + target.getName().getString() + ")")
                .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Current: %.2f (%d%%)",
                value, Math.round(value * 100f))).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Decay rate: %.4f", decay))
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "Thresholds: critical=%.2f low=%.2f excess=%.2f", critical, low, excess))
                .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal("Status: ").withStyle(ChatFormatting.GRAY).append(chip), false);
        return 1;
    }

    private static int setProfile(CommandSourceStack source, String profile, ServerPlayer target) {
        if (ProfileRegistry.get(profile) == null) {
            source.sendFailure(Component.literal("Unknown profile: " + profile));
            return 0;
        }
        MarieCommandSupport.setActiveProfile(target, profile);
        source.sendSuccess(() -> Component.literal(
                "Set active profile for " + target.getName().getString() + " to " + profile), true);
        return 1;
    }

    private static int getProfile(CommandSourceStack source, ServerPlayer target) {
        source.sendSuccess(() -> Component.literal(target.getName().getString() + " active profile: "
                + MarieCommandSupport.activeProfile(target)), false);
        return 1;
    }
}
