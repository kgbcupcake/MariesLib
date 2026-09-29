/**
 * Reusable infrastructure for auditing and correcting item-to-tag-file
 * mismatches. Domain-agnostic — consuming mods define what tags/categories
 * mean and supply the actual rules.
 *
 * <p>Subpackages: {@code model} (issues, fix suggestions, reports, severity,
 * the rule-execution context) and {@code rule} (the TagRule interface
 * consuming mods implement). A later addition will add {@code TagScanner}
 * (the orchestrator) and an {@code apply} subpackage for writing approved
 * fixes back to datapack overrides or bundled source.</p>
 */
@ApiStatus.Stable
package dev.marie.framework.tagaudit;

import dev.marie.framework.api.ApiStatus;
