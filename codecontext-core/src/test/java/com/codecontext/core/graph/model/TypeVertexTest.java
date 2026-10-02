package com.codecontext.core.graph.model;

import com.codecontext.core.model.TypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TypeVertexTest {

    @Test
    @DisplayName("UTC-S2.1-US01-001: should instantiate valid TypeVertex with correct fields")
    void should_instantiate_valid_vertex() {
        TypeVertex vertex = TypeVertex.of(
                "com.example.OrderService",
                "OrderService",
                "com.example",
                TypeKind.CLASS,
                Path.of("src/main/java/OrderService.java")
        );

        assertThat(vertex.fqcn()).isEqualTo("com.example.OrderService");
        assertThat(vertex.simpleName()).isEqualTo("OrderService");
        assertThat(vertex.packageName()).isEqualTo("com.example");
        assertThat(vertex.kind()).isEqualTo(TypeKind.CLASS);
        assertThat(vertex.filePath()).contains(Path.of("src/main/java/OrderService.java"));
        assertThat(vertex.isBoundaryNode()).isFalse();
    }

    @Test
    @DisplayName("UTC-S2.1-US01-002: should reject null FQCN")
    void should_reject_null_fqcn() {
        assertThatThrownBy(() -> TypeVertex.of(
                null,
                "OrderService",
                "com.example",
                TypeKind.CLASS,
                Path.of("src/OrderService.java")
        )).isInstanceOf(NullPointerException.class)
          .hasMessageContaining("fqcn");
    }

    @Test
    @DisplayName("UTC-S2.1-US01-003: should evaluate equality and hashCode based solely on FQCN")
    void should_evaluate_equality_based_on_fqcn() {
        TypeVertex v1 = TypeVertex.of("com.example.Foo", "Foo", "com.example", TypeKind.CLASS, null);
        TypeVertex v2 = new TypeVertex("com.example.Foo", "FooRenamed", "com.other", TypeKind.INTERFACE, null, true);
        TypeVertex v3 = TypeVertex.of("com.example.Bar", "Bar", "com.example", TypeKind.CLASS, null);

        assertThat(v1).isEqualTo(v2);
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
        assertThat(v1).isNotEqualTo(v3);
    }

    @Test
    @DisplayName("UTC-S2.1-US01-004: should support boundary node with empty filePath")
    void should_support_boundary_node() {
        TypeVertex boundary = TypeVertex.boundary("java.util.List", "List", "java.util");

        assertThat(boundary.isBoundaryNode()).isTrue();
        assertThat(boundary.filePath()).isEmpty();
        assertThat(boundary.fqcn()).isEqualTo("java.util.List");
    }
}
