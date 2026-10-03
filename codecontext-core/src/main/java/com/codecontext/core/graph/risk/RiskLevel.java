package com.codecontext.core.graph.risk;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static RiskLevel fromScore(double score) {
        if (score <= 25.0) return LOW;
        if (score <= 50.0) return MEDIUM;
        if (score <= 75.0) return HIGH;
        return CRITICAL;
    }
}
