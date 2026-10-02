package com.codecontext.core.graph;

import com.codecontext.core.graph.model.DependencyEdge;
import com.codecontext.core.graph.model.TypeVertex;
import com.codecontext.core.model.*;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DependencyGraphBuilderTest {

    private final DependencyGraphBuilder builder = new DependencyGraphBuilder();

    private ParsedCompilationUnit createUnit(String fqcn, String simpleName, String pkg, List<InvocationReference> calls) {
        TypeDefinition typeDef = new TypeDefinition(
                fqcn,
                simpleName,
                pkg,
                TypeKind.CLASS,
                Path.of(simpleName + ".java"),
                Optional.empty(),
                Set.of(),
                List.of(),
                List.of(),
                Set.of(),
                new SourceRange(1, 1, 50, 1)
        );

        return new ParsedCompilationUnit(
                Path.of(simpleName + ".java"),
                pkg,
                List.of(),
                List.of(typeDef),
                calls,
                new ResolutionMetrics(calls.size(), calls.size(), 0, 0),
                List.of()
        );
    }

    @Test
    @DisplayName("FTC-S2.1-001: Build directed graph from multi-package compilation units")
    void should_build_directed_graph_from_compilation_units() {
        InvocationReference call1 = new InvocationReference(
                "com.example.OrderController",
                "handleOrder",
                "com.example.OrderService",
                "placeOrder",
                "placeOrder(Order)",
                InvocationKind.METHOD_CALL,
                new SourceRange(25, 5, 25, 30),
                true
        );

        InvocationReference call2 = new InvocationReference(
                "com.example.OrderService",
                "placeOrder",
                "com.example.OrderRepository",
                "save",
                "save(Order)",
                InvocationKind.METHOD_CALL,
                new SourceRange(42, 9, 42, 25),
                true
        );

        ParsedCompilationUnit controller = createUnit("com.example.OrderController", "OrderController", "com.example", List.of(call1));
        ParsedCompilationUnit service = createUnit("com.example.OrderService", "OrderService", "com.example", List.of(call2));
        ParsedCompilationUnit repo = createUnit("com.example.OrderRepository", "OrderRepository", "com.example", List.of());

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(List.of(controller, service, repo), GraphBuildOptions.defaults());

        assertThat(graph.vertexSet()).hasSize(3);
        assertThat(graph.edgeSet()).hasSize(2);

        TypeVertex vController = TypeVertex.of("com.example.OrderController", "OrderController", "com.example", TypeKind.CLASS, Path.of("OrderController.java"));
        TypeVertex vService = TypeVertex.of("com.example.OrderService", "OrderService", "com.example", TypeKind.CLASS, Path.of("OrderService.java"));
        TypeVertex vRepo = TypeVertex.of("com.example.OrderRepository", "OrderRepository", "com.example", TypeKind.CLASS, Path.of("OrderRepository.java"));

        assertThat(graph.containsEdge(vController, vService)).isTrue();
        assertThat(graph.containsEdge(vService, vRepo)).isTrue();

        DependencyEdge edge1 = graph.getEdge(vController, vService);
        assertThat(edge1.getCallCount()).isEqualTo(1);
        assertThat(edge1.getWeight()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    @DisplayName("FTC-S2.1-002: Coalesce multiple invocations with logarithmic damping")
    void should_coalesce_multiple_invocations_with_logarithmic_damping() {
        InvocationReference c1 = new InvocationReference("com.svc.PaymentService", "pay", "com.gate.Gateway", "charge", "charge()", InvocationKind.METHOD_CALL, new SourceRange(10, 1, 10, 20), true);
        InvocationReference c2 = new InvocationReference("com.svc.PaymentService", "pay", "com.gate.Gateway", "charge", "charge()", InvocationKind.METHOD_CALL, new SourceRange(11, 1, 11, 20), true);
        InvocationReference c3 = new InvocationReference("com.svc.PaymentService", "pay", "com.gate.Gateway", "verify", "verify()", InvocationKind.METHOD_CALL, new SourceRange(12, 1, 12, 20), true);

        ParsedCompilationUnit paymentSvc = createUnit("com.svc.PaymentService", "PaymentService", "com.svc", List.of(c1, c2, c3));
        ParsedCompilationUnit gateway = createUnit("com.gate.Gateway", "Gateway", "com.gate", List.of());

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(List.of(paymentSvc, gateway), GraphBuildOptions.defaults());

        TypeVertex vPayment = TypeVertex.of("com.svc.PaymentService", "PaymentService", "com.svc", TypeKind.CLASS, Path.of("PaymentService.java"));
        TypeVertex vGateway = TypeVertex.of("com.gate.Gateway", "Gateway", "com.gate", TypeKind.CLASS, Path.of("Gateway.java"));

        DependencyEdge edge = graph.getEdge(vPayment, vGateway);
        assertThat(edge).isNotNull();
        assertThat(edge.getCallCount()).isEqualTo(3);
        // weight = log2(1 + 3) = 2.0
        assertThat(edge.getWeight()).isCloseTo(2.0, within(1e-6));
        assertThat(edge.getCallDetails()).hasSize(3);
    }

    @Test
    @DisplayName("FTC-S2.1-003: Boundary node exclusion and tagging toggle")
    void should_handle_boundary_nodes_according_to_options() {
        InvocationReference boundaryCall = new InvocationReference(
                "com.example.OrderService",
                "listOrders",
                "java.util.List",
                "of",
                "of()",
                InvocationKind.STATIC_METHOD_CALL,
                new SourceRange(15, 5, 15, 20),
                false
        );

        ParsedCompilationUnit service = createUnit("com.example.OrderService", "OrderService", "com.example", List.of(boundaryCall));

        // Mode 1: filterBoundaryNodes = true
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> filteredGraph =
                builder.build(List.of(service), GraphBuildOptions.excludeBoundaryNodes());

        assertThat(filteredGraph.vertexSet()).hasSize(1);
        assertThat(filteredGraph.edgeSet()).isEmpty();

        // Mode 2: filterBoundaryNodes = false
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> includedGraph =
                builder.build(List.of(service), GraphBuildOptions.defaults());

        assertThat(includedGraph.vertexSet()).hasSize(2);
        assertThat(includedGraph.edgeSet()).hasSize(1);

        TypeVertex boundaryVertex = includedGraph.vertexSet().stream()
                .filter(TypeVertex::isBoundaryNode)
                .findFirst()
                .orElseThrow();

        assertThat(boundaryVertex.fqcn()).isEqualTo("java.util.List");
    }

    @Test
    @DisplayName("FTC-S2.1-006: Self-invoking recursive method call creates self-loop without errors")
    void should_handle_self_invocations() {
        InvocationReference recursiveCall = new InvocationReference(
                "com.example.TreeWalker",
                "traverse",
                "com.example.TreeWalker",
                "traverse",
                "traverse(Node)",
                InvocationKind.METHOD_CALL,
                new SourceRange(30, 5, 30, 20),
                true
        );

        ParsedCompilationUnit walker = createUnit("com.example.TreeWalker", "TreeWalker", "com.example", List.of(recursiveCall));
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(List.of(walker), GraphBuildOptions.defaults());

        TypeVertex v = TypeVertex.of("com.example.TreeWalker", "TreeWalker", "com.example", TypeKind.CLASS, Path.of("TreeWalker.java"));
        assertThat(graph.containsEdge(v, v)).isTrue();
    }

    @Test
    @DisplayName("UTC-S2.1-US01-009: Null compilation units list throws NullPointerException")
    void should_reject_null_units() {
        assertThatThrownBy(() -> builder.build(null, GraphBuildOptions.defaults()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("UTC-S2.1-US01-010: Empty compilation units list returns empty graph")
    void should_return_empty_graph_for_empty_list() {
        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(List.of(), GraphBuildOptions.defaults());

        assertThat(graph.vertexSet()).isEmpty();
        assertThat(graph.edgeSet()).isEmpty();
    }

    @Test
    @DisplayName("UTC-S2.1-US01-013: Disconnected single classes have vertices but zero edges")
    void should_build_disconnected_classes_with_zero_edges() {
        ParsedCompilationUnit u1 = createUnit("com.example.A", "A", "com.example", List.of());
        ParsedCompilationUnit u2 = createUnit("com.example.B", "B", "com.example", List.of());

        DefaultDirectedWeightedGraph<TypeVertex, DependencyEdge> graph =
                builder.build(List.of(u1, u2), GraphBuildOptions.defaults());

        assertThat(graph.vertexSet()).hasSize(2);
        assertThat(graph.edgeSet()).isEmpty();
    }
}
