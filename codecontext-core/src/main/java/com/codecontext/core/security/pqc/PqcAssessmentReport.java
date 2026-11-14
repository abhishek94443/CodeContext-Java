package com.codecontext.core.security.pqc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Top-level aggregation of the Post-Quantum Cryptography migration assessment.
 */
public record PqcAssessmentReport(
        List<PqcAssetAssessment> assessments,
        int totalAssetsAnalyzed,
        int quantumVulnerableCount,
        int quantumImpactedCount,
        int classicallyWeakCount,
        int quantumSafeCount,
        int unknownExposureCount,
        int reviewRequiredPriorityCount,
        int undeterminedPriorityCount,
        int lowPriorityCount,
        int totalAffectedClassesInBlastRadius,
        Map<QuantumExposureLevel, Integer> exposureBreakdown,
        Map<CryptoPurpose, Integer> purposeBreakdown
) {
    public PqcAssessmentReport {
        assessments = assessments == null ? List.of() : List.copyOf(assessments);
        exposureBreakdown = exposureBreakdown == null ? Map.of() : Collections.unmodifiableMap(exposureBreakdown);
        purposeBreakdown = purposeBreakdown == null ? Map.of() : Collections.unmodifiableMap(purposeBreakdown);
    }

    public int criticalPriorityCount() {
        return (int) assessments.stream().filter(a -> a.priority() == MigrationPriority.CRITICAL).count();
    }

    public int highPriorityCount() {
        return (int) assessments.stream().filter(a -> a.priority() == MigrationPriority.HIGH).count();
    }

    public int mediumPriorityCount() {
        return (int) assessments.stream().filter(a -> a.priority() == MigrationPriority.MEDIUM).count();
    }

    public int pqcMigrationCandidatesCount() {
        return (int) assessments.stream().filter(a -> a.category() == AssessmentCategory.PQC_MIGRATION_CANDIDATE).count();
    }

    public int classicalRemediationCount() {
        return (int) assessments.stream().filter(a -> a.category() == AssessmentCategory.CLASSICAL_REMEDIATION).count();
    }

    public int quantumConsiderationsCount() {
        return (int) assessments.stream().filter(a -> a.category() == AssessmentCategory.QUANTUM_CONSIDERATION).count();
    }

    public int unknownOrUnresolvedCount() {
        return (int) assessments.stream().filter(a -> a.category() == AssessmentCategory.UNKNOWN_OR_UNRESOLVED).count();
    }

    public static PqcAssessmentReport empty() {
        return new PqcAssessmentReport(
                List.of(),
                0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                Map.of(),
                Map.of()
        );
    }
}
