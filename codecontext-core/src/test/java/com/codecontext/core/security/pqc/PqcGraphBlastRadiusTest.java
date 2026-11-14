package com.codecontext.core.security.pqc;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.graph.DependencyGraphBuilder;
import com.codecontext.core.graph.GraphBuildOptions;
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
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class PqcGraphBlastRadiusTest {

    private PqcMigrationEngine pqcEngine;
    private CryptoInventoryEngine inventoryEngine;
    private DependencyGraphBuilder graphBuilder;
    private PageRankEngine pageRankEngine;

    @BeforeEach
    void setUp() {
        pqcEngine = new PqcMigrationEngine();
        inventoryEngine = new CryptoInventoryEngine();
        graphBuilder = new DependencyGraphBuilder();
        pageRankEngine = new PageRankEngine();
    }

    @Test
    @DisplayName("Fixture 1: Linear chain A -> B -> C -> CryptoUsage verifies direct (B) vs transitive-only (A) at max depth 2")
    void testLinearChainBlastRadius() {
        String classA = "com.app.ServiceA";
        String classB = "com.app.ServiceB";
        String classC = "com.app.CryptoC";

        InvocationReference aToB = createCall(classA, "doA", classB, "doB", 10);
        InvocationReference bToC = createCall(classB, "doB", classC, "encrypt", 20);

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph = graphBuilder.build(
                List.of(
                        createUnit(classA, "ServiceA", List.of(aToB)),
                        createUnit(classB, "ServiceB", List.of(bToC)),
                        createUnit(classC, "CryptoC", List.of())
                ),
                GraphBuildOptions.defaults()
        );

        CryptoApiUsage usage = createUsage("src/CryptoC.java", 30, CryptoApiOwner.CIPHER, "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", classC, "encrypt");
        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(usage), Path.of("."));

        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, graph, computeHotspots(graph), 10);
        assertThat(report.assessments()).hasSize(1);

        CryptoAssetBlastRadius br = report.assessments().get(0).blastRadius();
        assertThat(br.directDependentClasses()).containsExactly(classB);
        assertThat(br.transitiveOnlyDependentClasses()).containsExactly(classA);
        assertThat(br.totalDirectDependents()).isEqualTo(1);
        assertThat(br.totalTransitiveOnlyDependents()).isEqualTo(1);
        assertThat(br.totalReachableClasses()).isEqualTo(2);
        assertThat(br.maxCallDepth()).isEqualTo(2);
    }

    @Test
    @DisplayName("Fixture 2: Two unrelated chains using same algorithm aggregate independent callers without crosstalk")
    void testTwoUnrelatedChainsSameAlgorithm() {
        // Chain 1: App1 -> Service1 -> Crypto1
        // Chain 2: App2 -> Service2 -> Crypto2
        String app1 = "com.org1.App1";
        String svc1 = "com.org1.Service1";
        String cry1 = "com.org1.Crypto1";

        String app2 = "com.org2.App2";
        String svc2 = "com.org2.Service2";
        String cry2 = "com.org2.Crypto2";

        InvocationReference c1 = createCall(app1, "run", svc1, "exec", 10);
        InvocationReference c2 = createCall(svc1, "exec", cry1, "sign", 20);

        InvocationReference c3 = createCall(app2, "run", svc2, "exec", 10);
        InvocationReference c4 = createCall(svc2, "exec", cry2, "sign", 20);

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph = graphBuilder.build(
                List.of(
                        createUnit(app1, "App1", List.of(c1)),
                        createUnit(svc1, "Service1", List.of(c2)),
                        createUnit(cry1, "Crypto1", List.of()),
                        createUnit(app2, "App2", List.of(c3)),
                        createUnit(svc2, "Service2", List.of(c4)),
                        createUnit(cry2, "Crypto2", List.of())
                ),
                GraphBuildOptions.defaults()
        );

        CryptoApiUsage u1 = createUsage("src/Crypto1.java", 15, CryptoApiOwner.SIGNATURE, "SHA256withRSA", cry1, "sign");
        CryptoApiUsage u2 = createUsage("src/Crypto2.java", 15, CryptoApiOwner.SIGNATURE, "SHA256withRSA", cry2, "sign");

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(u1, u2), Path.of("."));
        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, graph, computeHotspots(graph), 10);

        // Single consolidated asset SHA256withRSA
        assertThat(report.assessments()).hasSize(1);
        CryptoAssetBlastRadius br = report.assessments().get(0).blastRadius();

        // Direct callers to either Crypto1 or Crypto2
        assertThat(br.directDependentClasses()).containsExactlyInAnyOrder(svc1, svc2);
        // Transitive only
        assertThat(br.transitiveOnlyDependentClasses()).containsExactlyInAnyOrder(app1, app2);
        assertThat(br.totalDirectDependents()).isEqualTo(2);
        assertThat(br.totalTransitiveOnlyDependents()).isEqualTo(2);
        assertThat(br.totalReachableClasses()).isEqualTo(4);
    }

    @Test
    @DisplayName("Fixture 3: Disconnected classes are properly excluded from blast radius")
    void testDisconnectedClassesExcluded() {
        String activeCaller = "com.app.ActiveCaller";
        String cryptoClass = "com.app.CryptoService";
        String isolatedClass = "com.app.IsolatedWorker";

        InvocationReference call = createCall(activeCaller, "run", cryptoClass, "encrypt", 10);

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph = graphBuilder.build(
                List.of(
                        createUnit(activeCaller, "ActiveCaller", List.of(call)),
                        createUnit(cryptoClass, "CryptoService", List.of()),
                        createUnit(isolatedClass, "IsolatedWorker", List.of())
                ),
                GraphBuildOptions.defaults()
        );

        CryptoApiUsage usage = createUsage("src/CryptoService.java", 20, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding", cryptoClass, "encrypt");
        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(usage), Path.of("."));

        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, graph, computeHotspots(graph), 10);
        CryptoAssetBlastRadius br = report.assessments().get(0).blastRadius();

        assertThat(br.directDependentClasses()).containsExactly(activeCaller);
        assertThat(br.transitiveOnlyDependentClasses()).isEmpty();
        assertThat(br.directDependentClasses()).doesNotContain(isolatedClass);
        assertThat(br.totalReachableClasses()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fixture 4: Circular dependencies (A -> B -> C -> A) terminate safely without infinite loops")
    void testCircularDependencyTermination() {
        String classA = "com.circ.ClassA";
        String classB = "com.circ.ClassB";
        String classC = "com.circ.ClassC";

        // Cycle: A -> B -> C -> A
        InvocationReference aToB = createCall(classA, "callB", classB, "callC", 10);
        InvocationReference bToC = createCall(classB, "callC", classC, "callA", 20);
        InvocationReference cToA = createCall(classC, "callA", classA, "callB", 30);

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph = graphBuilder.build(
                List.of(
                        createUnit(classA, "ClassA", List.of(aToB)),
                        createUnit(classB, "ClassB", List.of(bToC)),
                        createUnit(classC, "ClassC", List.of(cToA))
                ),
                GraphBuildOptions.defaults()
        );

        // Crypto call is in ClassC
        CryptoApiUsage usage = createUsage("src/ClassC.java", 50, CryptoApiOwner.KEY_AGREEMENT, "ECDH", classC, "agree");
        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(usage), Path.of("."));

        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, graph, computeHotspots(graph), 5);
        CryptoAssetBlastRadius br = report.assessments().get(0).blastRadius();

        // Direct caller to C is B
        assertThat(br.directDependentClasses()).containsExactly(classB);
        // Transitive caller is A
        assertThat(br.transitiveOnlyDependentClasses()).containsExactly(classA);
        assertThat(br.totalReachableClasses()).isEqualTo(2);
        assertThat(br.maxCallDepth()).isLessThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Fixture 5: Fully qualified class name collisions (com.pkg1.Service vs com.pkg2.Service) are isolated")
    void testFqcnCollisionIsolation() {
        String pkg1Service = "com.pkg1.Service";
        String pkg2Service = "com.pkg2.Service";
        String callerPkg1 = "com.pkg1.Caller";

        InvocationReference call1 = createCall(callerPkg1, "run", pkg1Service, "hash", 10);

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph = graphBuilder.build(
                List.of(
                        createUnit(callerPkg1, "Caller", List.of(call1)),
                        createUnit(pkg1Service, "Service", List.of()),
                        createUnit(pkg2Service, "Service", List.of())
                ),
                GraphBuildOptions.defaults()
        );

        // Crypto in com.pkg1.Service
        CryptoApiUsage u1 = createUsage("src/com/pkg1/Service.java", 20, CryptoApiOwner.MESSAGE_DIGEST, "SHA-256", pkg1Service, "hash");
        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(u1), Path.of("."));

        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, graph, computeHotspots(graph), 10);
        CryptoAssetBlastRadius br = report.assessments().get(0).blastRadius();

        assertThat(br.directDependentClasses()).containsExactly(callerPkg1);
        assertThat(br.directDependentClasses()).doesNotContain(pkg2Service);
        assertThat(br.totalReachableClasses()).isEqualTo(1);
    }

    @Test
    @DisplayName("Fixture 6: Structural graph fan-in threshold detects high fan-in components and always discloses reachability limits")
    void testSharedUtilityClassHeuristicsAndDisclaimer() {
        String sharedClass = "com.common.SecurityProcessor"; // Non-'Utils' name to ensure purely graph-based detection
        String caller1 = "com.app.Controller1";
        String caller2 = "com.app.Controller2";
        String caller3 = "com.app.Controller3";
        String caller4 = "com.app.Controller4";
        String caller5 = "com.app.Controller5";

        InvocationReference c1 = createCall(caller1, "exec", sharedClass, "encrypt", 10);
        InvocationReference c2 = createCall(caller2, "exec", sharedClass, "encrypt", 10);
        InvocationReference c3 = createCall(caller3, "exec", sharedClass, "encrypt", 10);
        InvocationReference c4 = createCall(caller4, "exec", sharedClass, "encrypt", 10);
        InvocationReference c5 = createCall(caller5, "exec", sharedClass, "encrypt", 10);

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph = graphBuilder.build(
                List.of(
                        createUnit(caller1, "Controller1", List.of(c1)),
                        createUnit(caller2, "Controller2", List.of(c2)),
                        createUnit(caller3, "Controller3", List.of(c3)),
                        createUnit(caller4, "Controller4", List.of(c4)),
                        createUnit(caller5, "Controller5", List.of(c5)),
                        createUnit(sharedClass, "SecurityProcessor", List.of())
                ),
                GraphBuildOptions.defaults()
        );

        CryptoApiUsage usage = createUsage("src/SecurityProcessor.java", 15, CryptoApiOwner.CIPHER, "AES/GCM/NoPadding", sharedClass, "encrypt");
        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(usage), Path.of("."));

        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, graph, computeHotspots(graph), 10);
        CryptoAssetBlastRadius br = report.assessments().get(0).blastRadius();

        // 1. High fan-in (5 direct callers >= HIGH_FAN_IN_THRESHOLD) flagged structurally
        assertThat(br.isSharedUtilityUsage()).isTrue();
        assertThat(br.totalDirectDependents()).isEqualTo(5);
        assertThat(br.totalTransitiveOnlyDependents()).isEqualTo(0);

        // 2. Always discloses that class reachability does not prove method invocation
        assertThat(br.reachabilitySemanticsNote()).contains("Class-level architectural dependency approximation");
        assertThat(br.reachabilitySemanticsNote()).contains("does not prove runtime invocation");
        assertThat(br.reachabilitySemanticsNote()).contains("high structural graph fan-in (5 direct caller classes)");
    }

    @Test
    @DisplayName("Fixture 7: KeyPairGenerator RSA assigns KEY_PAIR_GENERATION without downstream usage assumption")
    void testKeyPairGeneratorRsaBlastRadiusAndPurpose() {
        String keyGenClass = "com.app.KeyManagementService";
        String caller = "com.app.BootstrapConfig";

        InvocationReference call = createCall(caller, "init", keyGenClass, "createKeys", 10);

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph = graphBuilder.build(
                List.of(
                        createUnit(caller, "BootstrapConfig", List.of(call)),
                        createUnit(keyGenClass, "KeyManagementService", List.of())
                ),
                GraphBuildOptions.defaults()
        );

        CryptoApiUsage usage = createUsage("src/KeyManagementService.java", 35, CryptoApiOwner.KEY_PAIR_GENERATOR, "RSA", keyGenClass, "createKeys");
        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(usage), Path.of("."));

        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, graph, computeHotspots(graph), 10);
        PqcAssetAssessment assessment = report.assessments().get(0);

        assertThat(assessment.purpose()).isEqualTo(CryptoPurpose.KEY_PAIR_GENERATION);
        assertThat(assessment.exposureLevel()).isEqualTo(QuantumExposureLevel.QUANTUM_VULNERABLE);
        assertThat(assessment.recommendation().targetStandard()).contains("Usage Dependent");
        assertThat(assessment.blastRadius().directDependentClasses()).containsExactly(caller);
    }

    @Test
    @DisplayName("Fixture 8: Unknown algorithm expression assigns UNKNOWN exposure and PRIORITY_UNDETERMINED")
    void testUnknownAlgorithmExpression() {
        Path file = Path.of("src/DynCrypto.java");
        ExpressionResolution unk = ExpressionResolution.unknown("System.getProperty(\"cipher\")", "Property lookup");
        CryptoApiUsage usage = new CryptoApiUsage(
                CryptoApiOwner.CIPHER,
                "getInstance",
                file,
                new SourceRange(10, 5, 10, 35),
                "System.getProperty(\"cipher\")",
                unk,
                Optional.empty(),
                AnalysisConfidence.HIGH,
                Map.of("enclosingClass", "com.app.DynCrypto", "enclosingMethod", "getCipher")
        );

        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(usage), Path.of("."));
        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, null, null, 10);

        assertThat(report.assessments()).hasSize(1);
        PqcAssetAssessment assessment = report.assessments().get(0);
        assertThat(assessment.exposureLevel()).isEqualTo(QuantumExposureLevel.UNKNOWN);
        assertThat(assessment.purpose()).isEqualTo(CryptoPurpose.UNKNOWN);
        assertThat(assessment.priority()).isEqualTo(MigrationPriority.PRIORITY_UNDETERMINED);
        assertThat(assessment.blastRadius().totalReachableClasses()).isEqualTo(0);
    }

    @Test
    @DisplayName("Fixture 9: Valid crypto observation missing from graph handles null target gracefully")
    void testObservationMissingFromGraph() {
        // Enclosing class exists in usage properties but was never indexed into dependency graph
        String unindexedClass = "com.external.StandaloneHelper";

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> emptyGraph = graphBuilder.build(
                List.of(),
                GraphBuildOptions.defaults()
        );

        CryptoApiUsage usage = createUsage("src/StandaloneHelper.java", 12, CryptoApiOwner.MESSAGE_DIGEST, "SHA-512", unindexedClass, "digest");
        CryptoInventory inventory = inventoryEngine.buildInventory(List.of(usage), Path.of("."));

        PqcAssessmentReport report = pqcEngine.assessInventory(inventory, emptyGraph, Map.of(), 10);
        assertThat(report.assessments()).hasSize(1);

        CryptoAssetBlastRadius br = report.assessments().get(0).blastRadius();
        assertThat(br.directDependentClasses()).isEmpty();
        assertThat(br.transitiveOnlyDependentClasses()).isEmpty();
        assertThat(br.totalReachableClasses()).isEqualTo(0);
        assertThat(br.maxCallDepth()).isEqualTo(0);
    }

    private Map<String, HotspotMetric> computeHotspots(DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph) {
        List<HotspotMetric> list = pageRankEngine.compute(graph, PageRankOptions.defaults());
        Map<String, HotspotMetric> map = new HashMap<>();
        list.forEach(h -> map.put(h.fqcn(), h));
        return map;
    }

    private InvocationReference createCall(String fromFqcn, String fromMethod, String toFqcn, String toMethod, int line) {
        return new InvocationReference(
                fromFqcn,
                fromMethod,
                toFqcn,
                toMethod,
                toMethod + "()",
                InvocationKind.METHOD_CALL,
                new SourceRange(line, 1, line, 25),
                true
        );
    }

    private CryptoApiUsage createUsage(String filePath, int line, CryptoApiOwner owner, String rawAlgo, String enclosingClass, String enclosingMethod) {
        Path file = Path.of(filePath);
        CryptoAlgorithm algo = CryptoAlgorithm.parse(rawAlgo);
        ExpressionResolution res = ExpressionResolution.resolved(rawAlgo, "\"" + rawAlgo + "\"", "Direct literal");
        return new CryptoApiUsage(
                owner,
                "getInstance",
                file,
                new SourceRange(line, 5, line, 5 + rawAlgo.length()),
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
