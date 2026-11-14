package com.codecontext.core.security.pqc;

import com.codecontext.core.security.inventory.CryptoAsset;

/**
 * Distinguishes the actionable category of cryptographic findings:
 * <ul>
 *   <li><b>PQC_MIGRATION_CANDIDATE</b>: Asymmetric public-key algorithms completely broken by Shor's algorithm on a CRQC.</li>
 *   <li><b>CLASSICAL_REMEDIATION</b>: Broken or deprecated classical primitives requiring immediate vulnerability remediation.</li>
 *   <li><b>QUANTUM_CONSIDERATION</b>: Modern symmetric ciphers and hashes subject to Grover/BHT bounds (retain baseline or harden key length; not mandatory PQC replacements).</li>
 *   <li><b>UNKNOWN_OR_UNRESOLVED</b>: Dynamic expressions or implementation-dependent usages requiring manual audit.</li>
 * </ul>
 */
public enum AssessmentCategory {
    PQC_MIGRATION_CANDIDATE(
            "PQC Migration Candidate",
            "Public-key cryptography vulnerable to polynomial-time Shor's algorithm on a CRQC"
    ),
    CLASSICAL_REMEDIATION(
            "Classical Remediation",
            "Broken or deprecated classical algorithm requiring immediate remediation regardless of quantum timelines"
    ),
    QUANTUM_CONSIDERATION(
            "Modern Symmetric/Hash Consideration",
            "Modern symmetric cipher or hash subject to Grover/BHT bounds; not a mandatory PQC replacement"
    ),
    UNKNOWN_OR_UNRESOLVED(
            "Unknown / Unresolved Usage",
            "Unresolved dynamic expression or implementation-dependent usage requiring manual audit"
    );

    private final String displayName;
    private final String description;

    AssessmentCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public static AssessmentCategory from(QuantumExposureLevel exposure, CryptoAsset asset) {
        if (!asset.isResolved() || exposure == QuantumExposureLevel.UNKNOWN) {
            return UNKNOWN_OR_UNRESOLVED;
        }
        return switch (exposure) {
            case QUANTUM_VULNERABLE, QUANTUM_SAFE -> PQC_MIGRATION_CANDIDATE;
            case CLASSICALLY_WEAK -> CLASSICAL_REMEDIATION;
            case QUANTUM_IMPACTED -> QUANTUM_CONSIDERATION;
            case UNKNOWN -> UNKNOWN_OR_UNRESOLVED;
        };
    }
}
