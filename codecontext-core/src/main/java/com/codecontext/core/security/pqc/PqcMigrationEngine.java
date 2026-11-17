package com.codecontext.core.security.pqc;

import com.codecontext.core.graph.analytics.BlastRadiusEngine;
import com.codecontext.core.graph.analytics.ImpactAnalysisResult;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.security.inventory.CryptoAsset;
import com.codecontext.core.security.inventory.CryptoAssetUsage;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.inventory.CryptoPrimitiveType;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.util.*;

/**
 * Enterprise Post-Quantum Cryptography (PQC) migration assessment and architectural impact engine.
 * Integrates canonical cryptographic inventories with JGraphT call graph blast-radius analysis.
 */
public class PqcMigrationEngine {

    private final BlastRadiusEngine blastRadiusEngine;

    /**
     * Graph-based threshold for high structural fan-in. A target class with direct callers (in-degree)
     * >= HIGH_FAN_IN_THRESHOLD represents a shared component or utility where class reachability
     * does not prove execution of specific methods.
     */
    public static final int HIGH_FAN_IN_THRESHOLD = 5;

    public PqcMigrationEngine() {
        this.blastRadiusEngine = new BlastRadiusEngine();
    }

    public PqcMigrationEngine(BlastRadiusEngine blastRadiusEngine) {
        this.blastRadiusEngine = Objects.requireNonNull(blastRadiusEngine, "blastRadiusEngine cannot be null");
    }

    /**
     * Assesses a cryptographic inventory for post-quantum vulnerability and architectural migration impact.
     *
     * @param inventory The canonical cryptographic inventory.
     * @param graph     The complete codebase dependency/call graph (optional).
     * @param metrics   Hotspot metrics (PageRank) for distance-decayed risk calculations (optional).
     * @param maxDepth  Maximum transitive blast radius search depth (default: 10).
     * @return Complete PqcAssessmentReport.
     */
    public PqcAssessmentReport assessInventory(
            CryptoInventory inventory,
            DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
            Map<String, HotspotMetric> metrics,
            int maxDepth
    ) {
        if (inventory == null || inventory.assets().isEmpty()) {
            return PqcAssessmentReport.empty();
        }

        List<PqcAssetAssessment> assessments = new ArrayList<>();
        Set<String> allAffectedClasses = new HashSet<>();

        Map<QuantumExposureLevel, Integer> exposureBreakdown = new LinkedHashMap<>();
        Map<CryptoPurpose, Integer> purposeBreakdown = new LinkedHashMap<>();

        int qVulnerable = 0;
        int qImpacted = 0;
        int qClassicallyWeak = 0;
        int qSafe = 0;
        int qUnknown = 0;

        int pReviewRequired = 0;
        int pUndetermined = 0;
        int pLow = 0;

        for (CryptoAsset asset : inventory.assets()) {
            QuantumExposureLevel exposure = classifyQuantumExposure(asset);
            CryptoPurpose purpose = deriveCryptoPurpose(asset);
            MigrationPriority priority = assignMigrationPriority(asset, exposure, purpose);
            PqcMigrationRecommendation rec = generateRecommendation(asset, exposure, purpose);
            CryptoAssetBlastRadius blastRadius = computeBlastRadius(asset, graph, metrics, maxDepth);
            String threatSummary = summarizeQuantumThreat(asset, exposure, purpose);

            PqcAssetAssessment assessment = new PqcAssetAssessment(
                    asset,
                    exposure,
                    purpose,
                    priority,
                    rec,
                    blastRadius,
                    threatSummary
            );
            assessments.add(assessment);

            allAffectedClasses.addAll(blastRadius.directDependentClasses());
            allAffectedClasses.addAll(blastRadius.transitiveOnlyDependentClasses());

            exposureBreakdown.merge(exposure, 1, Integer::sum);
            purposeBreakdown.merge(purpose, 1, Integer::sum);

            switch (exposure) {
                case QUANTUM_VULNERABLE -> qVulnerable++;
                case QUANTUM_IMPACTED -> qImpacted++;
                case CLASSICALLY_WEAK -> qClassicallyWeak++;
                case QUANTUM_SAFE -> qSafe++;
                case UNKNOWN -> qUnknown++;
            }

            switch (priority) {
                case REVIEW_REQUIRED, CRITICAL, HIGH, MEDIUM -> pReviewRequired++;
                case PRIORITY_UNDETERMINED -> pUndetermined++;
                case LOW, INFO -> pLow++;
            }
        }

        Collections.sort(assessments);

        return new PqcAssessmentReport(
                assessments,
                inventory.assets().size(),
                qVulnerable,
                qImpacted,
                qClassicallyWeak,
                qSafe,
                qUnknown,
                pReviewRequired,
                pUndetermined,
                pLow,
                allAffectedClasses.size(),
                exposureBreakdown,
                purposeBreakdown
        );
    }

    /**
     * Evidence-based quantum exposure classification.
     * Separates classical insecurity, public-key quantum exposure (Shor),
     * symmetric/hash considerations (Grover/BHT), and standardized PQC algorithms.
     */
    public QuantumExposureLevel classifyQuantumExposure(CryptoAsset asset) {
        if (!asset.isResolved()) {
            return QuantumExposureLevel.UNKNOWN;
        }

        String fam = asset.algorithmFamily().toUpperCase(Locale.ROOT);
        String name = asset.name().toUpperCase(Locale.ROOT);

        // 1. Quantum Safe (explicit standardized PQC algorithms)
        if (fam.contains("ML-KEM") || fam.contains("ML-DSA") || fam.contains("SLH-DSA")
                || fam.contains("KYBER") || fam.contains("DILITHIUM") || fam.contains("SPHINCS")
                || fam.contains("FALCON") || fam.contains("XMSS") || fam.contains("LMS")
                || name.contains("ML-KEM") || name.contains("ML-DSA") || name.contains("SLH-DSA")
                || name.contains("KYBER") || name.contains("DILITHIUM") || name.contains("SPHINCS")
                || name.contains("FALCON") || name.contains("XMSS") || name.contains("LMS")) {
            return QuantumExposureLevel.QUANTUM_SAFE;
        }

        // 2. Classically Weak / Broken (DES, 3DES, MD5, SHA-1, RC4, Blowfish)
        // Their primary vulnerability is classical cryptanalysis; not merely a quantum issue
        if (fam.equals("DES") || fam.equals("3DES") || fam.equals("TRIPLEDES") || fam.equals("DESEDE")
                || fam.equals("MD2") || fam.equals("MD4") || fam.equals("MD5")
                || fam.equals("SHA-1") || fam.equals("SHA1") || fam.equals("RC2") || fam.equals("RC4") || fam.equals("ARCFOUR")
                || fam.equals("BLOWFISH")) {
            return QuantumExposureLevel.CLASSICALLY_WEAK;
        }

        // SHA1PRNG: Provider-dependent implementation uncertainty.
        // Do not automatically declare every SHA1PRNG implementation classically broken.
        // Standard OpenJDK/Oracle SUN implementations securely draw from OS entropy (/dev/urandom or Windows CryptoAPI);
        // however, legacy Android Apache Harmony implementations had critical deterministic PRNG state replacement flaws.
        // In the absence of an established vulnerable provider or broken seed flow, represent uncertainty.
        if (name.contains("SHA1PRNG") || fam.equals("SHA1PRNG")) {
            return QuantumExposureLevel.UNKNOWN;
        }

        // 3. Classical Public-Key Cryptography -> Quantum Vulnerable (Shor's algorithm breaks discrete log / factoring)
        if (fam.contains("RSA") || fam.contains("DIFFIE-HELLMAN") || fam.equals("DH") || fam.equals("ECDH")
                || fam.contains("ECDSA") || fam.equals("DSA") || fam.contains("EC") || fam.contains("ED25519")
                || fam.contains("ED448") || fam.contains("X25519") || fam.contains("X448")
                || name.contains("RSA") || name.contains("ECDSA") || name.contains("DIFFIE") || name.contains("DH")
                || asset.primitiveType() == CryptoPrimitiveType.PUBLIC_KEY_ENCRYPTION
                || asset.primitiveType() == CryptoPrimitiveType.KEY_AGREEMENT
                || asset.primitiveType() == CryptoPrimitiveType.SIGNATURE
                || asset.primitiveType() == CryptoPrimitiveType.KEY_PAIR_GENERATOR) {
            return QuantumExposureLevel.QUANTUM_VULNERABLE;
        }

        // 4. Secure Symmetric Ciphers and Modern Hashes -> Quantum Impacted (Grover/BHT quadratic speedup)
        if (asset.primitiveType() == CryptoPrimitiveType.BLOCK_CIPHER
                || asset.primitiveType() == CryptoPrimitiveType.STREAM_CIPHER
                || asset.primitiveType() == CryptoPrimitiveType.MESSAGE_DIGEST
                || asset.primitiveType() == CryptoPrimitiveType.MAC
                || asset.primitiveType() == CryptoPrimitiveType.KEY_GENERATOR
                || asset.primitiveType() == CryptoPrimitiveType.SECURE_RANDOM) {
            return QuantumExposureLevel.QUANTUM_IMPACTED;
        }

        if (asset.primitiveType() == CryptoPrimitiveType.TLS_PROTOCOL) {
            return QuantumExposureLevel.UNKNOWN; // SSLContext identifier does not establish negotiated key exchange
        }

        return QuantumExposureLevel.UNKNOWN;
    }

    /**
     * Derives precise cryptographic purpose based directly on immediate API primitive type and operation.
     * Distinguishes immediate API function from inferred downstream application use.
     */
    public CryptoPurpose deriveCryptoPurpose(CryptoAsset asset) {
        if (!asset.isResolved()) {
            return CryptoPurpose.UNKNOWN;
        }

        return switch (asset.primitiveType()) {
            case KEY_PAIR_GENERATOR -> CryptoPurpose.KEY_PAIR_GENERATION;
            case KEY_GENERATOR -> CryptoPurpose.SYMMETRIC_KEY_GENERATION;
            case SECURE_RANDOM -> CryptoPurpose.SECURE_RANDOM;
            case SIGNATURE -> CryptoPurpose.DIGITAL_SIGNATURE;
            case KEY_AGREEMENT -> CryptoPurpose.KEY_AGREEMENT;
            case PUBLIC_KEY_ENCRYPTION -> CryptoPurpose.PUBLIC_KEY_ENCRYPTION;
            case BLOCK_CIPHER, STREAM_CIPHER -> CryptoPurpose.SYMMETRIC_ENCRYPTION;
            case MESSAGE_DIGEST -> CryptoPurpose.HASH_FUNCTION;
            case MAC -> CryptoPurpose.MAC;
            case TLS_PROTOCOL -> CryptoPurpose.SECURE_TRANSPORT;
            case OTHER -> CryptoPurpose.UNKNOWN;
            default -> CryptoPurpose.UNKNOWN;
        };
    }

    /**
     * Determines migration priority.
     * Strictly separates static quantum exposure from organizational business migration priority.
     * When business context (data longevity, confidentiality horizon) is unavailable,
     * reports REVIEW_REQUIRED or PRIORITY_UNDETERMINED rather than inventing criticality.
     */
    public MigrationPriority assignMigrationPriority(CryptoAsset asset, QuantumExposureLevel exposure, CryptoPurpose purpose) {
        if (!asset.isResolved()) {
            return MigrationPriority.PRIORITY_UNDETERMINED;
        }

        if (exposure == QuantumExposureLevel.QUANTUM_SAFE) {
            return MigrationPriority.LOW;
        }

        // In the absence of enterprise business context, static analysis mandates human review
        return MigrationPriority.REVIEW_REQUIRED;
    }

    /**
     * Generates NIST PQC migration recommendation without guessing parameter sets unless context is available.
     */
    public PqcMigrationRecommendation generateRecommendation(CryptoAsset asset, QuantumExposureLevel exposure, CryptoPurpose purpose) {
        if (!asset.isResolved()) {
            return PqcMigrationRecommendation.none("Algorithm is dynamic or unresolvable at compile time. Resolve call site before planning PQC migration.");
        }

        if (purpose == CryptoPurpose.SECURE_TRANSPORT) {
            return PqcMigrationRecommendation.none("TLS protocol selection does not identify key establishment, certificates, or negotiated groups. Inspect runtime configuration before choosing migration algorithms or parameter sets.");
        }

        if (exposure == QuantumExposureLevel.CLASSICALLY_WEAK) {
            return PqcMigrationRecommendation.classicalRemediation(
                    asset.algorithmFamily(),
                    "Legacy algorithm (" + asset.name() + ") is broken or deprecated under classical cryptanalysis. Immediate classical vulnerability remediation is required regardless of quantum timelines."
            );
        }

        if (exposure == QuantumExposureLevel.QUANTUM_VULNERABLE) {
            if (purpose == CryptoPurpose.DIGITAL_SIGNATURE) {
                return PqcMigrationRecommendation.mlDsaSignature(
                        Optional.empty(),
                        "Classical digital signatures (" + asset.name() + ") are vulnerable to forgery under Shor's algorithm. Evaluate NIST FIPS 204 ML-DSA for general signatures, or FIPS 205 SLH-DSA for conservative hash-based fallback."
                );
            }
            if (purpose == CryptoPurpose.KEY_AGREEMENT) {
                return PqcMigrationRecommendation.mlKemKeyExchange(
                        Optional.empty(),
                        "Classical key agreement protocols (" + asset.name() + ") are completely broken by Shor's algorithm, exposing sessions to 'Harvest Now, Decrypt Later' threats. Transition to NIST FIPS 203 ML-KEM via hybrid key exchange."
                );
            }
            if (purpose == CryptoPurpose.PUBLIC_KEY_ENCRYPTION) {
                return PqcMigrationRecommendation.kemDemArchitecture(
                        "RSA public-key encryption (" + asset.name() + ") is completely broken by Shor's algorithm. Note: NIST FIPS 203 ML-KEM is a Key Encapsulation Mechanism, not a direct drop-in Cipher replacement for arbitrary plaintext. Re-architect to encapsulate a symmetric key using ML-KEM, then encrypt payload using AES-256-GCM."
                );
            }
            if (purpose == CryptoPurpose.KEY_PAIR_GENERATION) {
                return PqcMigrationRecommendation.keyPairUsageDependent(
                        "KeyPairGenerator for " + asset.name() + " generates classical asymmetric keys vulnerable to Shor's algorithm. Intended application usage (signature vs key exchange vs encryption) is undetermined at this key generation site. Do not assume ML-KEM without establishing key usage."
                );
            }

        }

        if (exposure == QuantumExposureLevel.QUANTUM_IMPACTED) {
            if (purpose == CryptoPurpose.SYMMETRIC_ENCRYPTION || purpose == CryptoPurpose.SYMMETRIC_KEY_GENERATION) {
                String fam = asset.algorithmFamily().toUpperCase(Locale.ROOT);
                if (asset.name().contains("128")) {
                    return PqcMigrationRecommendation.symmetricUpgrade(
                            fam,
                            "Grover's algorithm provides quadratic speedup against symmetric key search, reducing effective security of 128-bit keys to ~64 bits. Upgrade to 256-bit keys (e.g. AES-256-GCM)."
                    );
                }
                return PqcMigrationRecommendation.symmetricUpgrade(
                        fam,
                        "Grover's algorithm provides quadratic speedup against symmetric key search. AES-256 maintains 128 bits of post-quantum security margin."
                );
            }
            if (purpose == CryptoPurpose.HASH_FUNCTION || purpose == CryptoPurpose.MAC) {
                return PqcMigrationRecommendation.hashBaseline(
                        "Grover's algorithm provides quadratic speedup for preimages O(2^(n/2)); quantum collision finding (BHT) is bounded by O(2^(n/3)) but constrained by extreme QRAM requirements. SHA-256/384/512 remain robust; use SHA-384 or SHA-512 for conservative post-quantum margins."
                );
            }
            if (purpose == CryptoPurpose.SECURE_RANDOM) {
                return PqcMigrationRecommendation.none(
                        "Cryptographically secure pseudo-random generation with approved entropy source. Maintain current NIST SP 800-90A DRBG baseline."
                );
            }
        }

        if (exposure == QuantumExposureLevel.QUANTUM_SAFE) {
            return PqcMigrationRecommendation.pqcApproved(
                    asset.name(),
                    "Recognized standardized post-quantum algorithm name. Note: Detection of algorithm identifier does not verify provider FIPS certification, side-channel hardening, or implementation correctness."
            );
        }

        if (asset.name().toUpperCase(Locale.ROOT).contains("SHA1PRNG") || asset.algorithmFamily().equalsIgnoreCase("SHA1PRNG")) {
            return new PqcMigrationRecommendation(
                    "JCA Provider & Seeding Implementation Audit",
                    "Audit Provider & Migrate to DRBG",
                    List.of("NativePRNG", "DRBG (NIST SP 800-90A)"),
                    Optional.empty(),
                    "Implementation-Dependent Security Margin",
                    "SHA1PRNG is not intrinsically broken on modern JDKs when seeded from OS entropy (/dev/urandom or CryptoAPI). However, legacy providers (e.g. Apache Harmony) have known state-replacement vulnerabilities. SetSeed() must not be used to replace entropy. Audit provider configuration and migrate to NIST SP 800-90A DRBG.",
                    "Verify runtime security provider in java.security; upgrade to SecureRandom.getInstanceStrong() or SHA-256-based DRBG."
            );
        }

        return PqcMigrationRecommendation.none("No immediate PQC migration required.");
    }

    /**
     * Traverses the JGraphT call graph to compute the upstream blast radius.
     * Strictly separates direct upstream callers (depth = 1) from transitive-only callers (depth >= 2).
     */
    public CryptoAssetBlastRadius computeBlastRadius(
            CryptoAsset asset,
            DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph,
            Map<String, HotspotMetric> metrics,
            int maxDepth
    ) {
        if (graph == null || asset.usages().isEmpty()) {
            return CryptoAssetBlastRadius.empty(asset);
        }

        Set<String> directCallers = new LinkedHashSet<>();
        Set<String> transitiveOnlyCallers = new LinkedHashSet<>();
        List<String> affectedUnits = new ArrayList<>();
        double cumulativeRisk = 0.0;
        Map<String, Integer> minimumCallerDepths = new LinkedHashMap<>();
        int maxDepthObserved = 0;
        boolean hasSharedUtility = false;

        // Collect all enclosing classes containing observed usages of this asset
        Set<String> usageClasses = new LinkedHashSet<>();
        for (CryptoAssetUsage u : asset.usages()) {
            u.enclosingClass().ifPresent(usageClasses::add);
            String unitPath = u.filePath().toString().replace('\\', '/');
            if (!affectedUnits.contains(unitPath)) {
                affectedUnits.add(unitPath);
            }
        }

        // If enclosing class was not explicitly tagged in usage, match graph vertices by source path
        if (usageClasses.isEmpty()) {
            for (CryptoAssetUsage u : asset.usages()) {
                String uPath = u.filePath().toString().replace('\\', '/');
                for (TypeVertex v : graph.vertexSet()) {
                    if (v.filePath().isPresent() && v.filePath().get().toString().replace('\\', '/').endsWith(uPath)) {
                        usageClasses.add(v.fqcn());
                    }
                }
            }
        }

        for (String targetFqcn : usageClasses) {
            ImpactAnalysisResult impact = blastRadiusEngine.analyzeImpact(targetFqcn, graph, metrics, maxDepth);

            // 1-hop direct callers
            directCallers.addAll(impact.directCallers());

            if (impact.directCallers().size() >= HIGH_FAN_IN_THRESHOLD) {
                hasSharedUtility = true;
            }

            // Transitive-only callers (depth >= 2, strictly excluding 1-hop direct callers)
            for (Map.Entry<String, Integer> entry : impact.transitiveCallerDepths().entrySet()) {
                String caller = entry.getKey();
                int depth = entry.getValue();
                if (depth > maxDepthObserved) {
                    maxDepthObserved = depth;
                }
                if (depth >= 2 && !impact.directCallers().contains(caller)) {
                    transitiveOnlyCallers.add(caller);
                }
            }
            impact.transitiveCallerDepths().forEach((caller, depth) -> minimumCallerDepths.merge(caller, depth, Math::min));
        }

        maxDepthObserved = 0;
        for (var entry : minimumCallerDepths.entrySet()) {
            var metric = metrics == null ? null : metrics.get(entry.getKey());
            double rawScore = metric == null ? 0.01 : metric.rawScore();
            cumulativeRisk += rawScore / Math.pow(2.0, entry.getValue() - 1);
            maxDepthObserved = Math.max(maxDepthObserved, entry.getValue());
        }

        // Strictly ensure transitive-only callers do not include any direct caller
        transitiveOnlyCallers.removeAll(directCallers);

        StringBuilder note = new StringBuilder();
        note.append("Class-level architectural dependency approximation: graph edges reflect compilation unit dependencies, not method-level interprocedural control flow. Reachability of the enclosing class does not prove runtime invocation of the specific cryptographic operation.");
        if (hasSharedUtility) {
            note.append(String.format(" The enclosing class exhibits high structural graph fan-in (%d direct caller classes), increasing the likelihood that upstream callers invoke unrelated methods.", directCallers.size()));
        }
        String semanticsNote = note.toString();

        return new CryptoAssetBlastRadius(
                asset.assetId(),
                asset.name(),
                directCallers,
                transitiveOnlyCallers,
                directCallers.size() + transitiveOnlyCallers.size(),
                maxDepthObserved,
                cumulativeRisk,
                affectedUnits,
                hasSharedUtility,
                semanticsNote
        );
    }

    private String summarizeQuantumThreat(CryptoAsset asset, QuantumExposureLevel exposure, CryptoPurpose purpose) {
        if (asset.name().toUpperCase(Locale.ROOT).contains("SHA1PRNG") || asset.algorithmFamily().equalsIgnoreCase("SHA1PRNG")) {
            return "SHA1PRNG security depends on the runtime security provider and seeding mechanism. The Sun/Oracle JDK provider securely seeds from OS entropy (/dev/urandom or Windows CryptoAPI) and is not broken; however, legacy Android Apache Harmony implementations had critical state-replacement flaws. Provider verification is required.";
        }

        if (purpose == CryptoPurpose.SECURE_TRANSPORT) return "TLS key establishment is not observable from the SSLContext algorithm name; quantum exposure is unknown.";

        return switch (exposure) {
            case QUANTUM_VULNERABLE -> "Shor's polynomial-time algorithm completely undermines " + asset.algorithmFamily() +
                    " (integer factorization / discrete logarithm). Private keys and ciphertexts are broken once a Cryptanalytically Relevant Quantum Computer (CRQC) is realized.";
            case QUANTUM_IMPACTED -> {
                if (purpose == CryptoPurpose.SYMMETRIC_ENCRYPTION || purpose == CryptoPurpose.SYMMETRIC_KEY_GENERATION) {
                    yield "Grover's quantum search algorithm provides quadratic speedup for key search O(2^(k/2)). AES-256 retains 128-bit quantum security; AES-128 drops to 64-bit.";
                }
                yield "Grover provides quadratic speedup for preimages O(2^(n/2)); quantum collision attacks (BHT) are constrained by severe QRAM hardware bounds. Hashes remain robust.";
            }
            case CLASSICALLY_WEAK -> "Algorithm is broken or deprecated under classical cryptanalysis (e.g. collision attacks, small key/block size). Insecurity is fundamentally classical, not quantum.";
            case QUANTUM_SAFE -> "Recognized standardized post-quantum algorithm providing lattice or hash-based mathematical resistance.";
            case UNKNOWN -> "Dynamic or unresolved cryptographic argument; quantum exposure cannot be determined statically without runtime instrumentation.";
        };
    }
}
