package com.codecontext.core.graph.analytics;

import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.HotspotMetric;
import com.codecontext.core.graph.model.InvocationDetail;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.model.SourceRange;
import com.codecontext.core.model.TypeKind;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class BlastRadiusEngineTest {

    private final BlastRadiusEngine engine = new BlastRadiusEngine();

    private TypeVertex vertex(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        String simple = lastDot > 0 ? fqcn.substring(lastDot + 1) : fqcn;
        String pkg = lastDot > 0 ? fqcn.substring(0, lastDot) : "";
        return TypeVertex.of(fqcn, simple, pkg, TypeKind.CLASS, Path.of(simple + ".java"));
    }

    private void addEdge(DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph, TypeVertex from, TypeVertex to) {
        InvocationDetail detail = new InvocationDetail("callerM", "targetM", "targetM()", new SourceRange(1, 1, 1, 10));
        DependencyEdge edge = new DependencyEdge(detail);
        graph.addEdge(from, to, edge);
        graph.setEdgeWeight(edge, 1.0);
    }

    @Test
    @DisplayName("FTC-S2.2-005: Direct upstream caller impact analysis (O(1) lookup)")
    void should_find_direct_upstream_callers() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex target = vertex("com.example.PaymentGateway");
        TypeVertex checkout = vertex("com.example.CheckoutService");
        TypeVertex subscription = vertex("com.example.SubscriptionService");
        TypeVertex refund = vertex("com.example.RefundService");

        graph.addVertex(target);
        graph.addVertex(checkout);
        graph.addVertex(subscription);
        graph.addVertex(refund);

        addEdge(graph, checkout, target);
        addEdge(graph, subscription, target);
        addEdge(graph, refund, target);

        Map<String, HotspotMetric> metrics = Map.of(
                "com.example.CheckoutService", new HotspotMetric("com.example.CheckoutService", (Path) null, 0.1, 50.0, 1, 1, 1),
                "com.example.SubscriptionService", new HotspotMetric("com.example.SubscriptionService", (Path) null, 0.2, 60.0, 1, 1, 2),
                "com.example.RefundService", new HotspotMetric("com.example.RefundService", (Path) null, 0.3, 70.0, 1, 1, 3)
        );

        ImpactAnalysisResult result = engine.analyzeImpact("com.example.PaymentGateway", graph, metrics, 10);

        assertThat(result.targetFqcn()).isEqualTo("com.example.PaymentGateway");
        assertThat(result.directCallers()).containsExactlyInAnyOrder(
                "com.example.CheckoutService",
                "com.example.SubscriptionService",
                "com.example.RefundService"
        );
        assertThat(result.transitiveCallerDepths()).containsEntry("com.example.CheckoutService", 1);
        assertThat(result.transitiveCallerDepths()).containsEntry("com.example.SubscriptionService", 1);
        assertThat(result.transitiveCallerDepths()).containsEntry("com.example.RefundService", 1);
    }

    @Test
    @DisplayName("FTC-S2.2-006: Transitive multi-hop blast radius with depth tracking (A -> B -> C -> Target)")
    void should_traverse_transitive_callers_with_depths() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex target = vertex("com.example.OrderRepository");
        TypeVertex service = vertex("com.example.OrderService");
        TypeVertex controller = vertex("com.example.OrderController");
        TypeVertex client = vertex("com.example.WebClient");

        graph.addVertex(target);
        graph.addVertex(service);
        graph.addVertex(controller);
        graph.addVertex(client);

        addEdge(graph, client, controller);
        addEdge(graph, controller, service);
        addEdge(graph, service, target);

        ImpactAnalysisResult result = engine.analyzeImpact("com.example.OrderRepository", graph, Map.of(), 10);

        assertThat(result.directCallers()).containsExactly("com.example.OrderService");
        assertThat(result.transitiveCallerDepths()).containsEntry("com.example.OrderService", 1);
        assertThat(result.transitiveCallerDepths()).containsEntry("com.example.OrderController", 2);
        assertThat(result.transitiveCallerDepths()).containsEntry("com.example.WebClient", 3);

        // When maxDepth is 1, only depth 1 callers should be returned
        ImpactAnalysisResult depth1Result = engine.analyzeImpact("com.example.OrderRepository", graph, Map.of(), 1);
        assertThat(depth1Result.transitiveCallerDepths()).containsOnlyKeys("com.example.OrderService");
    }

    @Test
    @DisplayName("FTC-S2.2-007: Distance-decayed cumulative risk calculation")
    void should_calculate_distance_decayed_cumulative_risk() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex target = vertex("com.example.BaseRepo");
        TypeVertex d1 = vertex("com.example.ServiceA");
        TypeVertex d2 = vertex("com.example.ControllerB");
        TypeVertex d3 = vertex("com.example.ClientC");

        graph.addVertex(target);
        graph.addVertex(d1);
        graph.addVertex(d2);
        graph.addVertex(d3);

        addEdge(graph, d3, d2);
        addEdge(graph, d2, d1);
        addEdge(graph, d1, target);

        Map<String, HotspotMetric> metrics = new HashMap<>();
        metrics.put("com.example.ServiceA", new HotspotMetric("com.example.ServiceA", (Path) null, 0.40, 80.0, 1, 1, 1));
        metrics.put("com.example.ControllerB", new HotspotMetric("com.example.ControllerB", (Path) null, 0.20, 50.0, 1, 1, 2));
        metrics.put("com.example.ClientC", new HotspotMetric("com.example.ClientC", (Path) null, 0.16, 40.0, 1, 1, 3));

        ImpactAnalysisResult result = engine.analyzeImpact("com.example.BaseRepo", graph, metrics, 10);

        // Expected: (0.40 / 1) + (0.20 / 2) + (0.16 / 4) = 0.40 + 0.10 + 0.04 = 0.54
        assertThat(result.cumulativeRisk()).isCloseTo(0.54, within(1e-5));
    }

    @Test
    @DisplayName("FTC-S2.2-008: Blast radius traversal through circular callers terminates without loop")
    void should_safely_terminate_on_circular_callers() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex target = vertex("com.example.Utility");
        TypeVertex a = vertex("com.example.ServiceA");
        TypeVertex b = vertex("com.example.ServiceB");

        graph.addVertex(target);
        graph.addVertex(a);
        graph.addVertex(b);

        addEdge(graph, a, target);
        addEdge(graph, b, target);
        addEdge(graph, a, b);
        addEdge(graph, b, a); // circular loop between callers

        ImpactAnalysisResult result = engine.analyzeImpact("com.example.Utility", graph, Map.of(), 10);

        assertThat(result.directCallers()).containsExactlyInAnyOrder("com.example.ServiceA", "com.example.ServiceB");
        assertThat(result.transitiveCallerDepths()).hasSize(2);
    }

    @Test
    @DisplayName("UTC-S2.2-US02-004: Non-existent target returns empty result")
    void should_return_empty_for_missing_target() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        ImpactAnalysisResult result = engine.analyzeImpact("com.example.Unknown", graph, Map.of(), 10);

        assertThat(result.targetFqcn()).isEqualTo("com.example.Unknown");
        assertThat(result.directCallers()).isEmpty();
        assertThat(result.transitiveCallerDepths()).isEmpty();
        assertThat(result.cumulativeRisk()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("UTC-S2.2-US02-008: Recommended test scope detects test classes")
    void should_identify_test_classes_in_call_chain() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                new DefaultDirectedWeightedGraph<>(DependencyEdge.class);

        TypeVertex service = vertex("com.example.OrderService");
        TypeVertex testClass = vertex("com.example.OrderServiceTest");

        graph.addVertex(service);
        graph.addVertex(testClass);

        addEdge(graph, testClass, service);

        ImpactAnalysisResult result = engine.analyzeImpact("com.example.OrderService", graph, Map.of(), 10);

        assertThat(result.recommendedTestScope()).containsExactly("com.example.OrderServiceTest");
    }
}
