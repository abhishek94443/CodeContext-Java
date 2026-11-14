package com.codecontext.core.security.pqc;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Detailed NIST-aligned migration recommendations for transitioning away from quantum-vulnerable cryptography.
 * Aligns strictly with final NIST FIPS standards:
 * - FIPS 203: ML-KEM (Module-Lattice-Based Key-Encapsulation Mechanism)
 * - FIPS 204: ML-DSA (Module-Lattice-Based Digital Signature Algorithm)
 * - FIPS 205: SLH-DSA (Stateless Hash-Based Digital Signature Algorithm)
 * - SP 800-175B: Guideline for Using Cryptographic Standards
 */
public record PqcMigrationRecommendation(
        String standardFamily,
        String targetStandard,
        List<String> targetAlgorithms,
        Optional<String> recommendedParameterSet,
        String keySizeOrSecurityLevel,
        String rationale,
        String transitionStrategy
) {
    public PqcMigrationRecommendation {
        Objects.requireNonNull(standardFamily, "standardFamily cannot be null");
        Objects.requireNonNull(targetStandard, "targetStandard cannot be null");
        targetAlgorithms = targetAlgorithms == null ? List.of() : List.copyOf(targetAlgorithms);
        recommendedParameterSet = recommendedParameterSet == null ? Optional.empty() : recommendedParameterSet;
        Objects.requireNonNull(keySizeOrSecurityLevel, "keySizeOrSecurityLevel cannot be null");
        Objects.requireNonNull(rationale, "rationale cannot be null");
        Objects.requireNonNull(transitionStrategy, "transitionStrategy cannot be null");
    }

    public static PqcMigrationRecommendation mlKemKeyExchange(Optional<String> parameterSet, String rationale) {
        return new PqcMigrationRecommendation(
                "NIST FIPS 203",
                "ML-KEM (Module-Lattice Key Encapsulation)",
                List.of("ML-KEM-512", "ML-KEM-768", "ML-KEM-1024"),
                parameterSet,
                parameterSet.orElse("Category 1, 3, or 5 depending on security requirements"),
                rationale,
                "Implement hybrid key agreement (e.g. X25519 + ML-KEM-768) where protocol support exists during transition."
        );
    }

    public static PqcMigrationRecommendation mlDsaSignature(Optional<String> parameterSet, String rationale) {
        return new PqcMigrationRecommendation(
                "NIST FIPS 204 / FIPS 205",
                "ML-DSA (Lattice) / SLH-DSA (Stateless Hash)",
                List.of("ML-DSA-44", "ML-DSA-65", "ML-DSA-87", "SLH-DSA"),
                parameterSet,
                parameterSet.orElse("Category 2, 3, or 5 depending on security requirements"),
                rationale,
                "Transition to ML-DSA (FIPS 204) for general signing, or SLH-DSA (FIPS 205) for conservative hash-based fallback."
        );
    }

    public static PqcMigrationRecommendation kemDemArchitecture(String rationale) {
        return new PqcMigrationRecommendation(
                "NIST FIPS 203 (Architecture Redesign)",
                "KEM/DEM Hybrid Architecture (ML-KEM + Symmetric AEAD)",
                List.of("ML-KEM-768 + AES-256-GCM"),
                Optional.empty(),
                "Requires application redesign; ML-KEM is not a drop-in Cipher replacement",
                rationale,
                "Re-architect message encryption to encapsulate a symmetric key using ML-KEM, then encrypt payload using AES-256-GCM."
        );
    }

    public static PqcMigrationRecommendation keyPairUsageDependent(String rationale) {
        return new PqcMigrationRecommendation(
                "NIST FIPS 203 / FIPS 204 / FIPS 205",
                "Application Usage Dependent (ML-DSA vs ML-KEM)",
                List.of("ML-DSA", "SLH-DSA", "ML-KEM"),
                Optional.empty(),
                "Usage undetermined at key pair generation call site",
                rationale,
                "Determine downstream key pair use: migrate to ML-DSA/SLH-DSA if used for signatures; migrate to ML-KEM if used for key encapsulation."
        );
    }

    public static PqcMigrationRecommendation symmetricUpgrade(String currentFamily, String rationale) {
        return new PqcMigrationRecommendation(
                "NIST SP 800-175B",
                "Symmetric Key Size Hardening",
                List.of("AES-256-GCM"),
                Optional.of("AES-256"),
                "128-bit Post-Quantum Security Level against Grover Search",
                rationale,
                "Enforce minimum 256-bit symmetric key length to guarantee at least 128-bit post-quantum security."
        );
    }

    public static PqcMigrationRecommendation hashBaseline(String rationale) {
        return new PqcMigrationRecommendation(
                "NIST FIPS 180-4 / FIPS 202",
                "Cryptographic Hash Baseline",
                List.of("SHA-256", "SHA-384", "SHA-512", "SHA3-256", "SHA3-512"),
                Optional.empty(),
                "Standard post-quantum collision and preimage resistance",
                rationale,
                "Maintain modern SHA-2/SHA-3 hashes; use SHA-384 or SHA-512 for conservative post-quantum margins."
        );
    }

    public static PqcMigrationRecommendation classicalRemediation(String currentFamily, String rationale) {
        return new PqcMigrationRecommendation(
                "NIST Classical Deprecation Remediation",
                "Replace Broken Classical Primitive",
                List.of("AES-256-GCM", "SHA-256", "SHA-3"),
                Optional.empty(),
                "Immediate classical vulnerability remediation required",
                rationale,
                "Replace deprecated algorithm immediately to remediate known classical vulnerabilities (not solely a quantum concern)."
        );
    }

    public static PqcMigrationRecommendation pqcApproved(String name, String rationale) {
        return new PqcMigrationRecommendation(
                "NIST Post-Quantum Cryptography Standard",
                "Approved Post-Quantum Primitive (" + name + ")",
                List.of(name),
                Optional.empty(),
                "Quantum-resistant mathematical structure (lattice or hash-based)",
                rationale,
                "Algorithm name matches recognized post-quantum standards. Note: Identifier detection does not verify provider FIPS certification or implementation correctness."
        );
    }

    public static PqcMigrationRecommendation none(String rationale) {
        return new PqcMigrationRecommendation(
                "None Required",
                "Current Baseline Retained",
                List.of(),
                Optional.empty(),
                "N/A",
                rationale,
                "Maintain current implementation and monitor post-quantum standardization updates."
        );
    }
}
