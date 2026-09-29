package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

import dev.marie.framework.api.ConfigValidator;
import net.minecraft.commands.CommandSourceStack;

@ApiStatus.Stable
final class ConfigValidationDelegate {

    private ConfigValidationDelegate() {}

    static void registerConfigValidator(ConfigValidator validator) {
        dev.marie.framework.config.ConfigValidatorRegistry.registerRaw(validator);
        dev.marie.framework.config.ConfigValidatorRegistry.register(
                validator.validatorId(),
                ctx -> {
                    dev.marie.framework.config.validation.ValidationResult result = validator.validate();
                    CommandSourceStack source = ctx.getSource();
                    String prefix = "[" + validator.validatorId() + "] ";
                    if (result.status() == dev.marie.framework.config.validation.ValidationResult.Status.PASS) {
                        source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                                prefix + "PASS — no issues found."), false);
                    } else {
                        for (dev.marie.framework.config.validation.Finding f : result.findings()) {
                            String msg = prefix + f.severity().name() + " [" + f.file() + " / " + f.key() + "] " + f.message();
                            if (f.severity() == dev.marie.framework.config.validation.ValidationResult.Status.FAIL) {
                                source.sendFailure(net.minecraft.network.chat.Component.literal(msg));
                            } else {
                                source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(msg), false);
                            }
                        }
                    }
                    return result.status() == dev.marie.framework.config.validation.ValidationResult.Status.FAIL ? 0 : 1;
                });
    }
}
