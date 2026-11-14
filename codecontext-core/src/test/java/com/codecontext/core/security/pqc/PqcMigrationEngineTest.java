package com.codecontext.core.security.pqc;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.graph.DependencyGraphBuilder;
import com.codecontext.core.graph.GraphBuildOptions;
import com.codecontext.core.graph.analytics.BlastRadiusEngine;
import com.codecontext.core.graph.analytics.PageRankEngine;
import com.codecontext.core.graph.analytics.PageRankOptions;
import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.model.*;
import com.codecontext.core.security.crypto.CryptoAlgorithm;
import com.codecontext.core.security.crypto.CryptoApiOwner;
import com.codecontext.core.security.crypto.CryptoApiUsage;
import com.codecontext.core.security.crypto.ExpressionResolution;
import com.codecontext.core.security.inventory.CryptoAsset;
import com.codecontext.core.security.inventory.CryptoInventory;
import com.codecontext.core.security.inventory.CryptoInventoryEngine;
import com.codecontext.core.security.inventory.CryptoPrimitiveType;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PqcMigrationEngineTest {

    private PqcMigrationEngine pqcEngine;
    private CryptoInventoryEngine inventoryEngine;

    @BeforeEach
    void setUp() {
        pqcEngine = new PqcMigrationEngine();
        inventoryEngine = new CryptoInventoryEngine();
    }

    @Test
    @DisplayName("Classifies classical asymmetric algorithms as QUANTUM_VULNERABLE with NIST FIPS 203/204 targets")
    void testClassicalAsymmetricClassification() {
        Path file = Path.of("src/CryptoAuth.java");

        // RSA Signatures -> DIGITAL_SIGNATURE -> FIPS 204 ML-DSA
        CryptoApiUsage rsaSign = createUsage(file, 10, 5, CryptoApiOwner.SIGNATURE, "SHA256withRSA", "com.example.CryptoAuth", "signToken");
        // Diffie-Hellman Key Agreement -> KEY_ESTABLISHMENT -> FIPS 203 ML-KEM
        CryptoApiUsage dhAgree = createUsage(file, 20, 5, CryptoApiOwner.KEY_AGREEMENT, "DH", "com.example.CryptoAuth", "agreeKey");
        // RSA-OAEP Encryption -> PUBLIC_KEY_ENCRYPTION -> FIPS 203 ML-KEM
        CryptoApiUsage rsaOaep = createUsage(file, 30, 5, CryptoApiOwner.CIPHER, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "com.example.CryptoAuth", "encryptPayload");

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(rsaSign, dhAgree, rsaOaep), Path.of("."));
        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, null, null, 10);

        assertThat(report.totalAssetsAnalyzed()).isEqualTo(3);
        assertThat(report.quantumVulnerableCount()).isEqualTo(3);
        assertThat(report.quantumImpactedCount()).isEqualTo(0);

        // Check RSA Signature Assessment
        PqcAssetAssessment signAssessment = report.assessments().stream()
                .filter(a -> a.asset().name().equals("SHA256withRSA"))
                .findFirst().orElseThrow();
        assertThat(signAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.QUANTUM_VULNERABLE);
        assertThat(signAssessment.purpose()).isEqualTo(CryptoPurpose.DIGITAL_SIGNATURE);
        assertThat(signAssessment.priority()).isEqualTo(MigrationPriority.REVIEW_REQUIRED);
        assertThat(signAssessment.recommendation().standardFamily()).contains("FIPS 204");
        assertThat(signAssessment.recommendation().targetStandard()).contains("ML-DSA");

        // Check DH Key Exchange Assessment
        PqcAssetAssessment dhAssessment = report.assessments().stream()
                .filter(a -> a.asset().algorithmFamily().equalsIgnoreCase("Diffie-Hellman"))
                .findFirst().orElseThrow();
        assertThat(dhAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.QUANTUM_VULNERABLE);
        assertThat(dhAssessment.purpose()).isEqualTo(CryptoPurpose.KEY_AGREEMENT);
        assertThat(dhAssessment.priority()).isEqualTo(MigrationPriority.REVIEW_REQUIRED);
        assertThat(dhAssessment.recommendation().standardFamily()).contains("FIPS 203");
        assertThat(dhAssessment.recommendation().targetStandard()).contains("ML-KEM");

        // Check RSA-OAEP Encryption Assessment
        PqcAssetAssessment rsaEncAssessment = report.assessments().stream()
                .filter(a -> a.asset().name().contains("OAEP"))
                .findFirst().orElseThrow();
        assertThat(rsaEncAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.QUANTUM_VULNERABLE);
        assertThat(rsaEncAssessment.purpose()).isEqualTo(CryptoPurpose.PUBLIC_KEY_ENCRYPTION);
        assertThat(rsaEncAssessment.priority()).isEqualTo(MigrationPriority.REVIEW_REQUIRED);
        assertThat(rsaEncAssessment.recommendation().standardFamily()).contains("FIPS 203");
    }

    @Test
    @DisplayName("Classifies symmetric ciphers and digests as QUANTUM_IMPACTED with key-size hardening guidance")
    void testSymmetricAndDigestClassification() {
        Path file = Path.of("src/SymmetricService.java");
        CryptoApiUsage aesGcm = createUsage(file, 15, 5, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding", "com.example.SymmetricService", "encrypt");
        CryptoApiUsage sha256 = createUsage(file, 25, 5, CryptoApiOwner.MESSAGE_DIGEST, "SHA-256", "com.example.SymmetricService", "hash");

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(aesGcm, sha256), Path.of("."));
        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, null, null, 10);

        assertThat(report.quantumVulnerableCount()).isEqualTo(0);
        assertThat(report.quantumImpactedCount()).isEqualTo(2);

        PqcAssetAssessment aesAssessment = report.assessments().stream()
                .filter(a -> a.asset().name().equals("AES/GCM/NoPadding"))
                .findFirst().orElseThrow();
        assertThat(aesAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.QUANTUM_IMPACTED);
        assertThat(aesAssessment.purpose()).isEqualTo(CryptoPurpose.SYMMETRIC_ENCRYPTION);
        assertThat(aesAssessment.priority()).isEqualTo(MigrationPriority.REVIEW_REQUIRED);
        assertThat(aesAssessment.recommendation().targetStandard()).contains("Symmetric Key Size Hardening");

        PqcAssetAssessment shaAssessment = report.assessments().stream()
                .filter(a -> a.asset().name().equals("SHA-256"))
                .findFirst().orElseThrow();
        assertThat(shaAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.QUANTUM_IMPACTED);
        assertThat(shaAssessment.purpose()).isEqualTo(CryptoPurpose.HASH_FUNCTION);
        assertThat(shaAssessment.priority()).isEqualTo(MigrationPriority.REVIEW_REQUIRED);
    }

    @Test
    @DisplayName("Preserves UNKNOWN classification for unresolved dynamic expressions without guessing")
    void testUnknownDynamicExpressionHandling() {
        Path file = Path.of("src/DynamicService.java");
        ExpressionResolution unk = ExpressionResolution.unknown("props.get()", "Dynamic property");
        CryptoApiUsage usage = new CryptoApiUsage(
                CryptoApiOwner.CIPHER,
                "getInstance",
                file,
                new SourceRange(10, 5, 10, 30),
                "props.get()",
                unk,
                Optional.empty(),
                AnalysisConfidence.HIGH,
                Map.of("enclosingClass", "com.example.DynamicService", "enclosingMethod", "init")
        );

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(usage), Path.of("."));
        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, null, null, 10);

        assertThat(report.unknownExposureCount()).isEqualTo(1);
        PqcAssetAssessment unkAssessment = report.assessments().get(0);
        assertThat(unkAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.UNKNOWN);
        assertThat(unkAssessment.priority()).isEqualTo(MigrationPriority.PRIORITY_UNDETERMINED);
        assertThat(unkAssessment.recommendation().standardFamily()).isEqualTo("None Required");
    }

    @Test
    @DisplayName("Traverses JGraphT dependency graph to accurately compute direct and transitive upstream blast radius")
    void testGraphIntegrationAndBlastRadiusTraversal() {
        // Setup Call Graph:
        // ClientController -> OrderService -> PaymentGateway -> CryptoService (which uses RSA)
        // AdminController  -> OrderService
        String clientClass = "com.app.ClientController";
        String adminClass = "com.app.AdminController";
        String serviceClass = "com.app.OrderService";
        String gatewayClass = "com.app.PaymentGateway";
        String cryptoClass = "com.app.CryptoService";

        InvocationReference c1 = new InvocationReference(clientClass, "run", serviceClass, "process", "process()", InvocationKind.METHOD_CALL, new SourceRange(10, 1, 10, 20), true);
        InvocationReference c2 = new InvocationReference(adminClass, "manage", serviceClass, "process", "process()", InvocationKind.METHOD_CALL, new SourceRange(20, 1, 20, 20), true);
        InvocationReference c3 = new InvocationReference(serviceClass, "process", gatewayClass, "pay", "pay()", InvocationKind.METHOD_CALL, new SourceRange(30, 1, 30, 20), true);
        InvocationReference c4 = new InvocationReference(gatewayClass, "pay", cryptoClass, "sign", "sign()", InvocationKind.METHOD_CALL, new SourceRange(40, 1, 40, 20), true);

        ParsedCompilationUnit uClient = createUnit(clientClass, "ClientController", List.of(c1));
        ParsedCompilationUnit uAdmin = createUnit(adminClass, "AdminController", List.of(c2));
        ParsedCompilationUnit uService = createUnit(serviceClass, "OrderService", List.of(c3));
        ParsedCompilationUnit uGateway = createUnit(gatewayClass, "PaymentGateway", List.of(c4));
        ParsedCompilationUnit uCrypto = createUnit(cryptoClass, "CryptoService", List.of());

        DependencyGraphBuilder builder = new DependencyGraphBuilder();
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(List.of(uClient, uAdmin, uService, uGateway, uCrypto), GraphBuildOptions.defaults());

        PageRankEngine prEngine = new PageRankEngine();
        List<HotspotMetric> hotspots = prEngine.compute(graph, PageRankOptions.defaults());
        Map<String, HotspotMetric> hotspotMap = new java.util.HashMap<>();
        hotspots.forEach(h -> hotspotMap.put(h.fqcn(), h));

        // Create RSA usage located in CryptoService
        Path cryptoPath = Path.of("src/CryptoService.java");
        CryptoApiUsage rsaUsage = createUsage(cryptoPath, 50, 8, CryptoApiOwner.SIGNATURE, "SHA256withRSA", cryptoClass, "sign");
        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(rsaUsage), Path.of("."));

        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, graph, hotspotMap, 10);
        assertThat(report.assessments()).hasSize(1);

        PqcAssetAssessment assessment = report.assessments().get(0);
        CryptoAssetBlastRadius blastRadius = assessment.blastRadius();

        // Direct caller of CryptoService is PaymentGateway
        assertThat(blastRadius.directDependentClasses()).containsExactly(gatewayClass);

        // Transitive-only callers (depth >= 2): OrderService (2), ClientController (3), AdminController (3)
        assertThat(blastRadius.transitiveOnlyDependentClasses()).containsExactlyInAnyOrder(
                serviceClass,
                clientClass,
                adminClass
        );
        assertThat(blastRadius.totalDirectDependents()).isEqualTo(1);
        assertThat(blastRadius.totalTransitiveOnlyDependents()).isEqualTo(3);
        assertThat(blastRadius.totalReachableClasses()).isEqualTo(4);
        assertThat(blastRadius.maxCallDepth()).isEqualTo(3);
        assertThat(blastRadius.cumulativeRiskScore()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Classifies classically broken algorithms as CLASSICALLY_WEAK while preserving provider uncertainty for SHA1PRNG")
    void testClassicallyWeakAlgorithms() {
        Path file = Path.of("src/LegacyCrypto.java");
        CryptoApiUsage des = createUsage(file, 10, 5, CryptoApiOwner.CIPHER, "DES/ECB/PKCS5Padding", "com.example.LegacyCrypto", "desEncrypt");
        CryptoApiUsage md5 = createUsage(file, 20, 5, CryptoApiOwner.MESSAGE_DIGEST, "MD5", "com.example.LegacyCrypto", "md5Hash");
        CryptoApiUsage sha1 = createUsage(file, 30, 5, CryptoApiOwner.MESSAGE_DIGEST, "SHA-1", "com.example.LegacyCrypto", "sha1Hash");
        CryptoApiUsage sha1prng = createUsage(file, 40, 5, CryptoApiOwner.SECURE_RANDOM, "SHA1PRNG", "com.example.LegacyCrypto", "random");

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(des, md5, sha1, sha1prng), Path.of("."));
        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, null, null, 10);

        assertThat(report.classicallyWeakCount()).isEqualTo(3);
        assertThat(report.unknownExposureCount()).isEqualTo(1);
        assertThat(report.quantumVulnerableCount()).isEqualTo(0);

        PqcAssetAssessment desAssessment = report.assessments().stream()
                .filter(a -> a.asset().name().contains("DES"))
                .findFirst().orElseThrow();
        assertThat(desAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.CLASSICALLY_WEAK);
        assertThat(desAssessment.category()).isEqualTo(AssessmentCategory.CLASSICAL_REMEDIATION);
        assertThat(desAssessment.purpose()).isEqualTo(CryptoPurpose.SYMMETRIC_ENCRYPTION);
        assertThat(desAssessment.recommendation().targetAlgorithms()).contains("AES-256-GCM");

        PqcAssetAssessment prngAssessment = report.assessments().stream()
                .filter(a -> a.asset().name().equals("SHA1PRNG"))
                .findFirst().orElseThrow();
        // SHA1PRNG: Provider-dependent uncertainty; not automatically called classically broken
        assertThat(prngAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.UNKNOWN);
        assertThat(prngAssessment.category()).isEqualTo(AssessmentCategory.UNKNOWN_OR_UNRESOLVED);
        assertThat(prngAssessment.purpose()).isEqualTo(CryptoPurpose.SECURE_RANDOM);
        assertThat(prngAssessment.recommendation().rationale()).contains("provider");
    }

    @Test
    @DisplayName("Separates findings into distinct assessment categories: PQC Candidates, Classical Remediation, Quantum Considerations, Unknown")
    void testAssessmentCategorySeparation() {
        Path file = Path.of("src/MixedCrypto.java");
        CryptoApiUsage rsa = createUsage(file, 10, 5, CryptoApiOwner.CIPHER, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", "com.example.MixedCrypto", "enc");
        CryptoApiUsage des = createUsage(file, 20, 5, CryptoApiOwner.CIPHER, "DES", "com.example.MixedCrypto", "desEnc");
        CryptoApiUsage aes = createUsage(file, 30, 5, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding", "com.example.MixedCrypto", "aesEnc");

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(rsa, des, aes), Path.of("."));
        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, null, null, 10);

        assertThat(report.pqcMigrationCandidatesCount()).isEqualTo(1);
        assertThat(report.classicalRemediationCount()).isEqualTo(1);
        assertThat(report.quantumConsiderationsCount()).isEqualTo(1);
        assertThat(report.unknownOrUnresolvedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("KeyPairGenerator and KeyGenerator assign precise operational purposes without guessing downstream usage")
    void testKeyPairAndKeyGeneratorPurposes() {
        Path file = Path.of("src/KeyServices.java");
        CryptoApiUsage rsaKeyGen = createUsage(file, 10, 5, CryptoApiOwner.KEY_PAIR_GENERATOR, "RSA", "com.example.KeyServices", "genRsa");
        CryptoApiUsage aesKeyGen = createUsage(file, 20, 5, CryptoApiOwner.KEY_GENERATOR, "AES", "com.example.KeyServices", "genAes");

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(rsaKeyGen, aesKeyGen), Path.of("."));
        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, null, null, 10);

        PqcAssetAssessment rsaKeyAssessment = report.assessments().stream()
                .filter(a -> a.asset().name().equals("RSA"))
                .findFirst().orElseThrow();
        assertThat(rsaKeyAssessment.purpose()).isEqualTo(CryptoPurpose.KEY_PAIR_GENERATION);
        assertThat(rsaKeyAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.QUANTUM_VULNERABLE);
        assertThat(rsaKeyAssessment.recommendation().targetStandard()).contains("Usage Dependent");

        PqcAssetAssessment aesKeyAssessment = report.assessments().stream()
                .filter(a -> a.asset().name().equals("AES"))
                .findFirst().orElseThrow();
        assertThat(aesKeyAssessment.purpose()).isEqualTo(CryptoPurpose.SYMMETRIC_KEY_GENERATION);
        assertThat(aesKeyAssessment.exposureLevel()).isEqualTo(QuantumExposureLevel.QUANTUM_IMPACTED);
    }

    private CryptoApiUsage createUsage(Path file, int line, int col, CryptoApiOwner owner, String rawAlgo, String enclosingClass, String enclosingMethod) {
        CryptoAlgorithm algo = CryptoAlgorithm.parse(rawAlgo);
        ExpressionResolution res = ExpressionResolution.resolved(rawAlgo, "\"" + rawAlgo + "\"", "Direct literal");
        return new CryptoApiUsage(
                owner,
                "getInstance",
                file,
                new SourceRange(line, col, line, col + rawAlgo.length()),
                "\"" + rawAlgo + "\"",
                res,
                Optional.of(algo),
                AnalysisConfidence.HIGH,
                Map.of(
                        "enclosingClass", enclosingClass,
                        "enclosingMethod", enclosingMethod
                )
        );
    }

    private ParsedCompilationUnit createUnit(String fqcn, String simpleName, List<InvocationReference> calls) {
        String pkg = fqcn.contains(".") ? fqcn.substring(0, fqcn.lastIndexOf(".")) : "";
        TypeDefinition typeDef = new TypeDefinition(
                fqcn,
                simpleName,
                pkg,
                TypeKind.CLASS,
                Path.of("src/" + simpleName + ".java"),
                Optional.empty(),
                Set.of(),
                List.of(),
                List.of(),
                Set.of(),
                new SourceRange(1, 1, 100, 1)
        );

        return new ParsedCompilationUnit(
                Path.of("src/" + simpleName + ".java"),
                pkg,
                List.of(),
                List.of(typeDef),
                calls,
                new ResolutionMetrics(calls.size(), calls.size(), 0, 0),
                List.of()
        );
    }
}
