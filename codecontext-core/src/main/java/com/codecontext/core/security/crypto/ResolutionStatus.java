package com.codecontext.core.security.crypto;

/**
 * Indicates the outcome of evaluating an expression (e.g. an algorithm name argument) statically.
 */
public enum ResolutionStatus {
    /**
     * Expression was completely and unambiguously resolved to a concrete constant string.
     */
    RESOLVED,

    /**
     * Expression was partially resolved (e.g. some concatenated parts are known, others unknown).
     */
    PARTIALLY_RESOLVED,

    /**
     * Expression value cannot be determined statically (e.g. dynamic method call, config read).
     */
    UNKNOWN,

    /**
     * Expression has multiple conflicting values across branches or reassignments.
     */
    AMBIGUOUS
}

