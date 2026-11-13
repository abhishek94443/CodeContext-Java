package com.codecontext.core.finding;

/**
 * Normalized severity level for analysis findings.
 */
public enum FindingSeverity {
    INFO("note"),
    LOW("note"),
    MEDIUM("warning"),
    HIGH("error"),
    CRITICAL("error");

    private final String sarifLevel;

    FindingSeverity(String sarifLevel) {
        this.sarifLevel = sarifLevel;
    }

    /**
     * Maps finding severity to standard OASIS SARIF 2.1.0 result level.
     */
    public String toSarifLevel() {
        return sarifLevel;
    }
}

