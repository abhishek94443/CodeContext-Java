package com.codecontext.core.security.pqc;

import com.codecontext.core.security.inventory.CryptoAsset;

import java.util.Objects;

/**
 * Complete Post-Quantum Cryptography migration assessment for an individual cryptographic asset.
 * Combines quantum exposure classification, purpose derivation, NIST migration guidance,
 * and architectural dependency blast-radius evaluation.
 */
public record PqcAssetAssessment(
        CryptoAsset asset,
        QuantumExposureLevel exposureLevel,
        CryptoPurpose purpose,
        MigrationPriority priority,
        PqcMigrationRecommendation recommendation,
        CryptoAssetBlastRadius blastRadius,
        String quantumThreatSummary,
        AssessmentCategory category
) implements Comparable<PqcAssetAssessment> {

    public PqcAssetAssessment {
        Objects.requireNonNull(asset, "asset cannot be null");
        Objects.requireNonNull(exposureLevel, "exposureLevel cannot be null");
        Objects.requireNonNull(purpose, "purpose cannot be null");
        Objects.requireNonNull(priority, "priority cannot be null");
        Objects.requireNonNull(recommendation, "recommendation cannot be null");
        Objects.requireNonNull(blastRadius, "blastRadius cannot be null");
        quantumThreatSummary = quantumThreatSummary == null ? "" : quantumThreatSummary;
        category = category == null ? AssessmentCategory.from(exposureLevel, asset) : category;
    }

    public PqcAssetAssessment(
            CryptoAsset asset,
            QuantumExposureLevel exposureLevel,
            CryptoPurpose purpose,
            MigrationPriority priority,
            PqcMigrationRecommendation recommendation,
            CryptoAssetBlastRadius blastRadius,
            String quantumThreatSummary
    ) {
        this(asset, exposureLevel, purpose, priority, recommendation, blastRadius, quantumThreatSummary,
                AssessmentCategory.from(exposureLevel, asset));
    }

    public AssessmentCategory assessmentCategory() {
        return category;
    }

    @Override
    public int compareTo(PqcAssetAssessment o) {
        if (o == null) return 1;
        // 1. Sort by Priority rank ascending
        int pCmp = Integer.compare(this.priority.rank(), o.priority.rank());
        if (pCmp != 0) return pCmp;
        // 2. Total reachable classes descending
        int blastCmp = Integer.compare(o.blastRadius.totalReachableClasses(), this.blastRadius.totalReachableClasses());
        if (blastCmp != 0) return blastCmp;
        // 3. Asset name
        return this.asset.name().compareToIgnoreCase(o.asset.name());
    }
}
