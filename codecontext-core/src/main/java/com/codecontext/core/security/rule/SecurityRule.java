package com.codecontext.core.security.rule;

import com.codecontext.core.finding.Finding;
import com.codecontext.core.finding.RuleMetadata;

import java.util.List;

/**
 * Contract for extensible cryptographic and security analysis rules.
 */
public interface SecurityRule {

    /**
     * Immutable metadata describing this rule.
     */
    RuleMetadata metadata();

    /**
     * Unique stable rule identifier (e.g. "CC-SEC-001").
     */
    default String id() {
        return metadata().id();
    }

    /**
     * Human-readable rule name.
     */
    default String name() {
        return metadata().name();
    }

    /**
     * Evaluates this rule against a single compilation unit context.
     *
     * @param context Immutable context containing the AST, source path, and symbol lookup.
     * @return List of findings discovered (may be empty, never null).
     */
    List<Finding> analyze(CompilationUnitContext context);
}

