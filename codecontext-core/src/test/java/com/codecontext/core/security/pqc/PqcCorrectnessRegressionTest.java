package com.codecontext.core.security.pqc;

import com.codecontext.core.finding.AnalysisConfidence;
import com.codecontext.core.graph.model.*;
import com.codecontext.core.model.*;
import com.codecontext.core.security.crypto.*;
import com.codecontext.core.security.inventory.*;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PqcCorrectnessRegressionTest {
    @Test void nestedCryptoUsageMatchesBinaryGraphIdentity() {
        String source="package p; import javax.crypto.Cipher; class Outer { static class Inner { void encrypt(){ Cipher.getInstance(\"RSA\"); } } void call(Inner inner){inner.encrypt();} }";
        var symbols=new com.codecontext.core.index.ConcurrentSymbolTable();
        var file=Path.of("Nested.java");
        new com.codecontext.core.parser.JavaParserDeclarationVisitor().parseAndRegister(source,file,symbols);
        var hierarchy=new com.codecontext.core.hierarchy.TypeHierarchyIndex(symbols);
        symbols.allTypes().forEach(hierarchy::index);
        var raw=new com.codecontext.core.parser.Pass2InvocationResolver(symbols,hierarchy,com.codecontext.core.resolver.ResolutionChain.standardChain()).resolve(file,source);
        var unit=new ParsedCompilationUnit(file,raw.packageName(),raw.imports(),List.copyOf(symbols.allTypes()),raw.invocations(),raw.metrics(),raw.parseErrors());
        var graph=new com.codecontext.core.graph.DependencyGraphBuilder().build(List.of(unit),com.codecontext.core.graph.GraphBuildOptions.defaults());
        var context=new com.codecontext.core.security.rule.CompilationUnitContext(file,source,com.github.javaparser.StaticJavaParser.parse(source),Optional.of(symbols));
        var inventory=new CryptoInventoryEngine().buildInventory(CryptoApiDetector.detectAll(context),Path.of("."));
        var radius=new PqcMigrationEngine().assessInventory(inventory,graph,Map.of(),10).assessments().get(0).blastRadius();
        assertThat(radius.directDependentClasses()).containsExactly("p.Outer");
    }
    @Test void recommendationsDoNotSelectUnobservedParameterSets() {
        var engine = new PqcMigrationEngine();
        var inventory = new CryptoInventoryEngine().buildInventory(List.of(
                usage("A", CryptoApiOwner.CIPHER, "RSA"),
                usage("B", CryptoApiOwner.CIPHER, "AES/GCM/NoPadding")), Path.of("."));
        for (var assessment : engine.assessInventory(inventory, null, null, 10).assessments()) {
            assertThat(assessment.recommendation().recommendedParameterSet()).isEmpty();
            assertThat(assessment.recommendation().targetAlgorithms()).doesNotContain("ML-KEM-768 + AES-256-GCM");
        }
    }
    private CryptoApiUsage usage(String ownerClass, CryptoApiOwner owner, String algorithm) {
        return new CryptoApiUsage(owner,"getInstance",Path.of(ownerClass+".java"),new SourceRange(1,1,1,9),algorithm,
                ExpressionResolution.resolved(algorithm,algorithm,"literal"),Optional.of(CryptoAlgorithm.parse(algorithm)),
                AnalysisConfidence.HIGH,Map.of("enclosingClass",ownerClass));
    }
    @Test void tlsWithoutKeyExchangeEvidenceIsUnknown() {
        var inventory=new CryptoInventoryEngine().buildInventory(List.of(usage("A",CryptoApiOwner.SSL_CONTEXT,"TLSv1.3")),Path.of("."));
        var assessment=new PqcMigrationEngine().assessInventory(inventory,null,null,10).assessments().get(0);
        assertThat(assessment.exposureLevel()).isEqualTo(QuantumExposureLevel.UNKNOWN);
        assertThat(assessment.recommendation().recommendedParameterSet()).isEmpty();
        assertThat(assessment.recommendation().rationale()).containsIgnoringCase("key establishment");
    }
    @Test void overlappingCallersContributeRiskOnceAtShortestDepth() {
        var inventory=new CryptoInventoryEngine().buildInventory(List.of(usage("A",CryptoApiOwner.CIPHER,"RSA"),usage("B",CryptoApiOwner.CIPHER,"RSA")),Path.of("."));
        var g=new DefaultDirectedWeightedGraph<TypeVertex,DependencyEdge>(DependencyEdge.class);
        var a=TypeVertex.of("A","A","",TypeKind.CLASS,Path.of("A.java"));
        var b=TypeVertex.of("B","B","",TypeKind.CLASS,Path.of("B.java"));
        var c=TypeVertex.of("C","C","",TypeKind.CLASS,Path.of("C.java"));
        List.of(a,b,c).forEach(g::addVertex);
        for(var target:List.of(a,b)) g.addEdge(c,target,new DependencyEdge(new InvocationDetail("f","f","f()",new SourceRange(1,1,1,1))));
        g.addEdge(a,a,new DependencyEdge(new InvocationDetail("f","f","f()",new SourceRange(1,1,1,1))));
        var report=new PqcMigrationEngine().assessInventory(inventory,g,Map.of(),10);
        var radius=report.assessments().get(0).blastRadius();
        assertThat(radius.cumulativeRiskScore()).isCloseTo(0.01,within(1e-10));
        assertThat(radius.directDependentClasses()).containsExactly("C");
        assertThat(report.totalAffectedClassesInBlastRadius()).isEqualTo(1);
    }
}
