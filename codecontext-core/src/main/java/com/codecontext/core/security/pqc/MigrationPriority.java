package com.codecontext.core.security.pqc;

/**
 * Migration priority assessment for cryptographic assets.
 * Strictly separates static quantum exposure from organizational/business migration priority.
 * In the absence of enterprise data classification, secret longevity context, and threat models,
 * static analysis reports REVIEW_REQUIRED or PRIORITY_UNDETERMINED rather than inventing criticality.
 */
public enum MigrationPriority {
    /**
     * Organizational review required: Quantum-vulnerable or classically deprecated asset
     * whose migration priority depends on enterprise data retention, threat model, and exposure.
     */
    REVIEW_REQUIRED("Review Required", 1),

    /**
     * Priority cannot be determined statically without organizational business context.
     */
    PRIORITY_UNDETERMINED("Priority Undetermined", 2),

    /**
     * Low priority: Asset is either quantum-safe or non-security operational usage.
     */
    LOW("Low", 3),

    /**
     * Informational observation.
     */
    INFO("Info", 4),

    /**
     * Critical priority: Explicitly designated by organizational policy or validated external risk criteria.
     */
    CRITICAL("Critical", 5),

    /**
     * High priority: Explicitly designated by organizational policy or validated external risk criteria.
     */
    HIGH("High", 6),

    /**
     * Medium priority: Explicitly designated by organizational policy or validated external risk criteria.
     */
    MEDIUM("Medium", 7);

    private final String label;
    private final int rank;

    MigrationPriority(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    public int rank() {
        return rank;
    }
}
