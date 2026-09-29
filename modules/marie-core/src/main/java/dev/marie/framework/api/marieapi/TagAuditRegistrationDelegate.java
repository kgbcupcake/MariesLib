package dev.marie.framework.api.marieapi;

import dev.marie.framework.api.ApiStatus;

@ApiStatus.Stable
final class TagAuditRegistrationDelegate {

    private TagAuditRegistrationDelegate() {}

    static void registerTagRule(dev.marie.framework.tagaudit.rule.TagRule rule) {
        dev.marie.framework.tagaudit.TagRuleRegistry.register(rule);
    }

    static void registerTagAuditContext(String modId, dev.marie.framework.tagaudit.model.TagAuditContext context) {
        dev.marie.framework.tagaudit.TagAuditContextRegistry.register(modId, context);
    }
}
